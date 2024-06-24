package com.hmdp.inspector;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
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
* */
public class LoginAccessInspector implements HandlerInterceptor {


//    private StringRedisTemplate redisTemplate;
//
//    public LoginAccessInspector(StringRedisTemplate redisTemplate) {
//        this.redisTemplate = redisTemplate;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

//        Map<Object, Object> userMap
//                = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        if (userMap.isEmpty()) {
//            response.setStatus(401);
//            return false;
//        }
//        UserDTO userDTO = BeanUtil.mapToBean(userMap, UserDTO.class,false);
//        UserHolder.saveUser(userDTO);
        /* 基于TokenInspector 只需要判断ThreadLocal中是否存在User即可 */
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }

//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        UserHolder.removeUser();
//    }
}
