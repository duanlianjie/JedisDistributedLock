package com.github.jedis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Created by 段连杰
 * 2021-04-26
 */
public class Test {
    public static void main(String[] args) {
//        JedisPoolConfig config = new JedisPoolConfig();
//        config.setMinIdle(10);
//        config.setMaxIdle(50);
//        config.setMaxTotal(100);
//        config.setMaxWaitMillis(1000);
//        JedisPool jedisPool = new JedisPool(config, "127.0.0.1", 7010, 10000, "123456");
//        Jedis jedis = jedisPool.getResource();
        Jedis jedis = new Jedis("127.0.0.1", 6379);
        jedis.auth("123456");
        jedis.set("key", "value");
        jedis.close();
    }
}
