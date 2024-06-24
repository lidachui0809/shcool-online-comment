package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.LOCK_KEY;

@Component
@Slf4j
public class RedisClient {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 缓存击穿解决方案 -互斥锁
     * @param keyPrefix key前缀
     * @param idT
     * @param typeReturn
     * @param opeFun 数据库查找对象
     * @param timeOut
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public  <R,ID> R queryWithAbortLock(String keyPrefix, ID idT, Class<R> typeReturn, Function<ID,R> opeFun, Long timeOut, TimeUnit unit){
        String key =keyPrefix+idT;
        String cacheJson = this.get(key,String.class);
        /* redis未命中 */
        if (cacheJson==null|| StrUtil.isEmpty(cacheJson)) {
            try {
                /* 尝试获取互拆搜 */
                if (!tryLock(idT)) {
                    /* 获取失败 休眠 再次重新获取 */
                    Thread.sleep(50);
                    log.info("获取互拆锁失败！:{}",Thread.currentThread().getId());
                    queryWithAbortLock(keyPrefix,idT,typeReturn,opeFun,timeOut,unit);
                }
                log.info("获取互拆锁:{}",Thread.currentThread().getId());
                /* 获取成功 缓存重构 */
                R r=opeFun.apply(idT);
                this.set(key, r,timeOut,unit);
                return r;
            }catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                /* 释放锁 */
                relLock(idT);
            }
        }
        /* 缓存命中 直接返回 */
        return JSONUtil.toBean(cacheJson,typeReturn);
    }

    /*
     * 这里基于redis SETNX命令实现 SETNX:当一个key被写入数据后 将不再允许对其进行修改 也就是这个key时具有原子性的
     * 所以在高并发场景下 只有任何一个线程写入key 其它线程就无法修改这个key(set结果为false)
     * 也就表示这个缓存重构已经锁定 其它线程只能等待
     * */
    private boolean tryLock(Object key){
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY +key, "lock", 8, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(locked);
    }

    private void relLock(Object key){
        /* 删除key 也就是释放锁 */
        redisTemplate.delete(LOCK_KEY +key);
    }

    public void set(String key,Object value,Long timeOut,TimeUnit uint){
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),timeOut,uint);
    }

    public <T> T get(String key,Class<T> type){
        String jsonStr = redisTemplate.opsForValue().get(key);
        if (jsonStr==null||jsonStr.isEmpty()) {
            return null;
        }
        return JSONUtil.toBean(jsonStr,type);
    }

}
