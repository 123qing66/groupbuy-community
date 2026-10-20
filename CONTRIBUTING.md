# 协作约定（第 2 天由组长 A 提交，全组遵守）

## 1. 分支

- `main`：受保护，只接受来自 `develop` 的 PR，对应里程碑评审通过的版本。
- `develop`：日常集成分支，各功能分支合入这里。
- `feature/模块-简述`、`fix/模块-简述`、`docs/xxx`、`chore/xxx`：个人工作分支，一个 Issue 一支。
- 分支从 `develop` 切，PR 合回 `develop`；**禁止直接 push main/develop**。

## 2. Commit message

格式：`<type>(<scope>): <简述>`

```
feat(order): 增加下单库存 CAS 兜底校验
fix(pickup): 重复核销返回明确业务码
perf(activity): 活动详情缓存增加 TTL 抖动
test(order): 补充成团判定并发用例
docs(api): 冻结 v1 接口契约
```

- type：`feat` `fix` `perf` `refactor` `test` `docs` `chore`
- scope：`activity` `order` `pickup` `auth` `stats` `common` `db` `api`
- 每人**每周不少于 3 次有意义提交**；禁止 `update` / `修改` / `111` 这类无法追溯的信息。

## 3. 代码规范

- 分层：`controller → service → mapper`，Controller 不写业务逻辑，Service 不拼 HTTP 响应。
- 统一响应体 `R<T>`（code/message/data），业务异常与系统异常分开，禁止把堆栈抛给前端。
- 入参一律 `jakarta.validation` 校验，校验失败返回字段级错误信息。
- 日志用 SLF4J，关键节点（登录、下单、支付回调、成团判定、核销、异常）必须有 INFO/WARN；**禁止 `System.out.println`**。
- 命名：类名大驼峰、方法变量小驼峰、常量全大写下划线、表名字段下划线；禁止 `a`、`b`、`test1`。
- 单方法 ≤80 行，嵌套 ≤3 层，魔法值抽成常量或枚举（状态码一律用枚举 + 转移表）。
- 数据库脚本只放 `src/main/resources/db/`，改动需组长 A 评审。

## 4. 跨模块调用

- 只能调用对方 Service 接口，**禁止直接写别人的 Mapper**。
- 需要改别人的表字段或 DTO：开 Issue 指派给 owner，由 owner 修改。
- 共享文件（`R.java`、`ErrorCode`、`docs/api.md`、`schema.sql`、Redis key 规范）由组长 A 维护。

## 5. PR

- 一个 Issue 一个分支一个 PR，正文写 `Closes #编号`。
- 至少 1 个 Approve + CI 绿才能合并；reviewer 由 `CODEOWNERS` 自动指派。
- PR 描述里写明"希望评审重点看哪里"，尤其是并发、事务边界、幂等相关的改动。

## 6. 每日节奏

- 开工前 5 分钟：把看板上昨天完成的卡片拖到 Done，认领今天要做的 Issue。
- 第 6 / 12 / 16 天三次组内评审，截图存档到 `docs/evidence/`。
- 每周日组长跑 `git shortlog -sne --since="7 days ago"` 公示人均提交数。

## 7. AI 使用

允许用 AI 辅助编码与文档润色，但必须：
- 能解释每一行保留代码的意图；
- 在 Issue 或 PR 备注中写明哪些文件/方法借助 AI 生成；
- 有测试验证、符合本文件的分层与命名规范。
第 6 章《AI 使用说明》由成员 D 汇总。
