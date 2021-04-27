package com.github.jedis.lock;

import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * 订阅者实现
 */
public class SubscribeListener extends JedisPubSub {
    private final Set<Thread> subscribers;

    protected SubscribeListener(Set<Thread> subscribers) {
        this.subscribers = subscribers;
    }

    @Override
    public void onMessage(String channel, String message) {
        synchronized (subscribers) {
            // 唤醒等待的业务线程
            subscribers.parallelStream().forEach(LockSupport::unpark);
        }
    }
}
