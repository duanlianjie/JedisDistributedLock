package com.github.jedis.exceptions;

/**
 * 获取分布式锁失败异常类
 */
public class AcquireLockException extends JedisLockException {
    private static final long serialVersionUID = 8002282463557513191L;

    public AcquireLockException(String message) {
        super(message);
    }

    public AcquireLockException(Throwable e) {
        super(e);
    }

    public AcquireLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
