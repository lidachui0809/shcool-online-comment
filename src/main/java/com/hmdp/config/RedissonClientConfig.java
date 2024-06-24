package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonClientConfig {

    @Bean
    public RedissonClient redissonClient(){
        /* 配置redis的信息 */
        /* Redisson 一个用于java程序的分布式开发工具 提高了分布式锁 以及消息队列等各种工具 */
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        return Redisson.create(config);
    }
}
