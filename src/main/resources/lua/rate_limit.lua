-- ============================================================
-- 令牌桶限流：超限制返回 0，调用方抛 429
-- KEYS[1] = 限流 key，如 rl:seckill:userId:12
-- ARGV[1] = 桶容量 capacity（最多允许瞬时多少个请求）
-- ARGV[2] = 每秒补充速率 rate
-- ARGV[3] = 本次请求消耗令牌数（一般 1）
-- ARGV[4] = 当前毫秒时间戳（由 Java 传入，避免多机时钟不一致导致 Lua 里不能用 TIME 的问题）
-- 返回: 1=放行  0=拒绝
-- ============================================================
local key        = KEYS[1]
local capacity   = tonumber(ARGV[1])
local rate       = tonumber(ARGV[2])
local requested  = tonumber(ARGV[3])
local now        = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(bucket[1])
local last   = tonumber(bucket[2])

-- 首次访问：桶是满的
if tokens == nil or last == nil then
    tokens = capacity
    last   = now
end

-- 按时间补充令牌（delta 秒 * rate）
local delta = math.max(0, now - last)
tokens = math.min(capacity, tokens + delta / 1000.0 * rate)

local allowed = 0
if tokens >= requested then
    tokens  = tokens - requested
    allowed = 1
end

-- 写回并给一个兜底过期时间，避免冷 key 常驻
local ttl = math.ceil(capacity / rate) + 1
redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', key, ttl)

return allowed
