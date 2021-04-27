package com.github.jedis.lock;

import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.TimeUnit;

/**
 * 基于 cluster 模式的相关 lock 命令
 */
public class ClusterLockCommand implements LockCommand {
    private final JedisCluster jedisCluster;

    protected ClusterLockCommand(JedisCluster jedisCluster) {
        this.jedisCluster = jedisCluster;
    }

    @Override
    public Object eval(String script, int keyCount, String... params) {
        return jedisCluster.eval(script, keyCount, params);
    }

    @Override
    public String scriptLoad(String script) {
        return jedisCluster.scriptLoad(script, script);
    }

    @Override
    public Object evalsha(String script, int keyCount, String... params) {
        return jedisCluster.evalsha(script, keyCount, params);
    }

    @Override
    public void subscribe(Runnable callBack, JedisPubSub jedisPubSub, String... channels) {
        try {
            jedisCluster.subscribe(jedisPubSub, channels);
        } catch (Throwable e) {
            callBack.run();
            try {
                TimeUnit.SECONDS.sleep(1);  //断线重连
            } catch (InterruptedException interruptedException) {
                //...
            }
        }
    }
}
