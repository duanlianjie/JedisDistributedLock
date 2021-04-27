package com.github.jedis.lock;

import redis.clients.jedis.JedisPubSub;

/**
 * 封装实现分布式锁需要使用到的相关 redis 命令类
 */
public interface LockCommand {
    Object eval(String script, int keyCount, String... params);

    Object evalsha(String script, int keyCount, String... params);

    String scriptLoad(String script);

    void subscribe(Runnable callBack, JedisPubSub jedisPubSub, String... channels);
}
