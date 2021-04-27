package com.github.jedis.lock;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.util.Pool;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 non-cluster 模式的相关lock命令，需要手动释放资源
 */
public class NonClusterLockCommand implements LockCommand {
    private final Pool<Jedis> pool;

    protected NonClusterLockCommand(Pool<Jedis> pool) {
        this.pool = pool;
    }

    @Override
    public Object eval(String script, int keyCount, String... params) {
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if (Objects.nonNull(jedis)) {
                return jedis.eval(script, keyCount, params);
            }
        } finally {
            if (Objects.nonNull(jedis)) {
                jedis.close();
            }
        }
        return null;
    }

    @Override
    public String scriptLoad(String script) {
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if (Objects.nonNull(jedis)) {
                return jedis.scriptLoad(script);
            }
        } finally {
            if (Objects.nonNull(jedis)) {
                jedis.close();
            }
        }
        return null;
    }

    @Override
    public Object evalsha(String script, int keyCount, String... params) {
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if (Objects.nonNull(jedis)) {
                return jedis.evalsha(script, keyCount, params);
            }
        } finally {
            if (Objects.nonNull(jedis)) {
                jedis.close();  // 手动释放资源
            }
        }
        return null;
    }

    @Override
    public void subscribe(Runnable callBack, JedisPubSub jedisPubSub, String... channels) {
        try {
            Jedis jedis = null;
            try {
                jedis = pool.getResource();
                if (Objects.nonNull(jedis)) {
                    jedis.subscribe(jedisPubSub, channels);
                }
            } finally {
                if (Objects.nonNull(jedis)) {
                    jedis.close();
                }
            }
        } catch (JedisConnectionException e) {
            callBack.run();
            try {
                TimeUnit.SECONDS.sleep(1);  // 断线重连
            } catch (InterruptedException interruptedException) {
                //...
            }
        }
    }
}
