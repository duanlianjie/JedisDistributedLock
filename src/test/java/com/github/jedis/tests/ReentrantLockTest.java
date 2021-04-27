package com.github.jedis.tests;

import com.github.jedis.lock.JedisLock;
import com.github.jedis.lock.JedisLockManager;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.TimeUnit;

public class ReentrantLockTest {
    private volatile static JedisLock lock;

    @BeforeClass
    public static void init() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(10);
        config.setMaxIdle(50);
        config.setMaxTotal(100);
        config.setMaxWaitMillis(1000);
        lock = new JedisLockManager(new JedisCluster(
                new HostAndPort("127.0.0.1",6399), 10000, 10000, 100,"123456", config))
                .getLock("mylock");
    }

    @Test
    public void lock() {
        try {
            lock.lock();
            System.out.println("Get lock success...");
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void tryLock() {
        try {
            Assert.assertTrue(lock.tryLock());
            System.out.println("Get lock success...");
        } finally {
            lock.unlock();
        }
        try {
            Assert.assertTrue(lock.tryLock(1, TimeUnit.SECONDS));
            System.out.println("Get lock success...");
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void lockAsync() {
        try {
            // lock的异步方式
            lock.lockAsync().thenAccept(x -> System.out.println("Get lock success...")).get();
        } catch (Throwable e) {
            //...
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void tryLockAsync() {
        try {
            //tryLock的异步方式
            lock.tryLockAsync().thenAccept(x -> System.out.println("Get lock success...")).get();
        } catch (Throwable e) {
            //...
        } finally {
            lock.unlock();
        }
        try {
            lock.tryLockAsync(1, TimeUnit.SECONDS).thenAccept(x -> System.out.println("Get lock success...")).get();
        } catch (Throwable e) {
            //...
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void forceUnlock() {
        try {
            lock.tryLock();
            System.out.println("Get lock success...");
        } finally {
            lock.forceUnlock();
        }
    }
}
