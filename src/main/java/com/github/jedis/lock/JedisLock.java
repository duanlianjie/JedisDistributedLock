package com.github.jedis.lock;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Jedis 的分布式锁接口
 */
public interface JedisLock {
    /**
     * 同步获取重入锁，当前线程如果获取锁资源失败则一直阻塞直至成功
     */
    void lock();

    /**
     * 同步获取重入锁，当前线程尝试获取锁资源失败则快速失败
     */
    boolean tryLock();

    /**
     * 同步获取重入锁，当前线程尝试获取锁资源，如果在指定单位时间内都无法获取锁资源则失败
     */
    boolean tryLock(long waitTime, TimeUnit unit);

    /**
     * 释放锁资源
     */
    void unlock();

    /**
     * 暴力释放锁资源
     */
    void forceUnlock();

    /**
     * lock 的异步方式
     */
    CompletableFuture<Void> lockAsync();

    /**
     * tryLock() 的异步方式
     */
    CompletableFuture<Boolean> tryLockAsync();

    /**
     * tryLock(long time, TimeUnit unit) 的异步方式
     */
    CompletableFuture<Boolean> tryLockAsync(long time, TimeUnit unit);

    /**
     * unlock() 的异步方式
     */
    CompletableFuture<Void> unlockAsync();

    /**
     * forceUnlock的异步方式
     */
    CompletableFuture<Void> forceUnlockAsync();
}
