package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.IdGenerateRedisHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IShopService service;

    @Autowired
    private IdGenerateRedisHandler idGenerateRedisHandler;


    @Test
    public void test() throws InterruptedException {
        /* CountDownLatch 多线程同步的一个工具 可以实现阻塞当前线程 并等待指定数量的线程执行完毕  */
        CountDownLatch countDownLatch = new CountDownLatch(300);
        long l = System.currentTimeMillis();
        Runnable runnable = () -> { 
            for (int i = 0; i < 300; i++) {
                long id = idGenerateRedisHandler.getIncrId("order");
                System.out.println("id:" + id);
                countDownLatch.countDown();
            }
        };
        runnable.run();
        countDownLatch.wait();
        long l1 = System.currentTimeMillis();
        System.out.println("时间:" + (l1 - l) / 1000);
    }


    @Test
    public void testData() {
        /* redis提供了地理坐标的操作函数 和es的geo类似 可以查看当前坐标下附件以及摸个区域内的地点 */
        /* 这里使用typeId作为分组  将shopId和经纬度保存在redis中 在通过redis计算结果 */
        Map<Long, List<Shop>> collect =
                service.query().list().stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entity : collect.entrySet()) {
            String key = SHOP_GEO_KEY + entity.getKey();
            List<RedisGeoCommands.GeoLocation<String>> collect1 = entity.getValue().stream()
                    .map(shop ->
                            new RedisGeoCommands.GeoLocation<>(String.valueOf(shop.getId()), new Point(shop.getX(), shop.getY())))
                    .collect(Collectors.toList());
            redisTemplate.opsForGeo()
                    .add(key, collect1);
        }

    }


}

