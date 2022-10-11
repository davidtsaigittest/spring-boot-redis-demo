package com.example.springbootredisdemo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 單機鎖(Lock, Synchronize)無法解決超賣問題
 */
@RestController
public class GoodControllerSynchronize {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${server.port}")
    private String serverPort;

    @GetMapping("/buy_goods")
    public String buyGoods() {
        // 使用synchronized不得到鎖不會釋放，容易阻塞
        synchronized (this) {
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
        }

    }
}
