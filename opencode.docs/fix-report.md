# FamilyGuard 代码修复报告（v2）

> 更新日期: 2026-07-04
> 依据: claude.docs/code-review-report.md（第四轮审查）

---

## 修复状态

| 优先 | 编号 | 问题 | 状态 | 修改文件 |
|------|------|------|------|----------|
| P0 | #26 | ChildRegister 不验证配对码 | ✅ 已修复 | `handlers.go:264-279` |
| P0 | #1 | BindDevice 不验证配对码 | ✅ 已修复 | `repository.go:103-111` |
| P0 | #2 | JWT Secret 默认值 | ✅ 已修复 | `main.go:25-27` |
| P0 | #4 | CORS 过宽 | ✅ 已修复 | `main.go:29,44,143` |
| P0 | #5 | GetDeviceApps 无校验 | ✅ 已修复 | `handlers.go:335-337` |
| P1 | #6~#18 | 见下方明细 | ✅ 已修复 | 多处 |
| P2 | #20 | Provider 静默吞错 | ✅ 已修复 | `providers.dart:38-103` |
| P2 | #25 | proguard-rules.pro 缺失 | ✅ 已修复 | `child-app/proguard-rules.pro` |
| P0 | #3 | 速率限制 | ⏳ 暂缓 | 需额外依赖 |
| P2 | #19 | N+1 查询 | ⏳ 暂缓 | 后续优化 |
| P2 | #22 | Context 泄漏 | ⏳ 暂缓 | applicationContext 风险低 |
| P2 | #23 | token 刷新 | ⏳ 暂缓 | Phase 2 |
| P2 | #24 | UI 空按钮 | ⏳ 暂缓 | 待 API 稳定 |

### 本轮新增修复

| 编号 | 问题 | 代码 |
|------|------|------|
| #26 | `ChildRegister` 不验证 `pairing_code` | `handlers.go:264-279` — 设备已存在时检查 `device.PairingCode != req.PairingCode` |
| #20 | Provider 静默吞错 | `providers.dart` — DeviceProvider/RuleProvider/EventProvider 均增加 `_error` 字段 |

### P1 批量修复明细

| 编号 | 问题 | 文件 |
|------|------|------|
| #6 | 星期映射 | `RuleMatcher.kt:32-41` |
| #7 | Logger UTC | `Logger.kt:19-20` |
| #8 | Flutter Logger init | `logger_service.dart:14`, `main.dart:13` |
| #9 | AppCollector | `AppCollector.kt:18-21` |
| #10 | BootReceiver 崩溃 | `BootReceiver.kt:13-19` |
| #11 | DeviceAdmin XML | `res/xml/device_admin.xml` |
| #12 | SyncService 协程 | `SyncService.kt:16-19,30-34` |
| #13 | Register DB 错误 | `handlers.go:34-42` |
| #14 | 密码加密 error | `handlers.go:43-46` |
| #15 | Token 生成 error | `handlers.go:53-57` |
| #16 | ExportLogs 状态码 | `handlers.go:423` |
| #17 | Cron 错误 | `main.go:38` |
| #18 | 登录双重导航 | `login_screen.dart:26-27` |
| #21 | OkHttp 废弃 API | `NetworkUtils.kt:20` |

---

## 统计

| 分类 | 数量 |
|------|------|
| 已修复 | 20 |
| 暂缓 | 5 |
| **总计** | **26** |
