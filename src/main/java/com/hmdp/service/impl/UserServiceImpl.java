package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldType.UINT_32;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result code(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,1,TimeUnit.MINUTES);
        log.info("验证码已发送:{}",code);
        return Result.ok("验证码已发出！");
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误！");
        }
        String code = redisTemplate.opsForValue()
                .get(LOGIN_CODE_KEY + loginForm.getPhone());
        if (!Objects.equals(code, loginForm.getCode())) {
            return Result.fail("验证码错误！");
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StrUtil.isNotBlank(loginForm.getPhone())
                ,User::getPhone,loginForm.getPhone());
        User user = getOne(queryWrapper);
        if (user == null) {
            user=createNewUser(loginForm);
            save(user);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String token = UUID.randomUUID().toString(true);
        /* 将字段的所有值转string类型 */
        Map<String, Object> map =
                BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((name,value)->value.toString()));
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
        redisTemplate.expire(LOGIN_USER_KEY+token,30L, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result me() {
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /**
     * 用户签到
     * 这里是基于redis的bitmap数据结构实现的 使用二进位的位来标识签到状态 index表示签到日期 节约内存
     * @return
     */
    @Override
    public Result userSign() {
        /* setbit sign:202405:1015 0 1 */
        LocalDateTime now = LocalDateTime.now();
        String time = DateUtil.format(now, "yyyy:MM");
        String key=USER_SIGN_KEY+time+":"+UserHolder.getUser().getId();
        int day = now.getDayOfMonth()-1;
        redisTemplate.opsForValue().setBit(key,day,true);
        return Result.ok("已签到！");
    }

    /**
     * 连续签到次数
     * @return
     */
    @Override
    public Result signCount() {
        // 按位遍历bitmap中的数据 bitmap数据是基于string类型数据实现的
        int length=32;//一个月最多31 然后最后一位进行位运算 需要32
        int max=0,count=0;
        LocalDateTime now = LocalDateTime.now();
        String time = DateUtil.format(now, "yyyy:MM");
        String key=USER_SIGN_KEY+time+":"+UserHolder.getUser().getId();
        int day = now.getDayOfMonth();
        /* bitfield sign:202405:1015 get u10 0 */
        //获得key中无符号从0开始的10个比特位
        List<Long> result = redisTemplate.opsForValue().bitField(key,BitFieldSubCommands
                .create().get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0));
        if(result==null||result.size()==0)
            return Result.ok();
        int num = result.get(0).intValue();
        while (length!=0){
            //和1 与运算 获得末尾数
            if((num&1)==1){
                count++;
                if(count>=max)
                    max=count;
            }else {
                count=0;
            }
            /* 向右移动比特位 */
            num>>=1;
            length--;
        }
        return Result.ok(max);
    }

    private User createNewUser(LoginFormDTO loginForm) {
        User user = new User();
        user.setNickName(USER_NAME_PREFIX+RandomUtil.randomString(10));
        user.setPhone(loginForm.getPhone());
        return user;
    }
}
