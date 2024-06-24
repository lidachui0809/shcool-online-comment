package com.hmdp.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAMQPConfig {

    /* AMQP传递java对象 默认将对象使用jdk的序列滑方式 转成字节 可以自定义消息转换 这里使用jackson */
    /* publisher和receiver的converter需要保持一致 */
    @Bean
    public MessageConverter jackSonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }
}
