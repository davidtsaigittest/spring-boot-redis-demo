package com.example.springbootredisdemo.controller;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 問題:
 * 1. 確保redis lock過期時間要大於業務執行時間的問題
 *    => redis分佈式鎖如何實現緩存續命?
 *
*  先介紹CAP
 *   C: Consistency 一致性 => 同一時刻同一請求的不同實力返回的結果相同，要求數據具有強的一致性
 *   A: Availability 可用性 => 所有讀寫請求在一定時間內得到正確響應
 *   P: Partition tolerance 分區容錯性 => 網路異常情況下，系統仍能正常運作
 *
 * 2. 生產中如何同時確保redis的可用性與一致性:
 *    => redis集群(CP) => 滿足一致性，犧牲可用性
 *    情境:
 *    1.master加鎖後會異步(不是馬上)將鎖同步給其他slave，
 *    2.若過程中鎖還未同步完成master就發生故障
 *    3.哨兵模式啟動，新的master產生後，身上並沒有舊master的鎖
 *    => redis異步複製鎖(或數據)可能造成鎖丟失
 *
 *    對比zookeeper(AP: 分區容錯，滿足可用性犧牲一致性)的複製是同步的(主從節點資料一致)
 *    主節點故障後，被從節點取代時能保持資料完整
 *    => 但zookeeper的併發性低
 *
 * 3. 為了處理在實際生產中redis集群緩存續命和鎖同步工作的重要問題
 *      => redis官方建議使用基於redlock算法的java實現庫redisson
 *
 */
@RestController
public class GoodController91 {

    public static final String REDIS_LOCK = "atguiguLock";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${server.port}")
    private String serverPort;

    @Autowired
    private Redisson redisson;

    @GetMapping("/buy_goods")
    public String buyGoods() throws Exception {

        // 各線程獨立的唯一值，做為value
        String value = UUID.randomUUID().toString() + Thread.currentThread().getName();

        RLock redissonLock = redisson.getLock(REDIS_LOCK);

        redissonLock.lock();
        try {
            String soldOutMessage = "商品已經售完, 服務端口: " + serverPort;
            // get key === 查看庫存夠不夠
            String result = stringRedisTemplate.opsForValue().get("goods:001");
            int goodNumber = result == null ? 0 : Integer.parseInt(result);

            if (goodNumber > 0) {
                int realNumber = goodNumber - 1;
                stringRedisTemplate.opsForValue().set("goods:001", String.valueOf(realNumber));
                String successMessage = "成功買到商品, 庫存還剩下: " + realNumber + " 件" + "\t 服務端口: " + serverPort;
                System.out.println(successMessage);
                return successMessage;
            }
            return soldOutMessage;
        } finally {
            redissonLock.unlock();
        }
    }
}
