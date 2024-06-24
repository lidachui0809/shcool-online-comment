-- 判断redis中的商品库存 用户是否已经下单
local voucherId=ARGV[1]
local userId=ARGV[2]
--local orderId=ARGV[3]

local storeKey="voucher:store:" .. voucherId

local orderKey="secskill:order:" .. voucherId

if(tonumber(redis.call("get",storeKey))<=0) then
    -- 库存不足
    return 1
end
-- 判断用户是否下过单
if((redis.call("sismember",orderKey,userId))==1) then
    return 2
end
redis.call("incrby",storeKey,-1)
redis.call("sadd",orderKey,userId)
-- 将数据写入stream消息队列
--   XADD key [NOMKSTREAM] [MAXLEN|MINID [=|~] threshold [LIMIT count]] *|id field value [field value ...]
--redis.call("XADD","stream.orders","*","userId",userId,"voucherId",voucherId,"id",orderId)
-- 已经使用了mq代替 不需要使用redis-add
return 0

