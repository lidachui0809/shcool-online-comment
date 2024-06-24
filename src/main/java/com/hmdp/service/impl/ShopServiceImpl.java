package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisClient redisClient;

    /*
    * 缓存穿透(这里未实现)
    *   当请求的数据在redis和数据库中都不存在时 就会导致大量请求发送到数据库 导致数据库压力增大
    *   redis空值记录(记录这些空值,并设置较短声明周期,减少数据库压力) 布隆过滤器 (在访问缓存之前 判断是否存在这个数据 不存在直接拒绝 存在一定的误差)
    * 缓存雪崩
    *   当同一时间大量的key过期/redis宕机 导致缓存无法命中 数据库突然收到大量请求
    *   避免相同的TLL redis集群 访问限流
    * 缓存击穿
    *   热点数据过期 并且其缓存重建时间长 导致缓存未命中 且大量请求到数据库
    *   互斥锁 逻辑过期
    *
    * */
    @Override
    public Result getShopById(Long id) {
        return Result.ok(getShopWithAbortLock(id));
    }

    /*
    * 缓存更新策略 先更新数据库 在删缓存 这种情况下可以最大可能保证数据一致性
    * 对于更新后数据的缓存交给读操作发生时写入
    * */
    @Override
    public Result updateShopById(Shop shop) {
        boolean update = updateById(shop);
        redisTemplate.delete(CACHE_SHOP_INFO_KEY+shop.getId());
        return Result.ok("删除成功！");
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if(x==null||y==null){
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page);
        }

        int from=(current-1)*DEFAULT_BATCH_SIZE;
        int end=(current)*DEFAULT_BATCH_SIZE;
        // 根据类型以及xy获得该范围内的店铺信息 这里使用redis的geo实现
        //查询类型下的所有店铺地址信息
        String key=SHOP_GEO_KEY+typeId;
        /* GEOSEARCH key 122.234 20.32 10 KM WITHDIST  */
        // geo搜索 key值中 距离这个经纬度  10km的 成员 同时显示出距离
        // geo search并没有提供分页查找的功能 需要自己手动截取
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y), new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs
                        .newGeoSearchArgs().limit(end)
                        .includeDistance());
        if(geoResults==null)
            return Result.ok(Collections.emptyList());
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = geoResults.getContent();
        //如果截取长度大于数据长度 则返回空
        if(content.size()<=from)
            return Result.ok(Collections.emptyList());
        // skip() 截取指 定位置之后的所有数据
        Map<Long, Distance> distanceHashMap = new LinkedHashMap<>();
        content.stream().skip(from).forEach(result -> {
            String shopId = result.getContent().getName();
            distanceHashMap.put(Long.valueOf(shopId),result.getDistance());
        });
        //注意 hashmap是无序的 如果这里使用hashMap会导致id顺序变换
        Set<Long> shopIds = distanceHashMap.keySet();
        String joinIds = StrUtil.join(",", shopIds);
        List<Shop> shops = query().in("id", shopIds)
                .last(" ORDER BY FIELD(id," + joinIds + ")")
                .list();
        for (Shop shop : shops) {
            shop.setDistance(distanceHashMap.get(shop.getId()).getValue());
        }
        return Result.ok(shops);
    }

    /* 缓存击穿-互斥锁解决方案 */
    private Shop getShopWithAbortLock(Long id){
        Shop shop = redisClient.queryWithAbortLock(CACHE_SHOP_INFO_KEY, id, Shop.class, this::getById,
                30L, TimeUnit.MINUTES);
        return shop;
    }


}
