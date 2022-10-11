package com.example.springbootredisdemo.controller;

import com.example.springbootredisdemo.util.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 問題:
 */
@RestController
public class GoodController82 {

    public static final String REDIS_LOCK = "atguiguLock";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${server.port}")
    private String serverPort;

    @GetMapping("/buy_goods")
    public String buyGoods() throws Exception {

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
            // 解決: lua腳本寫法
            Jedis jedis = RedisUtils.getJedis();
            String luaScript = "if redis.call('get',KEYS[1]) == ARGV[1] " +
                    "then " +
                    "return redis.call('del',KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end";
            try {
                // KEYS: REDIS_LOCK, ARGS: uuid + thread name
                Object o = jedis.eval(luaScript, Collections.singletonList(REDIS_LOCK), Collections.singletonList(value));
                if ("1".equals((o.toString()))) {
                    System.out.println("-----del redis lock ok");
                } else {
                    System.out.println("-----del redis lock error");
                }
            } finally {
                if (null != jedis) {
                    jedis.close();
                }
            }

        }

    }
}
