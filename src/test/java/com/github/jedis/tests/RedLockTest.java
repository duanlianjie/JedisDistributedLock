package com.github.jedis.tests;

import com.github.jedis.lock.JedisLock;
import com.github.jedis.lock.JedisLockManager;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class RedLockTest {
    private static JedisLock lock;

    @BeforeClass
    public static void init() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(10);
        config.setMaxIdle(50);
        config.setMaxTotal(100);
        config.setMaxWaitMillis(1000);
        JedisLockManager manager = new JedisLockManager(Arrays.asList(
                new JedisPool(config, "127.0.0.1", 6379, 10000, "123456"),
                new JedisPool(config, "127.0.0.1", 6479, 10000, "123456"),
                new JedisPool(config, "127.0.0.1", 6579, 10000, "123456")));
        lock = manager.getLock("mylock");
    }

    @Test
    public void lock() {
        try {
            lock.lock();
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void tryLock() {
        try {
            Assert.assertTrue(lock.tryLock());
        } finally {
            lock.unlock();
        }
        try {
            Assert.assertTrue(lock.tryLock(10, TimeUnit.SECONDS));
        } finally {
            lock.unlock();
        }
    }
}
