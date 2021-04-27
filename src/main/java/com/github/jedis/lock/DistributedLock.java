package com.github.jedis.lock;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Jedis 分布式锁注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {
    /**
     * 分布式锁资源名称
     */
    String name();

    /**
     * 尝试获取锁资源的最大时间，time 和 unit 参数仅针对 tryLock 方式
     */
    long time() default 0L;

    /**
     * 时间单位
     */
    TimeUnit unit() default TimeUnit.MILLISECONDS;

    /**
     * 锁类型，对应 JedisLock
     */
    LockType type() default LockType.LOCK;

    /**
     * 锁类型，对应JedisLock
     */
    enum LockType {
        /**
         * 对应 lock
         */
        LOCK,
        /**
         * 对应 tryLock
         */
        TRY_LOCK,
    }
}
