package com.hmdp.config;

import com.hmdp.inspector.LoginAccessInspector;
import com.hmdp.inspector.TokenRefreshInspector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginAccessInspector())
                .excludePathPatterns(
                        "/shop/**",
                        "/user/code",
                        "/user/login",
                        "/voucher/**",
                        "/shop-type/**",
                        "/blog/likes/*",
                        "/blog/hot"
                ).order(1);
        registry.addInterceptor(new TokenRefreshInspector(redisTemplate)).order(0);
    }
}
