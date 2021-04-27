package com.github.jedis.tests;

import com.github.jedis.lock.DistributedLock;
import com.github.jedis.lock.JedisLockManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@SpringBootApplication
public class LockBySpring {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(LockBySpring.class, args);
        User user = (User) context.getBean("user");
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.execute(user::testA);
        executorService.execute(user::testA);
        executorService.execute(() -> System.out.printf("response: %s%n", user.testD("Hello jedis-lock")));
    }
}

@ComponentScan("com.github.jedis")
@Configuration
class SpringConfiguration {
    @Bean
    public JedisCluster jedisCluster() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMinIdle(20);
        poolConfig.setMaxIdle(50);
        poolConfig.setMaxTotal(100);
        poolConfig.setMaxWaitMillis(2000);
        return new JedisCluster(new HostAndPort("127.0.0.1",6399), 10000, 10000, 100,"123456", poolConfig);
    }

    @Bean
    public JedisLockManager jedisLockManager() {
        return new JedisLockManager(jedisCluster());
    }
}

@Component
class User {
    @DistributedLock(name = "mylock")
    public void testA() {
        testB();
    }

    @DistributedLock(name = "mylock")
    public void testB() {
        testC();
    }

    @DistributedLock(name = "mylock")
    public void testC() {
        try {
            System.out.println(String.format("thread: %s get lock", Thread.currentThread().getName()));
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            //...
        }
    }

    @DistributedLock(name = "mylock2")
    public String testD(String str) {
        Objects.requireNonNull(str);
        System.out.printf("request: %s%n", str);
        return str;
    }
}
