-- ============================================================
-- 库存预扣（原子）：下单接口的核心脚本
-- KEYS[1] = 库存 key，如 stock:activity:1001
-- ARGV[1] = 本次购买数量
-- 返回: 1=预扣成功  0=库存不足  -1=key不存在(未预热)
-- ============================================================
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end

local stock = tonumber(redis.call('GET', KEYS[1]))
local need  = tonumber(ARGV[1])

if stock >= need then
    redis.call('DECRBY', KEYS[1], need)
    return 1
end
return 0
