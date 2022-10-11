package com.example.springbootredisdemo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 問題:
 *
 */
@RestController
public class GoodController81 {

    public static final String REDIS_LOCK = "atguiguLock";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${server.port}")
    private String serverPort;

    @GetMapping("/buy_goods")
    public String buyGoods() {

        // 各線程獨立的唯一值，做為value
        String value = UUID.randomUUID().toString() + Thread.currentThread().getName();

        try {
            // 保證加鎖&過期時間兩操作的原子性
            Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(REDIS_LOCK, value, 10L, TimeUnit.SECONDS);//SETNX

            if (!flag) {
                return "搶鎖失敗，請重試";
            }

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
            // 買到商品要刪除鎖
            // 判斷是否是自己的鎖 => 問題: 判斷與刪除不是原子性
            /**
             * 解決:
             * 1. Lua腳本(工作上用)
             * 2. redis事務支持
             *  MULTI開啟一個事務
             *  SET k1 1
             *  SET k2 2
             *  EXEC
             *  MULTI + EXEC 批量處理命令 => 可以保證2個SET的原子性，不被打斷
             *
             *  set k1 1
             *  set k2 2
             *  WATCH k1 => 監控k1
             *  MULTI
             *  set k1 11
             *  set k2 22
             *  ---------------> 若在此時有其他線程進來更改k1 => EXEC會失敗，因為WATCH指令指向K1(類似樂觀鎖)
             *  EXEC
             *  UNWATCH
             *
             */
            while (true) {
                stringRedisTemplate.watch(REDIS_LOCK);
                if(stringRedisTemplate.opsForValue().get(REDIS_LOCK).equalsIgnoreCase(value)) {
                    stringRedisTemplate.setEnableTransactionSupport(true);
                    stringRedisTemplate.multi();
                    stringRedisTemplate.delete(REDIS_LOCK);
                    List<Object> list = stringRedisTemplate.exec();
                    // 判斷是否刪鎖成功，若list == null，表示樂觀鎖被人動過 => 重新執行一次watch
                    if (list == null) {
                        continue;
                    }
                    stringRedisTemplate.unwatch();
                    break;
                }
            }
        }

    }
}
