package com.example.springbootredisdemo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 *  存在問題 :
 *  若執行到finally前，server當機或進程被人kill
 *  代碼沒有走到finally，鎖不會被釋放
 *  解決: Redis加鎖同時要給過期時間
 */
@RestController
public class GoodController4 {

    public static final String REDIS_LOCK = "atguiguLock";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${server.port}")
    private String serverPort;

    @GetMapping("/buy_goods")
    public String buyGoods() {

        String value = UUID.randomUUID().toString() + Thread.currentThread().getName();

        try {
            Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(REDIS_LOCK, value);//SETNX

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
            stringRedisTemplate.delete(REDIS_LOCK);
        }

    }
}
