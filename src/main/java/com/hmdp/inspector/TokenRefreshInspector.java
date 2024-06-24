package com.hmdp.inspector;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

/*
*
* 刷新用户token时效 并将user存入ThreadLocal 这里不拦截 只刷新
* */
public class TokenRefreshInspector implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public TokenRefreshInspector(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if (!StrUtil.isBlank(token)) {
            Map<Object, Object> userMap
                    = redisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);
            if (userMap.isEmpty()) {
                return true;
            }
            UserDTO userDTO = BeanUtil.mapToBean(userMap, UserDTO.class, false);
            UserHolder.saveUser(userDTO);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        /* 每次请求完成后 移除UserHolder 防止内存泄漏 */
        UserHolder.removeUser();
    }
}
