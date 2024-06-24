package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryAllTypeList() {
        Map<Object, Object> cacheShopType = redisTemplate.opsForHash().entries(CACHE_SHOP_KEY);
        if (!cacheShopType.isEmpty()) {
            List<ShopType> shopTypes = new ArrayList<>();
            shopTypes.addAll(BeanUtil.mapToBean(cacheShopType, shopTypes.getClass(), false));
            return Result.ok(shopTypes);
        }
        List<ShopType> typeList =
                query().orderByAsc("sort").list();
        Map<String, Object> typeShopMap = BeanUtil.beanToMap(typeList,new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((field,value)->value.toString()));
        redisTemplate.opsForHash().putAll(CACHE_SHOP_KEY,typeShopMap);
        redisTemplate.expire(CACHE_SHOP_KEY,60, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
