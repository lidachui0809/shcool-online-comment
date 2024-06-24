package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {


    @Resource
    private IFollowService followService;

    /* 获得用户关注状态 */
    @GetMapping("/or/not/{blogUserId}")
    public Result isFollowUser(@PathVariable Long blogUserId){
        return followService.isFollow(blogUserId);
    }


    @PutMapping("/{blogUserId}/{isFollow}")
    public Result isFollow(@PathVariable Long blogUserId, @PathVariable Boolean isFollow){
        return followService.followUser(blogUserId,isFollow);
    }

    @GetMapping("/common/{blogUserId}")
    public Result commonFollow(@PathVariable Long blogUserId){
        return followService.commonFollow(blogUserId);
    }

}
