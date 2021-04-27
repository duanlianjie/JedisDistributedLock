package com.github.jedis.lock;

import com.github.jedis.exceptions.AcquireLockException;
import com.github.jedis.exceptions.JedisLockException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * 注解切面类
 */
@Aspect
@Component
public class LockAspect {
    @Resource
    private JedisLockManager jedisLockManager;

    /**
     * 切入点，拦截所有标记有 @DistributedLock 的方法
     */
    @Pointcut("@annotation(com.github.jedis.lock.DistributedLock)")
    public void pointcut() {
    }

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint joinPoint) {
        Class targetClass = joinPoint.getTarget().getClass();
        // 获取目标入参的参数类型
        Class<?>[] types = ((MethodSignature) joinPoint.getSignature()).getParameterTypes();
        String methodName = joinPoint.getSignature().getName();
        try {
            Method method = targetClass.getDeclaredMethod(methodName, types);
            if (method.isAnnotationPresent(DistributedLock.class)) {
                DistributedLock metadata = method.getAnnotation(DistributedLock.class);
                JedisLock lock = jedisLockManager.getLock(metadata.name());
                try {
                    switch (metadata.type()) {
                        case LOCK:
                            lock.lock();
                            break;
                        case TRY_LOCK:
                            long time = metadata.time();
                            boolean result = time < 0 ? lock.tryLock() : lock.tryLock(time, metadata.unit());
                            if (!result) {
                                throw new AcquireLockException("Unable to acquire distributed lock");
                            }
                    }
                     return joinPoint.proceed(joinPoint.getArgs());//执行目标方法
                } finally {
                    lock.unlock();  //无论如何都要尝试释放
                }
            }
        } catch (Throwable e) {
            throw e instanceof AcquireLockException ? new JedisLockException("Try again", e)
                    : new RuntimeException(e);  // 所有异常抛出
        }
        return null;
    }
}