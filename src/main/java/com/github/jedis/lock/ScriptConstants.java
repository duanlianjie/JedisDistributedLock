package com.github.jedis.lock;

/**
 * 缺省静态常量相关类
 */
public class ScriptConstants {
    /**
     * 加锁脚本
     * 如果成功获取到锁资源返回 -1，反之为 PTTL
     * KEY[1]: 锁的 key，ARGV[1]: hash 对象中的 key，ARGV[2]: 过期时间
     * <p>
     * Cluster 模式下不支持多参数，否则会触发异常 "CROSSSLOT Keys in request don't hash to the same slot"
     * <p>
     */
    public static final String ACQUIRE_LOCK_SCRIPT =    "if (redis.call('EXISTS', KEYS[1]) == 0) then " +
                                                            "redis.call('HINCRBY', KEYS[1], ARGV[1], 1); " +
                                                            "redis.call('PEXPIRE', KEYS[1], ARGV[2]); " +
                                                            "return -1; " +
                                                        "end; " +
                                                        "if (redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1) then " +
                                                            "redis.call('HINCRBY', KEYS[1], ARGV[1], 1); " +
                                                            "redis.call('PEXPIRE', KEYS[1], ARGV[2]); " +
                                                            "return -1; " +
                                                        "end; " +
                                                        "return redis.call('PTTL', KEYS[1]);";
    /**
     * 解锁脚本，解锁失败返回 0，完全解锁成功返回 1，一次解锁成功返回 2
     */
    public static final String ACQUIRE_UNLOCK_SCRIPT =  "if (redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1) then " +
                                                            "redis.call('HINCRBY', KEYS[1], ARGV[1], -1); " +
                                                            "redis.call('PEXPIRE', KEYS[1], ARGV[2]); " +
                                                            "if (tonumber(redis.call('HGET', KEYS[1], ARGV[1])) < 1) then " + // TODO 不用 tonumber 是否也可以？
                                                                "redis.call('DEL', KEYS[1]); " +
                                                                "redis.call('PUBLISH', KEYS[1], 1); " +
                                                                "return 1; " +
                                                            "end; " +
                                                            "return 2; " +
                                                        "end; " +
                                                        "return 0;";
    /**
     * 暴力解锁脚本，解锁失败返回 0，解锁成功返回 1
     */
    public static final String ACQUIRE_FORCE_UNLOCK_SCRIPT =    "if (redis.call('DEL', KEYS[1]) == 1) then " +
                                                                    "redis.call('PUBLISH', KEYS[1], 1); " +
                                                                    "return 1; " +
                                                                "end; " +
                                                                "return 0;";

    /**
     * 每隔 10 秒刷新当前锁 TTL 脚本
     */
    public static final String UPDATE_LOCK_TTL_SCRIPT = "if (redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1) then " +
                                                            "redis.call('PEXPIRE', KEYS[1], ARGV[2]); " +
                                                        "end;";
    /**
     * 锁的缺省 TTL：30000 毫秒
     */
    public static final int DEFAULT_KEY_TTL = 0x7530;

    /**
     * watchdog 缺省更新时间：10 秒
     */
    public static final int DEFAULT_UPDATE_TIME = 0xa;
}
