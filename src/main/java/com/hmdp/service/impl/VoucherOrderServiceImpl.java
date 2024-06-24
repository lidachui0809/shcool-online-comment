package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.IdGenerateRedisHandler;
import com.hmdp.utils.UserHolder;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
@EnableAspectJAutoProxy(exposeProxy = true)
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @Autowired
    private IdGenerateRedisHandler idGenerateRedisHandler;


    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static DefaultRedisScript<Long> VOUCHER_ORDER_SCRIPT;

    private IVoucherOrderService proxy;


    //    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    static {
        VOUCHER_ORDER_SCRIPT = new DefaultRedisScript<>();
        VOUCHER_ORDER_SCRIPT.setLocation(new ClassPathResource("voucherOrder.lua"));
        VOUCHER_ORDER_SCRIPT.setResultType(Long.class);
    }
//    @PostConstruct
//    //在类创建时 会调用
//    public void init() {
////        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//
    //订单消息队列处理类
//    private class VoucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//            //不断循环获取数据
//            while (true) {
//                try {
//                    /* XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 > */
//                    /* 参数: 组g1的c1的消费者;取消息数,是否阻塞,阻塞时间;stream队列的key,id */
//
//                    List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
//                    if (read == null || read.isEmpty()) {
//                        //灭有队列消息 继续
//                        continue;
//                    }
//
//                    Map<Object, Object> value = read.get(0).getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                    createOrder(voucherOrder);
//                    /* 确认ACK */
//                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", read.get(0).getId());
//                } catch (Exception ex) {
//                    log.error("消息处理发送异常:{}", ex.getMessage());
//                    handlerPendingListData();
//                }
//            }
//        }
//    }
//
//    /* 处理未没有被正常消费的队列消息(也就是在pendingList中的数据) */
//    private void handlerPendingListData() {
//        while (true) {
//            /* XREADGROUP GROUP g1 c1 COUNT 1  STREAMS s1 0 */
//            /* 参数: 组g1的c1的消费者;取消息数,是否阻塞,阻塞时间;stream队列的key,id */
//            List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1).block(Duration.ofSeconds(1)), StreamOffset.create("stream.orders", ReadOffset.from("0")));
//            if (read == null || read.isEmpty()) {
//                //灭有队列消息 继续
//                continue;
//            }
//            try {
//                Map<Object, Object> value = read.get(0).getValue();
//                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                createOrder(voucherOrder);
//                /* 确认ACK */
//                stringRedisTemplate.opsForStream().acknowledge("s1", "g1", read.get(0).getId());
//            } catch (Exception ex) {
//                log.error("队列消息处理失败！e:{}", ex.getMessage());
//            }
//        }
//    }
//
//    /*
//     * 优惠券秒杀
//     * */
//    @Override
////    @Transactional
//    public Result voucherOrder(Long voucherId) {
//        SeckillVoucher seckillVoucher
//                = iSeckillVoucherService.getById(voucherId);
//        if (seckillVoucher == null) {
//            return Result.fail("未找到优惠券信息！");
//        }
//        /* 判断时效性 */
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("优惠券还未开始！");
//        }
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("活动结束了！");
//        }
//        /* 判断库存 */
//        if (seckillVoucher.getStock() <= 0) {
//            return Result.fail("没库存了！");
//        }
//        Long userId = UserHolder.getUser().getId();
//        /* 一人一单   */
//        /* 单体系统 */
//        /* 高并发情况下 还是会出现重复下单 这里使用悲观锁  */
//        /* string类型如果值相同 其引用地址是相同的 也就说 对于相同用户id 他的userId的对象始终是同一个 */
//        /* 所以只有当同一个用户下单 这个锁才会生效 */
////        int count = voucherOrderService.count(new LambdaQueryWrapper<VoucherOrder>()
////                .eq(VoucherOrder::getUserId, userId));
////        if (count > 0) {
////            return Result.fail("你已经下过单了！");
////        }
////
////
////
////
////        /* intern() 将字符串加入常量池 */
////        synchronized (userId.toString().intern()) {
////            /* 由于是在不是代理类调用 会导致事务失效 所以只能获得动态代理对象调用 实现事务 */
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.singleVoucherOrder(userId, voucherId);
////        }
////
////        /* 一人一单 因此 需要锁定userId */
//        RLock lock = redissonClient.getLock("lock:order:"+userId);
//        if (!lock.tryLock()) {
//            return Result.fail("一个用户最多下一单！");
//        }
////        RedisSimpLock lock = new RedisSimpLock(stringRedisTemplate, String.valueOf(userId));
////        /* 如果在分布式集群环境下 使用jdk的锁 就无法限制了 所以需要分布式锁 */
////        if (!lock.tryLock(10L)) {
////            return Result.fail("一个用户最多下一单！");
////        }
//        /* 防止业务发生异常  使用try{} 释放锁 */
//        try{
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.singleVoucherOrder(userId, voucherId);
//        }finally {
//            /* 释放 */
//            lock.unlock();
//        }
//    }
//
//    private void createOrder(VoucherOrder voucherOrder) {
//        /* 一人一单 单体系统  */
//        /* 高并发情况下 还是会出现重复下单 这里使用悲观锁  */
//        /* string类型如果值相同 其引用地址是相同的 也就说 对于相同用户id 他的userId的对象始终是同一个 */
//        /* 所以只有当同一个用户下单 这个锁才会生效 */
//        /* 一人一单 因此 需要锁定userId */
//        RLock lock = redissonClient.getLock("lock:order:" + voucherOrder.getUserId());
//        if (!lock.tryLock()) {
//            Result.fail("一个用户最多下一单！");
//        }
//        /* 防止业务发生异常  使用try{} 释放锁 */
//        try {
//            /*  AopContext.currentProxy() 他是通过ThreadLocal获得代理对象 子线程获取不到 需要在主线程获取 */
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            proxy.singleVoucherOrder(voucherOrder);
//        } catch (Exception e) {
//            log.error("创建订单发送异常:{}", e.getMessage());
//        } finally {
//            /* 释放 */
//            lock.unlock();
//        }
//    }


    @Override
    public Result voucherOrder(Long voucherId) {
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("未找到优惠券信息！");
        }

        /* 在lua脚本中 直接判断库存 用户是否已经下单 将库存信息保存在redis中
            同时用set将订单信息保存在redis中 shopId:[userId,id2,id3...] */
        /* 将部分数据库查找逻辑 替换使用redis 配合lua脚本实现 消息队列 并使用异步执行 提高效率*/
        Long userId = UserHolder.getUser().getId();
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        Long result = stringRedisTemplate.execute(VOUCHER_ORDER_SCRIPT, Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
//                String.valueOf(orderId)
        );
        if (result.intValue() != 0) {
            return Result.fail(result == 1 ? "库存不足!" : "你已经下过单了！");
        }
        long orderId = idGenerateRedisHandler.getIncrId("order");
        //使用mq将消息发送出去 发送到名为order.ex的交换机中
        rabbitTemplate.convertAndSend("order.ex","voucher.order",new VoucherOrder(userId,voucherId,orderId));
        return Result.ok("下单成功！");
    }

//    @Override
//    public Result voucherOrder(Long voucherId) {
//        SeckillVoucher seckillVoucher
//                = iSeckillVoucherService.getById(voucherId);
//        if (seckillVoucher == null) {
//            return Result.fail("未找到优惠券信息！");
//        }
//        /* 判断时效性 */
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("优惠券还未开始！");
//        }
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("活动结束了！");
//        }
//        /* 一人一单   */
//        /* 单体系统 */
//        /* 高并发情况下 还是会出现重复下单 这里使用悲观锁  */
//        /* string类型如果值相同 其引用地址是相同的 也就说 对于相同用户id 他的userId的对象始终是同一个 */
//        /* 所以只有当同一个用户下单 这个锁才会生效 */
//        int count = voucherOrderService.count(new LambdaQueryWrapper<VoucherOrder>()
//                .eq(VoucherOrder::getUserId, userId));
//        if (count > 0) {
//            return Result.fail("你已经下过单了！");
//        }
//
//
//
//
//        /* intern() 将字符串加入常量池 */
//        synchronized (userId.toString().intern()) {
//            /* 由于是在不是代理类调用 会导致事务失效 所以只能获得动态代理对象调用 实现事务 */
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.singleVoucherOrder(userId, voucherId);
//        }
//
//        /* 一人一单 因此 需要锁定userId */
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        if (!lock.tryLock()) {
//            return Result.fail("一个用户最多下一单！");
//        }
//        RedisSimpLock lock = new RedisSimpLock(stringRedisTemplate, String.valueOf(userId));
//        /* 如果在分布式集群环境下 使用jdk的锁 就无法限制了 所以需要分布式锁 */
//        if (!lock.tryLock(10L)) {
//            return Result.fail("一个用户最多下一单！");
//        }
//        /* 防止业务发生异常  使用try{} 释放锁 */
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.singleVoucherOrder(userId, voucherId);
//        } finally {
//            /* 释放 */
//            lock.unlock();
//        }
//    }

    @Override
    @Transactional
    public Result singleVoucherOrder(VoucherOrder voucherOrder) {
        /* 判断用户订单 */
//        int count = voucherOrderService.count(new LambdaQueryWrapper<VoucherOrder>()
//                .eq(VoucherOrder::getUserId, userId));
//        if (count > 0) {
//            return Result.fail("你已经下过单了！");
//        }
        /* 使用乐观锁 保证高并发下 数据一致性 防止超卖 */
        LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<SeckillVoucher>().setSql("stock=stock-1")
                .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId()).gt(SeckillVoucher::getStock, 0);
        if (!iSeckillVoucherService.update(updateWrapper)) {
            return Result.fail("没库存了！");
        }

        /* 创建订单 */
//        long oderId = idGenerateRedisHandler.getIncrId("order");
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(oderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
        voucherOrderService.save(voucherOrder);
        return Result.ok();
    }


    //这里直接使用了注解的方式创建队列和交换机 以及binging 创建一个监听器
    //设置消息确认机制为手动确认 ackMode="MANUAL"
    @RabbitListener(bindings = @QueueBinding(value = @Queue("voucher.order"), exchange =
    @Exchange(value = "order.ex", type = ExchangeTypes.DIRECT),
            key = "voucher.order"),ackMode = "MANUAL")
    public void orderHandlerLister(VoucherOrder voucherOrder, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        if(voucherOrder!=null){
            try {
                createOrder(voucherOrder);
                //手动确认ack
                channel.basicAck(deliveryTag,false);
            } catch (Exception e) {
                try {
                    log.error("发送异常 重新发送队列...");
                    //发生异常 重新投递队列
                    channel.basicNack(deliveryTag,false,true);
                } catch (IOException ex) {
                    log.error("队列重新投递失败！");
                }
            }
        }
    }

    private void createOrder(VoucherOrder voucherOrder) throws Exception {
        /* 一人一单 单体系统  */
        /* 高并发情况下 还是会出现重复下单 这里使用悲观锁  */
        /* string类型如果值相同 其引用地址是相同的 也就说 对于相同用户id 他的userId的对象始终是同一个 */
        /* 所以只有当同一个用户下单 这个锁才会生效 */
        /* 一人一单 因此 需要锁定userId */
        RLock lock = redissonClient.getLock("lock:order:" + voucherOrder.getUserId());
        if (!lock.tryLock()) {
            Result.fail("一个用户最多下一单！");
        }
        /* 防止业务发生异常  使用try{} 释放锁 */
        try {
            proxy.singleVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("创建订单发送异常:{}", e.getMessage());
            throw new Exception("订单异常!");
        } finally {
            /* 释放 */
            lock.unlock();
        }
    }


}
