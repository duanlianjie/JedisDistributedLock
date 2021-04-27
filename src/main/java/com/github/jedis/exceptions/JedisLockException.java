package com.github.jedis.exceptions;

import redis.clients.jedis.exceptions.JedisException;

/**
 * 分布式锁异常基类
 */
public class JedisLockException extends JedisException {
    private static final long serialVersionUID = -1249619682740569559L;

    public JedisLockException(String message) {
        super(message);
    }

    public JedisLockException(Throwable e) {
        super(e);
    }

    public JedisLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
