# KidSafe 项目审查报告

> 审查日期：2026-07-02
> 审查范围：`architecture.md`、`deplyment.md`、项目配置文件

---

## 一、软件名称建议

| 项目 | 建议名称 | 说明 |
|------|----------|------|
| 项目总名 | **KidSafe** | 体现"儿童安全"定位 |
| 家长端 App | **kidsafe-parent** | Flutter 项目目录名，与主项目关联清晰 |
| 孩子端 App | **kidsafe** | Kotlin 原生项目目录名 |
| Go 后端 | **backend** | 无需特殊命名 |
| 项目根目录 | `kidsafe/` | 替代原 `family-guard-hybrid/` |

**命名优势**：简洁、语义明确、便于 GitHub 仓库命名（`kidsafe` 或 `kidsafe-parent`）。

---

## 二、已完成的重命名

以下文件中的 `FamilyGuard` / `family-guard-hybrid` / `com/familyguard` 已全部更新为 KidSafe：

- `architecture.md` — 标题、目录名、项目结构
- `deplyment.md` — 域名、文件路径、部署命令
- `docker-compose.yml` — 数据库名
- `.env.example` — 注释和 DB_NAME
- `backend/configs/config.example.yaml` — 注释和域名

---

## 三、架构关键问题

### 3.1 规则模型复杂度

当前设计有四张关联表：`rules → rule_apps → rule_schedules → rule_devices`。

**建议**：如果一个规则只针对一台设备，`rules` 表可直接包含 `device_id`，去掉 `rule_devices`。除非有明确的多设备分组需求。

### 3.2 规则同步实时性

文档提到"定时轮询"但未定义间隔。家长修改规则后，孩子端多久生效？

**建议**：
- 基础轮询（15-30分钟）+ FCM 推送触发（秒级生效）
- 无网络时降级为本地缓存的最近一次规则

### 3.3 App识别方式

`rule_apps` 只存 `package_name`，但家长端UI需要显示App图标和名称。

**需明确**：
- App名称/图标来源：孩子端上报已安装列表 → 上传 Base64 图标
- 卸载再重装后规则自动生效（`package_name` 不变）

---

## 四、遗漏功能清单

### 4.1 高优先级（MVP必须）

| 遗漏项 | 说明 | 建议 |
|--------|------|------|
| **应用图标采集** | 家长端需要看到孩子手机App的图标才能识别 | 孩子端采集时将图标转为 Base64 一并上传 |
| **rules.updated_at 字段** | 孩子端增量同步需要时间戳对比 | rules 表增加 `updated_at TIMESTAMP` |
| **密码重置功能** | 文档只有登录注册，无找回密码流程 | 增加邮箱/手机验证码重置密码 API |
| **登出/Token吊销** | 家长退出登录后 Token 仍有效 | 后端增加 token 黑名单或短有效期 + refresh_token |

### 4.2 中优先级（Phase 2）

| 遗漏项 | 说明 |
|--------|------|
| **设备绑定数量限制** | 一个家长账号最多绑定几台设备？需定义 |
| **多家长管理同一设备** | 数据模型需提前考虑，否则后期改造成本高 |
| **本地规则缓存加密** | 孩子端 Room 数据库若设备 root 可被读取，建议用 EncryptedRoom |
| **pairing_code 完整生命周期** | 生成算法、单次使用、有效期、防暴力尝试 |

---

## 五、安全建议

| 问题 | 建议 |
|------|------|
| JWT Secret 管理 | 明确存储位置（环境变量）、轮换机制、孩子端/家长端是否共用 |
| 事件查询效率 | `events` 表增加冗余 `owner_id`，避免多表 JOIN |
| 防绕过-ADB禁用 | 监听 `PACKAGE_CHANGED` 事件，检测异常卸载/禁用时上报 |
| 防绕过-无障碍关闭 | 检测频率建议 30 秒，关闭后立即通知家长端 |
| 本地规则缓存 | 建议使用 EncryptedRoom 或 Android Keystore 加密 |

---

## 六、文档不一致问题

| 问题 | 位置 | 说明 |
|------|------|------|
| 反向代理配置 | deplyment.md vs architecture.md | 部署文档用 Caddy（自动HTTPS），架构文档提到 Nginx，建议统一 |
| 日志上传间隔冲突 | architecture.md | 同时写了"每 5 分钟"和"每 15 分钟"上报一次，需统一 |
| 最低SDK版本 | architecture.md | 建议标注最低兼容版本而非精确版本，避免文档过时 |

---

## 七、MVP精简建议

### 可延后的功能

| 功能 | 建议 |
|------|------|
| Web 后台 | 删除或标注为远期规划 |
| `/events/stats` 统计 API | 移至 Phase 2 |
| 数据清理策略（90天归档） | 移至 Phase 2 |
| API 版本策略 | 简化为"当前 v1，有 breaking change 时再升级" |
| fl_chart 图表 | 移至 Phase 2 |

### MVP 核心功能清单

1. 认证：注册 / 登录 / Token 刷新 / 密码重置
2. 设备绑定：配对码绑定 / 解绑
3. 规则管理：创建规则（App 列表 + 时间段）/ 启用禁用 / 删除
4. 规则同步：孩子端拉取并缓存规则
5. 实时拦截：AccessibilityService + 全屏拦截页面
6. 事件上报：拦截记录上报 + 家长端查看
7. 应用列表采集：孩子端上报已安装 App（含图标）

---

## 八、其他建议

1. **心跳间隔**：定义设备心跳频率（建议5分钟），超时判定离线的阈值（建议15分钟）
2. **健康检查端点**：补充后端 `/health` 的响应内容和 uptime 监控方案
3. **备份策略**：明确 PostgreSQL 备份频率和恢复流程
4. **技术版本号**：标注最低兼容版本，避免文档过时
