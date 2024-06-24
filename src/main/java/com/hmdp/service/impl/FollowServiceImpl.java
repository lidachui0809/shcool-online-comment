package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.ScrollerPageResult;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followUser(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            if (save(follow)) {
                redisTemplate.opsForSet().add(key, followUserId.toString());
            }
            return Result.ok("关注成功！");
        } else {
            LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, followUserId)
                    .eq(Follow::getUserId, userId);
            if (remove(wrapper)) {
                redisTemplate.opsForSet().remove(key, followUserId.toString());
            }
            return Result.ok("取消关注!");
        }
    }

    @Override
    public Result commonFollow(Long followUserId) {
        /* 这里配合redis的set 查找交集 */
        Long userId = UserHolder.getUser().getId();
        String blogUserKey = "follow:" + userId;
        String followUserKey = "follow:" + followUserId;
        Set<String> commonUserIds
                = redisTemplate.opsForSet().intersect(blogUserKey, followUserKey);
        /* 查看redis缓存中是否存在 */
        if (commonUserIds == null || commonUserIds.isEmpty()) {
            /* 查找他们共同的关注者id  */
            List<String> followIds
                    = getBaseMapper().querySameFollows(followUserId, userId);
            if (followIds == null || followIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            /* 查询到的数据 放入redis缓存 */
            String[] idsStr = followIds.toArray(new String[0]);
            redisTemplate.opsForSet().add(blogUserKey, idsStr);
            redisTemplate.opsForSet().add(followUserKey, idsStr);
            List<UserDTO> userDTOS = convertUserDTOS(followIds);
            return Result.ok(userDTOS);
        }
        List<String> collectIdsStr = new ArrayList<>(commonUserIds);
        List<UserDTO> userDTOS = convertUserDTOS(collectIdsStr);
        return Result.ok(userDTOS);
    }


    private List<UserDTO> convertUserDTOS(List<String> commonUserIds) {
        List<Long> commonIds = commonUserIds.stream().map(Long::parseLong)
                .collect(Collectors.toList());
        return userService.listByIds(commonIds).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
    }
}
