# FamilyGuard 代码审查报告

> 审查日期: 2026-07-02
> 审查范围: backend (Go), parent-app (Flutter), child-app (Kotlin)
> 代码总量: 约 3500 行

---

## 一、严重问题（可能导致崩溃或数据丢失）

### 1.1 Go: `handlers.go:55` — Login 空指针崩溃

```go
user, err := h.repo.GetUserByEmail(req.Email)
if err != nil || bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)) != nil {
```

`GetUserByEmail` 返回 `(nil, sql.ErrNoRows)` 时，`user` 为 nil，访问 `user.PasswordHash` 直接 panic。

**修复**: 先判断 err，再访问 user

### 1.2 Go: `main.go:80-89` — 日志路由认证逻辑缺陷

家长端使用 `Authorization: Bearer <token>`，孩子端使用 `X-Device-Token: <token>`。如果孩子端误传了 Authorization 头，会产生混淆。

**修复**: 已拆分为独立路由 `parent.POST /logs` 和 `child.POST /logs`

### 1.3 Go: `repository.go:268-277` — 批量插入事务错误未处理

`Begin`、`Prepare`、`Exec` 的 error 全部忽略。

**修复**: 已全部加上 error 检查

### 1.4 Kotlin: `RuleStorage.kt:75` — 数组长度取错导致数组越界

嵌套 lambda 中 `it` 被覆盖，导致取错数组长度。

**修复**: 已显式命名外层变量

---

## 二、主要问题（功能逻辑缺陷）

### 2.1 Flutter: `logger_service.dart` — 日志上传功能未实现

`_flush()` 只做了 `_id++`，没有任何网络请求。

**修复**: 已重写为真实 Dio 上传 + 失败时本地缓存

### 2.2 Go: `handlers.go:33` — 注册时可能误判用户存在

数据库查询因其他错误失败时，错误被忽略。

### 2.3 Go: `main.go:34` — Cron 错误未处理

### 2.4 Flutter: 多处 `catch (_) {}` 静默吞掉错误

### 2.5 Kotlin: `SyncService.kt` — 协程作用域未处理异常

---

## 三、次要问题（代码质量、可维护性）

### 3.1 Go: 多处 error 被静默忽略

### 3.2 Go: GetEvents 查询缺少 owner_id 过滤

**高危漏洞** — 已修复，添加 `WHERE d.owner_id=$1`

### 3.3 Go: `Repository` 结构体设计问题

### 3.4 Flutter: `pubspec.yaml` 依赖版本冲突风险

### 3.5 Flutter: 未使用的依赖

### 3.6 Kotlin: `proguard-rules.pro` 缺失

### 3.7 Kotlin: `NetworkUtils.kt` — `MediaType.parse` 已废弃

### 3.8 Kotlin: `RuleStorage` companion object 冗余

---

## 四、安全审查

| 问题 | 严重度 | 状态 |
|------|--------|------|
| 登录空指针 | 严重 | ✅ 已修复 |
| events 未按 owner 过滤 | 高 | ✅ 已修复 |
| JWT Secret 硬编码默认值 | 中 | 需用户部署时修改 |
| 密码未做长度限制 | 中 | 后端已校验 min=6 |
| CORS 完全开放 | 低 | 生产环境应限制域名 |
| SQL 注入 | 低 | 已使用参数化查询 |
