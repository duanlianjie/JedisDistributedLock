package com.github.jedis.lock;

import com.github.jedis.exceptions.JedisLockException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 红锁
 */
public class JedisRedLock implements JedisLock {

    protected List<JedisLock> locks;

    protected JedisRedLock(List<JedisLock> locks) {
        this.locks = locks;
    }

    @Override
    public void lock() {
        Objects.requireNonNull(locks);
        long waitTime = locks.size() * 1500;    // 总最大等待时间
        while (true) {
            if (tryLock(waitTime, TimeUnit.MILLISECONDS)) {
                return;
            }
        }
    }

    @Override
    public boolean tryLock() {
        return tryLock(-1, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) {
        if (locks.size() < 3) {
            throw new JedisLockException("More than 3 redis nodes are required");
        }

        waitTime = unit.toMillis(waitTime);
        long beginTime = System.currentTimeMillis();                    // 记录开始时间
        long avgWaitTime = Math.max(waitTime / locks.size(), 1);           // 平均每个子锁的超时时间
        AtomicInteger acquiredLocks = new AtomicInteger();              // 记录成功获取子锁的次数

        long finalWaitTime = waitTime;
        locks.stream().filter(Objects::nonNull).forEach(lock -> {
            boolean result;
            try {
                result = finalWaitTime == -1L ? lock.tryLock() : lock.tryLock(avgWaitTime, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                result = false;
            }
            if (result) {
                acquiredLocks.incrementAndGet();
            }
        });

        // 当出现子锁取锁失败时，N / 2 + 1 个节点成功则代表取锁成功
        if (acquiredLocks.get() >= (locks.size() >> 1) + 1) {
            // 加锁使用的时间
            long endTime = System.currentTimeMillis() - beginTime;
            if (waitTime != -1L && waitTime <= endTime) {
                // 加锁使用的时间 >= 加锁超时时间，锁获取失败，取锁超时后释放所有锁
                unlockInner(locks);
                return false;
            }
            return true;
        } else {
            unlockInner(locks);                         // 当失败次数达到阈值时，释放所有子锁
            return false;
        }
    }

    @Override
    public void unlock() {
        locks.forEach(lock -> {
            try {
                lock.unlock();
            } catch (JedisLockException e) {
                //...
            }
        });
    }

    @Override
    public void forceUnlock() {
        locks.forEach(lock -> {
            try {
                lock.forceUnlock();
            } catch (JedisLockException e) {
                //...
            }
        });
    }

    @Override
    public CompletableFuture<Void> lockAsync() {
        return CompletableFuture.runAsync(this::lock);
    }

    @Override
    public CompletableFuture<Boolean> tryLockAsync() {
        return CompletableFuture.supplyAsync(this::tryLock);
    }

    @Override
    public CompletableFuture<Boolean> tryLockAsync(long time, TimeUnit unit) {
        return CompletableFuture.supplyAsync(() -> tryLock(time, unit));
    }

    @Override
    public CompletableFuture<Void> unlockAsync() {
        return CompletableFuture.runAsync(this::unlockAsync);
    }

    @Override
    public CompletableFuture<Void> forceUnlockAsync() {
        return CompletableFuture.runAsync(this::forceUnlock);
    }

    private void unlockInner(List<JedisLock> locks) {
        Objects.requireNonNull(locks);
        locks.forEach(lock -> {
            try {
                lock.unlock();
            } catch (JedisLockException e) {
                //...
            }
        });
    }
}
