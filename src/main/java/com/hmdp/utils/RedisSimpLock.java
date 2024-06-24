package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


/**
 * 使用Redis SETNX命令实现分布式锁 也可以使用 mysql/zookeeper 原子性特性实现
 *
 * 这里如果使用setNX命令 很明显 并不能解决锁重入 锁重试机制 或者整体实现会较麻烦 redis也提供了一个 Redission
 * 一个分布式开发工具 它提供了更加完善的分布式锁实现
 * */
@Deprecated
public class RedisSimpLock implements ILock{

    private StringRedisTemplate redisTemplate;
    private static final String LOCK_KEY="lock:key:";

    private String UUID= cn.hutool.core.lang.UUID.fastUUID().toString(true)+"-";
    private String key;

    private static DefaultRedisScript<Long> redisScript;

    /* 使用文件加载 lua脚本 */
    static {
        redisScript=new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("unlock.lua"));
    }

    public RedisSimpLock(StringRedisTemplate redisTemplate, String key) {
        this.redisTemplate = redisTemplate;
        this.key = key;
    }

    @Override
    public boolean tryLock(long timeOut) {
        long threadId = Thread.currentThread().getId();
        Boolean flag = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY + this.key, UUID+threadId, 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unLock() {
        /* 这里还有一个问题 在二次检测完成后
         单由于JVM机制导致阻塞 超过过期时间 锁被释放 再次出现线程安全问题  因此 需要保证del和sel的原子性 */

//        long threadId = Thread.currentThread().getId();
        /* 释放锁时  防止释放错误 对结果进行二次检验 */
//        String cacheV = redisTemplate.opsForValue().get(LOCK_KEY + this.key);
//        String threadV=UUID+ threadId;
//        if (Objects.equals(cacheV, threadV)) {
//            redisTemplate.delete(LOCK_KEY+this.key);
//        }

        /* 这里使用lua脚本 实现对redis命令的批处理 保证判断和删除的一致性 */
        Long execute = redisTemplate.execute(redisScript,
                Collections.singletonList(LOCK_KEY +this.key),
                UUID + Thread.currentThread().getId());
        System.out.println("执行结果:"+execute);
    }
}
