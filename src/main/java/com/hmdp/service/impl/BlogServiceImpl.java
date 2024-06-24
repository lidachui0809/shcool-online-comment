package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.ScrollerPageResult;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IUserService userService;

    @Resource
    private IFollowService followService;


    @Override
    public Result queryBlogWithId(Long id) {
        Blog blog = getById(id);
        isLikedBlog(blog);
        getBlogAuthorInfo(blog);
        return Result.ok(blog);
    }

    private void isLikedBlog(Blog blog) {
        String key = BLOG_LIKED_KEY + blog.getId();
        if (UserHolder.getUser() == null) {
            blog.setIsLike(false);
            return;
        }
        Long userId = UserHolder.getUser().getId();
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(score != null));
    }


    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        /* 判断用户是否点赞 */
        String sql = "";
        if (score != null) {
            redisTemplate.opsForZSet().remove(key, userId.toString());
            sql = "liked = liked - 1";
        } else {
            /* SortedSet 按照指定分数 排序 */
            redisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            sql = "liked = liked + 1";
        }
        boolean success = update().setSql(sql).eq("id", id).update();
        if (success) {
            return Result.ok();
        }
        return Result.fail("操作失败！");
    }

    @Override
    public Result queryBlogPage(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            getBlogAuthorInfo(blog);
            isLikedBlog(blog);
        });
        return Result.ok(records);
    }

    private void getBlogAuthorInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryLikesUsersByBlogId(Long blogId) {
        String key = BLOG_LIKED_KEY + blogId;
        /* 获得点赞前五名的用户id 这里基于sortedSet排序 */
        Set<String> top5Users = redisTemplate.opsForZSet().range(key, 0, 4);
        if (top5Users != null) {
            List<Long> userIds = top5Users.stream().map(Long::parseLong)
                    .collect(Collectors.toList());
            if (userIds.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }
            /* mysql查询结果 会自动按照id实现升序 改变原来位置 所以必须手动指定排序方式 */
            String joinIds = StrUtil.join(",", userIds);
            /* SELECT id,phone,password,nick_name,icon,create_time,update_time FROM tb_user WHERE id IN ( '5' , '1' ) ORDER BY FIELD(id,'5','1') */
            List<User> users = userService.query()
                    .in("id", userIds)
                    .last(" ORDER BY FIELD(id," + joinIds + ")")
                    .list();
            List<UserDTO> userDTOS = users.stream()
                    .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                    .collect(Collectors.toList());
            return Result.ok(userDTOS);
        }
        return Result.ok(Collectors.toList());
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        if (save(blog)) {
            new Thread(() -> {
                /* 将消息推送给粉丝 */
                List<Follow> follows = followService.lambdaQuery().eq(Follow::getFollowUserId, user.getId()).list();
                follows.forEach((follow -> {
                    /* 这里使用了sortedSet  feed:userId ,blogId 当前时间戳作为source */
                    redisTemplate.opsForZSet().add(FEED_KEY + follow.getUserId(), String.valueOf(blog.getId())
                            , System.currentTimeMillis());
                }));
            }).start();
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result followUserBolg(long lastId, int offset) {
        /* 滑动查找 使用redis 中 sortedSet的source范围查找 避免使用index查找出现重复数据  */
        //获得收件箱中的数据 (也就是在添加博客时推送给粉丝的数据)
        String key = FEED_KEY + UserHolder.getUser().getId();
        // ZRANGEBYSCORE key min max [WITHSCORES] offset count 这里使用降序查找
        // 这里的min max是用时间戳来表示source offset偏移量
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, lastId, offset, 2);
        if(typedTuples==null||typedTuples.isEmpty())
            return Result.ok("暂无数据！");
        int os = 0;
        ArrayList<Long> blogIds = new ArrayList<>(typedTuples.size());
        ArrayList<ZSetOperations.TypedTuple<String>> collect2 = new ArrayList<>(typedTuples);
        for (ZSetOperations.TypedTuple<String> stringTypedTuple : collect2) {
            blogIds.add(Long.valueOf(stringTypedTuple.getValue()));
        }
        /* 这里升序查找 */
        Set<ZSetOperations.TypedTuple<String>> typedTuples1 =
                redisTemplate.opsForZSet().rangeByScoreWithScores(key, 0, lastId, offset, 2);
        List<ZSetOperations.TypedTuple<String>> collect = new ArrayList<>(typedTuples1);
        /* 这里使用倒序 直接获得最小值 只有在第一个和后面值连续相等时 offest++ 否则直接跳出循环  */
        long minTime = collect.get(0).getScore().longValue();
        for (ZSetOperations.TypedTuple<String> stringTypedTuple : collect) {
            if (minTime == stringTypedTuple.getScore().longValue()) {
                os++;
                continue;
            }
            break;
        }
        String joinIds = StrUtil.join(",", blogIds);
        /* 根据blogId查出blog */
        List<Blog> blogs = query().in("id", blogIds)
                .last(" ORDER BY FIELD(id," + joinIds + ")")
                .list();
        ScrollerPageResult scrollerPageResult = new ScrollerPageResult();
        scrollerPageResult.setList(blogs);
        scrollerPageResult.setOffset(os);
        scrollerPageResult.setLastId(minTime);
        return Result.ok(scrollerPageResult);
    }


}
