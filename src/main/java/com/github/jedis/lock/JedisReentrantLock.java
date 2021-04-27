package com.github.jedis.lock;

import com.github.jedis.exceptions.JedisLockException;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/**
 * 单点可重入锁
 */
public class JedisReentrantLock implements JedisLock {

    private final String name;

    /**
     * 使用 ThreadLocal 保存每个线程的线程标识
     */
    private static final ThreadLocal<String> threadId = new ThreadLocal<>();

    private final LockCommand client;

    private volatile String lockScript, unLockScript, forceUnLockScript, updateTTLScript;

    /**
     * 工作线程组，可回收缓存线程池，空闲线程允许进行回收
     */
    private final Executor workerGroup = new ThreadPoolExecutor(10, 500, 2000, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(2000),
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * watchdog，定时更新当前锁的 TTL
     */
    private Future<?> future;

    /**
     * watchdog，监听线程组
     */
    private final ScheduledExecutorService watchGroup = Executors.newScheduledThreadPool(10);

    private boolean isListener;

    /**
     * 相关订阅者
     */
    private final Set<Thread> subscribers = Collections.synchronizedSet(new HashSet<>());

    protected JedisReentrantLock(String name, LockCommand client) {
        this.name = name;
        this.client = client;
    }

    @Override
    public void lock() {
        lock(acquireVisitorId());
    }

    @Override
    public boolean tryLock() {
        return tryLock(acquireVisitorId(), -1L, TimeUnit.SECONDS);
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) {
        return tryLock(acquireVisitorId(), waitTime, unit);
    }

    @Override
    public void unlock() {
        unlock(acquireVisitorId());
    }

    @Override
    public void forceUnlock() {
        synchronized (this) {
            if (Objects.isNull(forceUnLockScript)) {
                // 优先调用SCRIPT LOAD加载Lua脚本
                forceUnLockScript = client.scriptLoad(ScriptConstants.ACQUIRE_FORCE_UNLOCK_SCRIPT);
            }
        }
        Long result = null;
        try {
            result = (Long) client.evalsha(forceUnLockScript, 1, name);
        } catch (JedisNoScriptException e) {
            // 当evalsha命令执行失败时，执行eval缓存脚本
            result = (Long) client.eval(ScriptConstants.ACQUIRE_FORCE_UNLOCK_SCRIPT, 1, name);
        } finally {
            if (result == 1L) {
                if (Objects.nonNull(future)) {
                    future.cancel(true);        // watchdog退出
                }
            }
        }
    }

    @Override
    public CompletableFuture<Void> lockAsync() {
        String visitorId = acquireVisitorId();
        return CompletableFuture.runAsync(() -> lock(visitorId), workerGroup);
    }

    @Override
    public CompletableFuture<Boolean> tryLockAsync() {
        String visitorId = acquireVisitorId();
        return CompletableFuture.supplyAsync(() -> tryLock(visitorId, -1L, TimeUnit.SECONDS), workerGroup);
    }

    @Override
    public CompletableFuture<Boolean> tryLockAsync(long time, TimeUnit unit) {
        String visitorId = acquireVisitorId();
        return CompletableFuture.supplyAsync(() -> tryLock(visitorId, time, unit), workerGroup);
    }

    @Override
    public CompletableFuture<Void> unlockAsync() {
        String visitorId = acquireVisitorId();
        return CompletableFuture.runAsync(() -> unlock(visitorId), workerGroup);
    }

    @Override
    public CompletableFuture<Void> forceUnlockAsync() {
        return CompletableFuture.runAsync(this::forceUnlock, workerGroup);
    }

    private void lock(String visitorId) {
        Long ttl = acquireLock(visitorId);
        if (ttl == -1L) {
            watchDog(visitorId); // 添加 watchdog
            return;
        }
        subscribe();
        try {
            while (true) {
                ttl = acquireLock(visitorId);
                if (ttl == -1) {
                    watchDog(visitorId);
                    break;
                }
                if (ttl >= 0) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(ttl));
                }
            }
        } finally {
            unsubscribe();
        }
    }

    private boolean tryLock(String visitorId, long waitTime, TimeUnit unit) {
        Objects.requireNonNull(unit);
        if (waitTime == -1) {
            return acquireLock(visitorId) == -1;
        }

        Long ttl = acquireLock(visitorId);
        if (ttl == -1) {
            watchDog(visitorId);
            return true;
        }
        subscribe();
        try {
            LockSupport.parkNanos(unit.toNanos(waitTime));
            ttl = acquireLock(visitorId);
            if (ttl == -1) {
                watchDog(visitorId);
                return true;
            }
        } finally {
            unsubscribe();
        }
        return false;
    }

    /**
     * 调用Lua脚本执行解锁原子操作
     */
    private void unlock(String visitorId) {
        if (Objects.isNull(unLockScript)) {
            synchronized (this) {
                if (Objects.isNull(unLockScript)) {
                    // 优先调用SCRIPT LOAD加载Lua脚本
                    unLockScript = client.scriptLoad(ScriptConstants.ACQUIRE_UNLOCK_SCRIPT);
                }
            }
        }

        Long result = null;
        try {
            result = (Long) client.evalsha(unLockScript, 1, name, visitorId,
                    String.valueOf(ScriptConstants.DEFAULT_KEY_TTL), visitorId);
        } catch (JedisNoScriptException e) {
            // 当 evalsha 命令执行失败时，执行eval缓存脚本
            result = (Long) client.eval(ScriptConstants.ACQUIRE_UNLOCK_SCRIPT, 1, name, visitorId,
                    String.valueOf(ScriptConstants.DEFAULT_KEY_TTL), visitorId);
        } finally {
            /**
             * 并发环境下，目标线程可能存在取锁成功但解锁失败的情况
             * 为了避免目标线程多次取锁/解锁操作导致重入次数永远不为 0，watchDog 不退出导致其他线程取不到锁的情况，TODO
             * 需要在解锁出现异常时重设当前线程的 visitorId，自身和其他线程等待孤锁自然失效
             */
            if (Objects.isNull(result)) {
                threadId.remove();                                 // reset visitorId
                if (Objects.nonNull(future)) {
                    future.cancel(true);        // watchdog exit
                }
                return;
            }
            if (result == 1) {
                if (Objects.nonNull(future)) {
                    future.cancel(true);
                }
            } else if (result == 0) {
                throw new JedisLockException(String.format("attempt to unlock lock, not locked by " +
                        "current thread by visitor id: %s", acquireVisitorId()));
            }
        }
    }

    /**
     * 调用 Lua 脚本获取分布式锁
     * @param visitorId 线程标识
     * @return 加锁成功返回 -1，加锁失败返回该锁的剩余过期时间
     */
    private Long acquireLock(String visitorId) {
        // 双检锁形式加载脚本
        if (Objects.isNull(lockScript)) {
            synchronized (this) {
                if (Objects.isNull(lockScript)) {
                    // 优先调用 SCRIPT LOAD 加载 Lua 脚本
                    lockScript = client.scriptLoad(ScriptConstants.ACQUIRE_LOCK_SCRIPT);
                }
            }
        }

        try {
            return (Long) client.evalsha(lockScript, 1, name, visitorId,
                    String.valueOf(ScriptConstants.DEFAULT_KEY_TTL));
        } catch (JedisNoScriptException e) {
            // 当 evalsha 命令执行失败时，执行 eval 缓存脚本
            return (Long) client.eval(ScriptConstants.ACQUIRE_LOCK_SCRIPT, 1, name, visitorId,
                    String.valueOf(ScriptConstants.DEFAULT_KEY_TTL));
        }
    }

    /**
     * 获取访问者 id
     */
    private String acquireVisitorId() {
        String visitorId = threadId.get();
        if (Objects.isNull(visitorId)) {
            // 访问者唯一标识采用 uuid + threadId
            visitorId = String.format("%s:%s", UUID.randomUUID().toString(),
                    Thread.currentThread().getId());
            threadId.set(visitorId);
        }
        return visitorId;
    }

    /**
     * 每隔 10 秒重设当前锁的 TTL
     *
     * @param visitorId 线程标识
     */
    private void watchDog(String visitorId) {
        if (Objects.nonNull(future)) {
            future.cancel(true);    // 从队列中移除任务
        }
        future = watchGroup.scheduleAtFixedRate(() -> {
            if (Objects.isNull(updateTTLScript)) {
                updateTTLScript = client.scriptLoad(ScriptConstants.UPDATE_LOCK_TTL_SCRIPT);
            }
            try {
                client.evalsha(updateTTLScript, 1, name, visitorId,
                        String.valueOf(ScriptConstants.DEFAULT_KEY_TTL));
            } catch (JedisNoScriptException e) {
                // 当evalsha命令执行失败时，先执行eval缓存脚本
                client.eval(ScriptConstants.UPDATE_LOCK_TTL_SCRIPT, 1, name, visitorId,
                        String.valueOf(ScriptConstants.DEFAULT_KEY_TTL));
            }
        }, ScriptConstants.DEFAULT_UPDATE_TIME, ScriptConstants.DEFAULT_UPDATE_TIME, TimeUnit.SECONDS);
    }

    /**
     * 订阅目标频道，等待信号来临时唤醒当前线程继续拿锁
     */
    private void subscribe() {
        Thread thread = Thread.currentThread();
        synchronized (subscribers) {
            if (!isListener) {
                isListener = true;
                workerGroup.execute(() ->
                    client.subscribe(() -> {
                            if (Objects.nonNull(future)) {
                                future.cancel(true); // TODO 断线重连时为什么要取消已经获得锁的线程的 watchdog
                            }
                        }, new SubscribeListener(subscribers), name
                    )
                );
            }
            subscribers.add(thread);
        }
    }

    /**
     * 取消订阅的目标线程
     */
    private void unsubscribe() {
        Thread thread = Thread.currentThread();
        synchronized (subscribers) {
            subscribers.remove(thread);
        }
    }

}
