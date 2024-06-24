package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

/**
 * 自定义的全局id生成器/分布式id生成器
 * 基于redis的string类型字段的原子性 可以使用redis的 increase命令 达到自增效果
 */
@Component
public class IdGenerateRedisHandler {

    @Autowired
    private StringRedisTemplate redisTemplate;
    public static final long BEGIN_TIME=1028822400;/* 2002年 */


    /* id: 一位符号位 31位时间戳 32位redis自增id */
    public long getIncrId(String keyPrefix){
        long nowTimeSamp= LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timeSamp=nowTimeSamp-BEGIN_TIME;
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long redisId = redisTemplate.opsForValue().increment("inr:redis:id:" + date +":"+ keyPrefix);
        /* >> 位运算符号 之后进行或运算  */
        return timeSamp<<32|redisId;
    }







}
