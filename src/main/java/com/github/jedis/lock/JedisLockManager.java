package com.github.jedis.lock;

import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.util.Pool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式锁管理类
 */
public class JedisLockManager {
    private Pool<Jedis> pool;
    private List<Pool<Jedis>> pools;
    private JedisCluster jedisCluster;
    private final LockType lockType;
    private final Map<String, JedisLock> lockMap = new ConcurrentHashMap<>(32);

    /**
     * 专用于主备的构造函数
     */
    public JedisLockManager(Pool<Jedis> pool) {
        this.pool = pool;
        lockType = LockType.SINGLE;
    }

    /**
     * 专用于红锁的构造函数
     */
    public JedisLockManager(List<Pool<Jedis>> pools) {
        this.pools = pools;
        lockType = LockType.RED;
    }

    /**
     * 专用于集群的构造函数
     */
    public JedisLockManager(JedisCluster jedisCluster) {
        this.jedisCluster = jedisCluster;
        lockType = LockType.CLUSTER;
    }

    /**
     * 锁类型
     */
    enum LockType {
        SINGLE, RED, CLUSTER
    }

    /**
     * 获取分布式锁
     */
    public JedisLock getLock(String name) {
        Objects.requireNonNull(name);
        JedisLock result;
        synchronized (this) {
            result = lockMap.get(name);
            if (Objects.isNull(result)) {
                switch (lockType) {
                    case SINGLE:
                        result = new JedisReentrantLock(name, new NonClusterLockCommand(pool));
                        break;
                    case RED:
                        List<JedisLock> locks = new ArrayList<>();
                        pools.forEach(pool -> locks.add(new JedisReentrantLock(name, new NonClusterLockCommand(pool))));
                        result = new JedisRedLock(locks);
                        break;
                    case CLUSTER:
                        result = new JedisReentrantLock(name, new ClusterLockCommand(jedisCluster));
                        break;
                }
                lockMap.put(name, result);
            }
        }
        return result;
    }
}
