package com.github.jedis.tests;

import com.github.jedis.lock.JedisLock;
import com.github.jedis.lock.JedisLockManager;
import org.junit.BeforeClass;
import org.junit.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RedLockBenchmark {
    private static volatile JedisLock lock;

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
    public void lockTest() {
        // 10 个线程，每个线程 1000 次加锁解锁操作
        int threadSize = 10;
        int taskSize = 100000;
        CountDownLatch latch = new CountDownLatch(threadSize);
        long begin = System.nanoTime();
        for (int i = 0; i < threadSize; i++) {
            new Thread(() -> {
                for (int j = 0; j < taskSize / threadSize; j++) {
                    lock.lock();
                    lock.unlock();
                }
                latch.countDown();
            }).start();
        }
        try {
            latch.await();
            long end = System.nanoTime();
            long rtt = TimeUnit.NANOSECONDS.toSeconds(end - begin);
            DecimalFormat df = new DecimalFormat("0.00");
            df.setRoundingMode(RoundingMode.HALF_UP);
            String avg = df.format((double) rtt * 1000.0 / taskSize);
            String tps = df.format((double) taskSize / rtt);
            System.out.printf("[ThreadSize]:%s, [TaskSize]:%s, [RTT]:%ss, [AVG]:%sms, [TPS]:%s/s%n",
                    threadSize, taskSize, rtt, avg, tps);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void tryLockTest() {
        int threadSize = 20;
        int taskSize = 1000000;
        CountDownLatch latch = new CountDownLatch(threadSize);
        long begin = System.nanoTime();
        for (int i = 0; i < threadSize; i++) {
            new Thread(() -> {
                for (int j = 0; j < taskSize / threadSize; j++) {
                    if (lock.tryLock()) {
                        lock.unlock();
                    }
                }
                latch.countDown();
            }).start();
        }
        try {
            latch.await();
            long end = System.nanoTime();
            long rtt = TimeUnit.NANOSECONDS.toSeconds(end - begin);
            DecimalFormat df = new DecimalFormat("0.00");
            df.setRoundingMode(RoundingMode.HALF_UP);
            String avg = df.format((double) rtt * 1000.0 / taskSize);
            String tps = df.format((double) taskSize / rtt);
            System.out.printf("[ThreadSize]:%s, [TaskSize]:%s, [RTT]:%ss, [AVG]:%sms, [TPS]:%s/s%n",
                    threadSize, taskSize, rtt, avg, tps);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
