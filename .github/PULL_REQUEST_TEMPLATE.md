## 关联 Issue

Closes #

## 改动内容

<!-- 一句话说清改了什么，不要贴大段代码 -->

## 涉及模块与技术点

- 模块：activity / order / pickup / auth / stats / common
- 技术点：并发（CAS/Redis 预扣） / 幂等 / 缓存 / 延迟任务 / 统计 / 测试

## 自测情况

- [ ] 本地 `mvn clean package` 通过
- [ ] 新增或更新了单元测试，且全部通过
- [ ] 关键业务节点有 INFO/WARN 日志，无 `System.out.println`
- [ ] 未修改他人负责目录（如确需修改，已在下方说明并经 owner 同意）

## 需要评审重点看的地方

<!-- 例：请重点看 OrderServiceImpl.placeOrder() 的事务边界与 Redis 预扣回滚是否成对 -->

## 截图 / 验证证据（可选）

<!-- 接口响应、日志、压测结果等，交付时归入 docs/evidence/ -->
