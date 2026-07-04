# KidSafe 混合方案架构设计（v2.0）

## 一、关键决策记录

| 决策项 | 选择 |
|--------|------|
| 孩子端拦截方案 | AccessibilityService |
| 家长端框架 | Flutter |
| 后端语言 | Go 单体服务 |
| 数据库 | PostgreSQL |
| API协议 | RESTful JSON |
| Web后台 | 暂不需要，后期按需添加 |
| 部署方式 | Docker Compose → 自建VPS |
| VPS配置 | 1核2G（低配优化） |
| 目标设备 | 全品牌兼容 |

---

## 二、整体架构

```
                   +-------------------------------------+
                   |           Go 后端服务                |
                   |  Gin + PostgreSQL + JWT               |
                   |  1核2G VPS优化部署                    |
                   +-------------------------------------+
                     ↑                    ↑
                     | REST JSON          | REST JSON
                     ↓                    ↓
           +-------------------+  +-------------------+
           | Flutter 家长端App  |  | Kotlin 孩子端App  |
           | Provider状态管理    |  | Jetpack Compose   |
           | 仅管理端（无系统API）|  | AccessibilityService|
           +-------------------+  +-------------------+
                                       ↓
                             +-------------------+
                             | 实时拦截引擎       |
                             | RuleMatcher        |
                             | 本地Room缓存规则    |
                             +-------------------+
```

### 三个子项目

| 子项目 | 技术栈 | 用途 |
|--------|--------|------|
| `kidsafe-parent/` | Flutter + Dart + Provider | 家长管理界面 |
| `kidsafe/` | Kotlin + Jetpack Compose | 孩子端拦截核心 |
| `backend/` | Go + Gin + PostgreSQL | 后端API服务 |

### 数据流向

```
孩子端采集已安装App列表 → POST /api/v1/child/apps → 后端存储
     ↓
家长端获取设备App列表 → 展示App清单供勾选配置
     ↓
家长配置规则（勾选App + 设定时间段） → 后端API → PostgreSQL存储
     ↓
孩子设备定时拉取（轮询间隔15分钟）+ FCM推送即时触发
     ↓
AccessibilityService监听前台App
     ↓
命中规则 → 全屏拦截 → 上报事件
```

### MVP核心功能清单

```
1. 认证：注册 / 登录 / Token刷新
2. 设备绑定：配对码绑定 / 解绑
3. App列表采集：孩子端上报已安装App → 家长端展示可配置列表
4. 规则管理：家长从App列表中勾选 → 设置时间段 → 创建规则
5. 规则同步：孩子端拉取并缓存规则（轮询 + FCM推送）
6. 实时拦截：AccessibilityService + 全屏拦截页面
7. 事件上报：拦截记录上报 + 家长端查看
```

---

## 三、技术选型详解

### 3.1 孩子端 App - Kotlin + Jetpack Compose

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9+ | 开发语言 |
| Jetpack Compose | 1.5+ | UI框架 |
| AccessibilityService | API 26+ | 前台App监听 |
| Device Admin API | API 26+ | 防卸载 |
| Room | 2.6+ | 本地缓存规则 |
| Retrofit | 2.9+ | 网络请求 |
| WorkManager | 2.9+ | 后台定时同步 |

**为什么选择原生Kotlin而不是Flutter？**
1. AccessibilityService 是Android系统级API，Flutter无法直接调用
2. 设备管理员(DeviceAdmin)需要原生Receiver
3. 拦截页面需要全屏覆盖、禁用返回键等系统级UI操作
4. 后台保活需要原生Service和前台通知

### 3.2 家长端 App - Flutter + Dart

| 技术 | 版本 | 用途 |
|------|------|------|
| Flutter | 3.16+ | 跨平台框架 |
| Provider | 6.0+ | 状态管理 |
| Dio | 5.4+ | 网络请求 |
| fl_chart | 0.65+ | 图表展示 |
| mobile_scanner | 3.5+ | 二维码扫码绑定 |
| shared_preferences | 2.2+ | 本地Token存储 |

**为什么选择Flutter？**
1. 家长端不需要系统级API，纯UI操作
2. 一套代码跨平台（Android + iOS）
3. 开发效率高，热重载
4. 丰富的UI组件库

### 3.3 Web后台（暂不开发）

Web后台将在后续按需添加，技术储备：Vue 3 + Element Plus。

### 3.4 后端 - Go + Gin + PostgreSQL

| 技术 | 版本 | 用途 |
|------|------|------|
| Go | 1.21+ | 后端语言 |
| Gin | 1.9+ | HTTP框架 |
| PostgreSQL | 15+ | 关系数据库 |
| JWT (golang-jwt) | 5.2+ | Token认证 |
| bcrypt | - | 密码加密 |
| Docker Compose | - | 部署 |

**为什么选择Go？**
1. 高并发性能好，适合多设备同时请求
2. 编译单文件，部署简单
3. 内存占用低，适合1核2G低配VPS
4. Gin框架轻量高效

**1核2G VPS优化策略**
1. Go编译单文件，无运行时依赖，内存占用约30-50MB
2. PostgreSQL容器限制内存使用：`shared_buffers=256MB`
3. Docker Compose编排，资源限额
4. 日志轮转控制磁盘
5. 静态文件由Go内置服务，不额外部署Nginx

---

## 四、页面布局

### 4.1 家长端 App（Flutter）页面规划

```
┌─────────────────────────────────────────────────┐
│  4.1.1 登录/注册页                               │
│  ┌──────────────────────────────────┐            │
│  │            Logo + 应用名          │            │
│  │                                  │            │
│  │   [登录/注册 Tab 切换]            │            │
│  │   ┌──────────────────────────┐  │            │
│  │   │ 邮箱输入框                │  │            │
│  │   │ 密码输入框                │  │            │
│  │   │ [登录/注册按钮]           │  │            │
│  │   └──────────────────────────┘  │            │
│  └──────────────────────────────────┘            │
│  初次使用：先注册 → 自动登录                      │
│  已有账号：直接登录 → 进入首页                     │
├─────────────────────────────────────────────────┤
│  4.1.2 首页（仪表盘）                             │
│  ┌──────────────────────────────────┐            │
│  │ 顶栏: KidSafe    [⚙️设置]    │            │
│  ├──────────────────────────────────┤            │
│  │ 统计卡片:                         │            │
│  │ ┌──────┐ ┌──────┐ ┌──────┐     │            │
│  │ │今日   │ │设备   │ │拦截   │     │            │
│  │ │使用时长│ │在线数 │ │次数   │     │            │
│  │ └──────┘ └──────┘ └──────┘     │            │
│  ├──────────────────────────────────┤            │
│  │ 功能入口 (2列网格):                │            │
│  │ ┌──────┐ ┌──────┐              │            │
│  │ │📱设备 │ │📋规则 │              │            │
│  │ │管理   │ │管理   │              │            │
│  │ ├──────┤ ├──────┤              │            │
│  │ │📊拦截 │ │👤设置 │              │            │
│  │ │记录   │ │      │              │            │
│  │ └──────┘ └──────┘              │            │
│  ├──────────────────────────────────┤            │
│  │ 最近活动列表（最新5条拦截记录）       │            │
│  └──────────────────────────────────┘            │
│  数据来源: GET /api/v1/events/stats               │
│             GET /api/v1/devices                   │
├─────────────────────────────────────────────────┤
│  4.1.3 设备管理页                                  │
│  ┌──────────────────────────────────┐            │
│  │ 顶栏: 设备管理    [➕扫码绑定]     │            │
│  ├──────────────────────────────────┤            │
│  │ 设备卡片列表:                      │            │
│  │ ┌──────────────────────────┐   │            │
│  │ │ 📱 小明的手机              │   │            │
│  │ │    型号: 小米13           │   │            │
│  │ │    状态: 🟢在线            │   │            │
│  │ │    最后在线: 2分钟前        │   │            │
│  │ │    [详情] [解绑]           │   │            │
│  │ └──────────────────────────┘   │            │
│  │ ┌──────────────────────────┐   │            │
│  │ │ 📱 小红的平板              │   │            │
│  │ │    状态: 🔴离线            │   │            │
│  │ │    ...                    │   │            │
│  │ └──────────────────────────┘   │            │
│  └──────────────────────────────────┘            │
│  空状态: 显示"暂无设备" → 引导绑定                   │
│  数据: GET /api/v1/devices                        │
├─────────────────────────────────────────────────┤
│  4.1.4 规则管理页                                  │
│  ┌──────────────────────────────────┐            │
│  │ 顶栏: 规则管理    [➕创建规则]     │            │
│  ├──────────────────────────────────┤            │
│  │ 规则卡片列表:                      │            │
│  │ ┌──────────────────────────┐   │            │
│  │ │ 📋 学习时间              │🟢  │            │
│  │ │    微信·QQ·抖音           │开关│            │
│  │ │    周一~周五 08:00-17:00  │    │            │
│  │ │    [编辑] [删除]          │    │            │
│  │ └──────────────────────────┘   │            │
│  │ ┌──────────────────────────┐   │            │
│  │ │ 📋 睡觉时间              │🔴  │            │
│  │ │    所有App               │开关│            │
│  │ │    每天 22:00-06:00      │    │            │
│  │ │    [编辑] [删除]          │    │            │
│  │ └──────────────────────────┘   │            │
│  └──────────────────────────────────┘            │
│  空状态: 显示"暂无规则" → 引导创建                   │
│  数据: GET /api/v1/rules                         │
├─────────────────────────────────────────────────┤
│  4.1.5 创建/编辑规则页                             │
│  ┌──────────────────────────────────┐            │
│  │ 顶栏: ← 创建规则                  │            │
│  ├──────────────────────────────────┤            │
│  │ 规则名称: [________________]     │            │
│  ├──────────────────────────────────┤            │
│  │ 选择设备:                         │            │
│  │ ☑ 小明的手机                      │            │
│  │ ☐ 小红的平板                      │            │
│  ├──────────────────────────────────┤            │
│  │ 搜索App: [______________]       │            │
│  │ 已安装App列表（分页加载）:          │            │
│  │ ☑ 微信                            │            │
│  │ ☑ QQ                             │            │
│  │ ☐ 抖音                            │            │
│  │ ☐ 王者荣耀                        │            │
│  │ ...                              │            │
│  ├──────────────────────────────────┤            │
│  │ 时间段设定:                        │            │
│  │ ┌──────────────────────────┐   │            │
│  │ │ 周: 一二三四五六日  (勾选)   │   │            │
│  │ │ 起: [08:00] 止: [17:00] │   │            │
│  │ │                 [+添加时段]│   │            │
│  │ └──────────────────────────┘   │            │
│  ├──────────────────────────────────┤            │
│  │        [保存规则]                 │            │
│  └──────────────────────────────────┘            │
│  App列表来源: GET /api/v1/devices/:id/apps         │
│  提交: POST/PUT /api/v1/rules                    │
├─────────────────────────────────────────────────┤
│  4.1.6 拦截记录页                                  │
│  ┌──────────────────────────────────┐            │
│  │ 顶栏: 拦截记录    [🔍筛选] [🔄刷新]│            │
│  ├──────────────────────────────────┤            │
│  │ 统计概览: 今日X次, 共Y次           │            │
│  ├──────────────────────────────────┤            │
│  │ 记录列表（时间倒序）:               │            │
│  │ ┌──────────────────────────┐   │            │
│  │ │ 🚫 微信  小明的手机       │   │            │
│  │ │    2026-07-02 14:30      │   │            │
│  │ ├──────────────────────────┤   │            │
│  │ │ 🚫 抖音  小明的手机       │   │            │
│  │ │    2026-07-02 14:25      │   │            │
│  │ └──────────────────────────┘   │            │
│  │ 加载更多...                      │            │
│  └──────────────────────────────────┘            │
│  数据: GET /api/v1/events?page=1&page_size=20     │
├─────────────────────────────────────────────────┤
│  4.1.7 设置页                                     │
│  ┌──────────────────────────────────┐            │
│  │ 顶栏: 设置                       │            │
│  ├──────────────────────────────────┤            │
│  │ 用户信息                          │            │
│  │ ┌ 👤 用户头像  email@xxx.com ┐  │            │
│  ├──────────────────────────────────┤            │
│  │ 通用：                           │            │
│  │ 通知设置  >                       │            │
│  │ 语言      >                       │            │
│  ├──────────────────────────────────┤            │
│  │ 关于：                           │            │
│  │ 关于KidSafe  >                │            │
│  │ 隐私政策    >                     │            │
│  ├──────────────────────────────────┤            │
│  │ [退出登录]                        │            │
│  └──────────────────────────────────┘            │
└─────────────────────────────────────────────────┘
```

### 4.2 孩子端 App（Kotlin）页面规划

```
┌─────────────────────────────────────────────────┐
│  4.2.1 权限引导页（首次启动）                       │
│  ┌──────────────────────────────────┐            │
│  │  步骤1/3: 无障碍服务              │            │
│  │  ┌──────────────────────────┐  │            │
│  │  │  图标                     │  │            │
│  │  │  "允许KidSafe监控     │  │            │
│  │  │   应用使用情况"            │  │            │
│  │  └──────────────────────────┘  │            │
│  │  [去设置开启] → 跳转系统设置页    │            │
│  │  [已开启,下一步]                │            │
│  ├──────────────────────────────────┤            │
│  │  步骤2/3: 设备管理员             │            │
│  │  📋 "激活后可防止应用被卸载"      │            │
│  │  [去激活] → 跳转系统激活页        │            │
│  ├──────────────────────────────────┤            │
│  │  步骤3/3: 绑定家长               │            │
│  │  配对码: [______] 6位数字        │            │
│  │  设备名: [小明的手机]            │            │
│  │  [绑定]                         │            │
│  └──────────────────────────────────┘            │
├─────────────────────────────────────────────────┤
│  4.2.2 主页（运行状态）                             │
│  ┌──────────────────────────────────┐            │
│  │  守护中                           │            │
│  │  🟢 无障碍服务: 运行中             │            │
│  │  🟢 设备管理员: 已激活             │            │
│  │  🔴 网络: 离线（最后同步: 10分钟前）│            │
│  ├──────────────────────────────────┤            │
│  │  当前生效规则: 2条                │            │
│  │  受限App: 6个                    │            │
│  │  今日拦截: 3次                    │            │
│  └──────────────────────────────────┘            │
│  注意: 孩子端以Service后台运行为主                   │
│  此页面主要供家长设置时查看状态                      │
├─────────────────────────────────────────────────┤
│  4.2.3 拦截页面（全屏覆盖）                         │
│  ┌──────────────────────────────────┐            │
│  │                                  │            │
│  │           🚫                      │            │
│  │     应用已限制                     │            │
│  │      「微信」                      │            │
│  │                                  │            │
│  │   当前处于限制使用时段               │            │
│  │   请在允许的时间内使用               │            │
│  │                                  │            │
│  │    临时规则: [申请更多时间]         │            │
│  │                                  │            │
│  │      [ 返回桌面 ]                 │            │
│  │                                  │            │
│  │      KidSafe 家长控制          │            │
│  └──────────────────────────────────┘            │
│  特点:                                          │
│  - 禁用返回键（点击返回=回桌面）                    │
│  - 禁用任务切换（切到后台=回桌面）                  │
│  - 全屏沉浸模式，无系统导航栏                      │
│  - 每30秒检测无障碍服务是否存活                     │
└─────────────────────────────────────────────────┘
```

### 4.3 导航流程

```
家长端 App 导航:
┌──────────┐
│ 登录/注册 │
└────┬─────┘
     ↓ 登录成功
┌──────────┐
│  首页    │ ← 底部Tab导航
├──────────┤
│ 设备管理 │
├──────────┤
│ 规则管理 │
├──────────┤
│ 拦截记录 │
└──────────┘

孩子端 App 导航:
┌──────────┐
│ 权限引导 │ → 按步骤依次开启权限
└────┬─────┘
     ↓ 所有权限就绪
┌──────────┐
│ 主页状态 │ → 显示运行状态
├──────────┤
│ 拦截页面 │ → 触发规则时自动全屏弹出
└──────────┘
```

---

## 五、API设计

### 5.1 认证API

```
POST /api/v1/register       - 家长注册
  Request:  { "email": "string", "password": "string", "nickname": "string" }
  Response: { "token": "string", "user": {...} }

POST /api/v1/login          - 家长登录
  Request:  { "email": "string", "password": "string" }
  Response: { "token": "string", "expires_at": "int64", "user": {...} }

POST /api/v1/auth/refresh   - 刷新Token
  Header:   Authorization: Bearer <token>
  Response: { "token": "string", "expires_at": "int64" }
```

### 5.2 设备API

```
GET    /api/v1/devices            - 获取设备列表
  Response: [ { "id": "int", "device_id": "string", "device_name": "string",
                "is_online": "bool", "last_seen_at": "datetime" } ]

POST   /api/v1/devices/bind       - 绑定设备（配对码）
  Request:  { "device_id": "string", "pairing_code": "string", "device_name": "string" }
  Response: { "id": "int", "device_id": "string", ... }

DELETE /api/v1/devices/:id        - 解绑设备
  Response: { "success": true }
```

### 5.3 规则API

```
GET    /api/v1/rules              - 获取规则列表
  Response: [ { "id": "int", "name": "string", "is_active": "bool",
                "apps": [...], "schedules": [...], "device_ids": [...] } ]

POST   /api/v1/rules              - 创建规则
  Request:  { "name": "string", "app_ids": ["string"], "schedules": [...], "device_ids": ["int"] }
  Response: { "id": "int", "name": "string", ... }

PUT    /api/v1/rules/:id          - 更新规则
  Request:  { "name": "string", "is_active": "bool", ... }

DELETE /api/v1/rules/:id          - 删除规则

POST   /api/v1/rules/:id/toggle  - 启用/禁用规则
  Request:  { "is_active": true/false }
```

### 5.4 孩子端API（设备Token认证）

```
POST   /api/v1/child/register    - 设备注册（返回设备Token）
  Request:  { "device_id": "string", "device_name": "string", "pairing_code": "string" }
  Response: { "token": "string", "device_id": "string" }

POST   /api/v1/child/apps        - 上报已安装App列表（全量覆盖）
  Header:   X-Device-Token: <token>
  Request:  { "apps": [{ "package_name": "string", "app_name": "string" }] }
  Response: { "synced_at": "datetime" }
  触发时机: 首次注册时 + App安装/卸载监听 + 心跳时增量检测

GET    /api/v1/child/config      - 拉取规则配置（含规则+调度）
  Header:   X-Device-Token: <token>
  Response: { "device_id": "string", "rules": [{ "id": "int", "apps": ["string"], "schedules": [...] }] }

POST   /api/v1/child/events      - 批量上报拦截记录
  Header:   X-Device-Token: <token>
  Request:  { "events": [{ "package_name": "string", "app_name": "string", "blocked_at": "datetime" }] }

POST   /api/v1/child/heartbeat   - 设备心跳（更新在线状态）
  Header:   X-Device-Token: <token>
  频率:     每5分钟一次
  超时:     15分钟未收到心跳判定离线
```

### 5.5 设备App列表API（家长端）

```
GET    /api/v1/devices/:id/apps  - 获取指定设备的已安装App列表
  Header:   Authorization: Bearer <JWT>
  Query:    ?page=1&page_size=50&search=string
  Response: { "data": [{ "package_name": "string", "app_name": "string" }],
              "total": "int", "page": "int", "total_pages": "int" }
  说明:     家长端创建规则前先调用此接口，展示孩子手机中的App列表供勾选
```

### 5.6 事件API

```
GET    /api/v1/events            - 获取拦截记录（分页+筛选）
  Query:    ?page=1&page_size=20&device_id=int&start_date=string&end_date=string
  Response: { "data": [...], "total": "int", "page": "int", "total_pages": "int" }

GET    /api/v1/events/stats      - 获取统计数据
  Response: { "total_blocked": "int", "today_blocked": "int",
              "top_apps": [...], "daily_stats": [...] }
```

### 5.7 统一响应格式

```json
// 成功
{ "success": true, "data": {...} }

// 失败
{ "success": false, "message": "错误描述" }
```

### 5.8 认证方式

| 客户端 | 认证方式 | 有效期 |
|--------|----------|--------|
| 家长端App | Header: `Authorization: Bearer <JWT>` | 24小时 |
| 孩子端App | Header: `X-Device-Token: <JWT>` | 30天 |

### 5.9 API版本策略

```
当前版本: v1
URL前缀: /api/v1/
版本升级时: 新增 /api/v2/，旧版本保留6个月后废弃
Breaking change: 新增字段不破坏兼容性，删除字段需走版本升级
```

### 5.10 日志API

```http
POST   /api/v1/logs              - 批量上传客户端日志
  Header:   Authorization: Bearer <JWT>   (家长端)
            X-Device-Token: <token>        (孩子端)
  Request:  {
    "source": "parent-app | child-app",
    "logs": [
      {
        "level": "debug | info | warn | error",
        "tag": "string",           // 模块标签
        "message": "string",
        "stacktrace": "string?",   // 错误时附带
        "timestamp": "datetime"
      }
    ]
  }
  Response: { "success": true, "accepted": 5 }

GET    /api/v1/logs              - 查询日志（带筛选）
  Header:   Authorization: Bearer <JWT>
  Query:    ?source=parent-app&level=error&start=datetime&end=datetime
            &page=1&page_size=50
  Response: { "data": [...], "total": "int", "page": "int" }

GET    /api/v1/logs/export       - 导出日志供AI分析（JSON Lines格式）
  Header:   Authorization: Bearer <JWT>
  Query:    ?source=child-app&start=datetime&end=datetime&level=error
  Response: application/x-ndjson 流式文件

---

## 六、数据库设计

### 6.1 表结构

```sql
-- 用户表
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 设备表
CREATE TABLE devices (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) UNIQUE NOT NULL,
    device_name VARCHAR(100),
    model VARCHAR(100),
    owner_id INTEGER REFERENCES users(id),
    pairing_code VARCHAR(10),
    is_online BOOLEAN DEFAULT FALSE,
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 规则表
CREATE TABLE rules (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    owner_id INTEGER REFERENCES users(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 规则关联应用
CREATE TABLE rule_apps (
    id SERIAL PRIMARY KEY,
    rule_id INTEGER REFERENCES rules(id) ON DELETE CASCADE,
    package_name VARCHAR(255) NOT NULL,
    app_name VARCHAR(100)
);

-- 规则时间调度
CREATE TABLE rule_schedules (
    id SERIAL PRIMARY KEY,
    rule_id INTEGER REFERENCES rules(id) ON DELETE CASCADE,
    days_of_week INTEGER[] NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

-- 规则关联设备
CREATE TABLE rule_devices (
    rule_id INTEGER REFERENCES rules(id) ON DELETE CASCADE,
    device_id INTEGER REFERENCES devices(id) ON DELETE CASCADE,
    PRIMARY KEY (rule_id, device_id)
);

-- 拦截事件表
CREATE TABLE events (
    id SERIAL PRIMARY KEY,
    device_id INTEGER REFERENCES devices(id),
    package_name VARCHAR(255) NOT NULL,
    app_name VARCHAR(100),
    blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 设备已安装App列表（孩子端上报）
CREATE TABLE child_apps (
    id SERIAL PRIMARY KEY,
    device_id INTEGER REFERENCES devices(id) ON DELETE CASCADE,
    package_name VARCHAR(255) NOT NULL,
    app_name VARCHAR(100) NOT NULL,
    synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, package_name)
);

-- 客户端日志表（离线上报）
CREATE TABLE app_logs (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(20) NOT NULL,      -- parent-app | child-app
    device_id VARCHAR(255),            -- 孩子端设备的device_id
    owner_id INTEGER REFERENCES users(id),  -- 家长端用户ID
    level VARCHAR(10) NOT NULL,       -- debug | info | warn | error
    tag VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    stacktrace TEXT,
    logged_at TIMESTAMP NOT NULL,      -- 客户端实际时间
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 日志表索引（按时间倒序查询为主）
CREATE INDEX idx_app_logs_source ON app_logs(source);
CREATE INDEX idx_app_logs_level ON app_logs(level);
CREATE INDEX idx_app_logs_logged_at ON app_logs(logged_at DESC);
CREATE INDEX idx_app_logs_source_level ON app_logs(source, level, logged_at DESC);
```

### 6.2 表设计说明

**规则模型选择（4表关联）**：
- 当前保持 `rules → rule_apps → rule_schedules → rule_devices` 四表结构
- 原因：支持一个规则同时应用于多台设备，支持一个规则关联多个App
- MVP阶段：一个家长可能绑定多台设备（多个孩子），此设计可直接支持
- 简化场景：如果只需要单设备规则，可在业务层简化，数据模型保持不变

**child_apps 表（App列表采集）**：
- 孩子端通过 `PackageManager.getInstalledApplications()` 采集已安装App
- 过滤掉系统应用，只保留用户安装的第三方App
- 通过 `POST /api/v1/child/apps` 全量上报，后端以 `UNIQUE(device_id, package_name)` 做 upsert
- 家长端通过 `GET /api/v1/devices/:id/apps` 查询，支持分页和搜索
- 家长创建规则时：选择设备 → 加载该设备App列表 → 勾选App → 设定时间段 → 保存

**App列表同步时机**：
- 孩子端首次注册时全量上报
- 监听 `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REMOVED` 广播，变化时增量上报
- 每次心跳时检测App列表是否有变化，有变化则上报

**App图标**：不在child_apps表中存储图标，家长端使用系统默认图标即可，避免Base64传输开销

**配对码生命周期**：
- 生成：6位随机数字，设备注册时自动分配
- 有效期：24小时，超时自动失效
- 使用次数：单次有效，使用后立即失效
- 防暴力：同一设备5分钟内最多尝试3次，超过后锁定15分钟

---

## 七、孩子端核心技术

### 7.1 实时拦截流程

```
家长在手机配置规则
        ↓
规则存入PostgreSQL
        ↓
规则变更 → 触发FCM推送（即时生效）
        ↓
孩子端收到推送 → 立即拉取新规则
同时：定时轮询兜底（每15分钟一次）
        ↓
规则缓存到本地Room数据库
        ↓
AccessibilityService.onAccessibilityEvent()
        ↓
提取前台App包名
        ↓
RuleMatcher.shouldBlock(packageName)
    ├── 检查规则是否活跃
    ├── 检查App是否在禁用列表
    └── 检查当前时间是否在禁用时段
        ↓
命中 → 启动全屏BlockActivity覆盖
未命中 → 继续监听
```

### 7.2 防绕过策略

```
┌─────────────────────────────────────┐
│         防绕过策略矩阵               │
├─────────────────┬───────────────────┤
│ 绕过方式        │ 防护手段          │
├─────────────────┼───────────────────┤
│ 从桌面卸载App   │ Device Admin禁止   │
│ 强制停止App     │ Device Admin禁止   │
│ 关闭无障碍服务  │ 每30秒检测一次，   │
│                 │ 检测到关闭立即通知  │
│                 │ 家长端             │
│ 关闭网络        │ 本地缓存规则      │
│ 关机重启        │ BOOT_COMPLETED自启 │
│ 通过ADB禁用App  │ 监听PACKAGE_      │
│                 │ CHANGED事件上报    │
│ 安全模式启动    │ 已知限制，无法防护 │
│ 恢复出厂设置    │ 无法阻止          │
└─────────────────┴───────────────────┘
```

### 7.3 需要的权限

```
BIND_ACCESSIBILITY_SERVICE    - 监听前台App
BIND_DEVICE_ADMIN             - 防卸载
RECEIVE_BOOT_COMPLETED        - 开机自启
FOREGROUND_SERVICE            - 后台保活
POST_NOTIFICATIONS            - 持久通知
INTERNET                      - 网络通信
QUERY_ALL_PACKAGES            - 获取已安装App列表
PACKAGE_ADDED/REMOVED         - 监听App安装/卸载（动态注册）
```

---

## 八、数据库索引与性能

### 8.1 索引策略

```sql
-- 用户表
CREATE INDEX idx_users_email ON users(email);

-- 设备表
CREATE INDEX idx_devices_owner_id ON devices(owner_id);
CREATE INDEX idx_devices_device_id ON devices(device_id);

-- 规则相关
CREATE INDEX idx_rules_owner_id ON rules(owner_id);
CREATE INDEX idx_rule_apps_rule_id ON rule_apps(rule_id);
CREATE INDEX idx_rule_schedules_rule_id ON rule_schedules(rule_id);

-- 事件表（数据量最大，重点优化）
CREATE INDEX idx_events_device_id ON events(device_id);
CREATE INDEX idx_events_blocked_at ON events(blocked_at DESC);
CREATE INDEX idx_events_device_blocked ON events(device_id, blocked_at DESC);

-- 设备App列表
CREATE INDEX idx_child_apps_device_id ON child_apps(device_id);
CREATE INDEX idx_child_apps_package ON child_apps(package_name);
```

### 8.2 数据清理策略

```
events表数据保留90天，超过的自动归档或删除
清理时机：每天凌晨3点由后端定时任务执行
触发方式：使用 robfig/cron 库调度定时任务
```

### 8.3 HTTPS配置

```
VPS上使用 Let's Encrypt 免费证书
Go内置HTTP Server + ACME自动续期
或使用 Caddy 反向代理自动处理HTTPS
```

### 8.4 日志策略

```
Go: zerolog 或 zap 结构化日志
日志级别: error / info / debug
日志输出: stdout + 文件轮转
孩子端: Android Logcat，仅error级别写文件
```

---

## 九、安全设计

### 9.1 认证安全

| 措施 | 方案 |
|------|------|
| 密码存储 | bcrypt加盐哈希 |
| Token签名 | HS256 + 随机Secret |
| Token刷新 | refresh token机制 |
| 设备Token | 独立密钥，30天有效 |
| JWT Secret管理 | 环境变量注入，不写入代码/配置文件 |
| Secret轮换 | 每90天轮换一次，旧Secret保留7天兼容 |
| 多端密钥 | 家长端和孩子端共用同一Secret（简化部署） |

### 9.2 API安全

| 措施 | 方案 |
|------|------|
| 限流 | 按IP/用户ID限流 |
| 来源校验 | 检查请求头User-Agent和设备Token |
| 请求校验 | Gin binding + 自定义验证 |
| SQL注入 | 参数化查询（已内置） |

### 9.3 数据传输

```
家长端 → 后端: HTTPS
孩子端 → 后端: HTTPS
后端 → 数据库: 内网连接，密码认证
```

### 9.4 备份策略

```
PostgreSQL: pg_dump 每日凌晨2点自动备份
备份保留: 最近7天每日 + 每月1号归档
恢复流程: 从备份文件恢复到新实例，验证数据完整性
```

---

## 十、项目结构

```
kidsafe/
│
├── backend/                        # Go后端服务
│   ├── cmd/server/main.go         # 入口
│   ├── internal/
│   │   ├── handlers/              # HTTP处理器
│   │   ├── middleware/            # JWT中间件
│   │   ├── models/               # 数据模型
│   │   └── repository/           # 数据库操作
│   ├── migrations/               # SQL迁移脚本
│   ├── configs/                  # 配置文件
│   ├── go.mod
│   └── Dockerfile
│
├── parent-app/                     # Flutter家长端
│   ├── lib/
│   │   ├── main.dart             # 入口
│   │   ├── core/                 # 核心配置
│   │   │   ├── config/           # API配置
│   │   │   └── theme/            # 主题
│   │   ├── data/                 # 数据层
│   │   │   ├── models/           # 数据模型
│   │   │   └── services/         # API服务
│   │   ├── presentation/         # UI层
│   │   │   ├── screens/          # 页面
│   │   │   │   ├── auth/         # 登录注册
│   │   │   │   ├── home/         # 首页仪表盘
│   │   │   │   ├── devices/      # 设备管理
│   │   │   │   ├── rules/        # 规则管理
│   │   │   │   ├── events/       # 拦截记录
│   │   │   │   └── settings/     # 设置
│   │   │   └── widgets/          # 公共组件
│   │   └── providers/            # 状态管理
│   └── pubspec.yaml
│
├── child-app/                      # Kotlin孩子端
│   ├── src/main/
│   │   ├── kotlin/com/kidsafe/
│   │   │   ├── MainActivity.kt
│   │   │   ├── BlockActivity.kt        # 拦截页面
│   │   │   ├── service/
│   │   │   │   ├── BlockService.kt     # 无障碍服务
│   │   │   │   └── SyncService.kt      # 规则同步服务
│   │   │   ├── collector/
│   │   │   │   └── AppCollector.kt      # 已安装App采集
│   │   │   ├── receiver/
│   │   │   │   ├── BootReceiver.kt     # 开机自启
│   │   │   │   └── DeviceAdminReceiver.kt
│   │   │   ├── data/
│   │   │   │   ├── database/           # Room数据库
│   │   │   │   ├── network/            # Retrofit
│   │   │   │   └── repository/         # 数据仓库
│   │   │   ├── domain/
│   │   │   │   ├── model/              # 领域模型
│   │   │   │   └── matcher/            # 规则匹配引擎
│   │   │   └── ui/
│   │   │       ├── screen/             # Compose页面
│   │   │       └── theme/              # 主题
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── architecture.md                  # 本架构文档
├── docker-compose.yml               # Docker部署
└── README.md

---

## 十一、开发阶段计划

### Phase 1：MVP核心功能（4周）

| 周次 | 后端 | 家长端 | 孩子端 |
|------|------|--------|--------|
| 1 | Go项目+数据库+认证API | Flutter项目+登录注册 | - |
| 2 | 设备绑定+App列表API+规则API | 设备管理+App列表展示 | - |
| 3 | 事件上报API | 规则配置（勾选App+时间段） | 项目搭建+权限引导+App采集 |
| 4 | 联调测试 | 联调测试 | AccessibilityService+拦截+规则同步 |

### Phase 2：增强功能（3-4周）

| 周次 | 后端 | 家长端 | 孩子端 |
|------|------|--------|--------|
| 5 | 统计API+FCM推送 | 数据图表(fl_chart) | DeviceAdmin+开机自启 |
| 6 | 性能优化 | 通知推送 | 规则同步+事件上报 |
| 7 | 数据清理+监控 | - | 无障碍检测+防绕过 |

### Phase 3：测试发布（2周）

| 周次 | 内容 |
|------|------|
| 8 | 全链路联调测试 |
| 9 | Bug修复+打包发布 |

---

## 十二、域名与配置管理

### 配置汇总

| 配置项 | Backend (Go) | Flutter家长端 | Kotlin孩子端 |
|--------|-------------|---------------|-------------|
| 域名/API地址 | 环境变量注入 | `api_config.dart` 硬编码 | `NetworkUtils.kt` 硬编码 |
| 数据库密码 | `.env` → docker-compose | 不涉及 | 不涉及 |
| JWT密钥 | `.env` → docker-compose | 不感知 | 不感知 |
| 端口 | 8080 (容器内) | 不感知 | 不感知 |

### 提交前检查清单

```
□ backend/configs/config.example.yaml  →  只保留占位值，不提交真实域名
□ parent-app/lib/.../api_config.dart   →  baseUrl 用占位符 "https://YOUR_DOMAIN/api/v1"
□ child-app/.../NetworkUtils.kt        →  BASE_URL 用占位符 "https://YOUR_DOMAIN/api/v1"
□ .env.example                          →  不包含真实密码和密钥
```

### 部署流程

详见同级目录 `deplyment.md`

---

## 十三、日志系统设计

### 13.1 设计目标

```
1. 家长端和孩子端的运行日志统一上传到VPS
2. 开发者通过Web/API查看日志，定位问题
3. AI工具通过 /api/v1/logs/export 导出日志，分析异常模式后自动生成修复代码
```

### 13.2 数据流

```
Flutter家长端 / Kotlin孩子端
       ↓  本地缓存（最多500条，超过则丢弃最旧）
       ↓  每5分钟批量上传 或 缓存满200条触发上传
       ↓  POST /api/v1/logs
       ↓
   Go后端 → PostgreSQL app_logs 表
       ↓
  GET /api/v1/logs           → 人工查询
  GET /api/v1/logs/export    → AI工具下载分析
```

### 13.3 日志分级

| 级别 | 用途 | 举例 |
|------|------|------|
| `debug` | 开发调试 | API请求/响应、规则匹配过程 |
| `info` | 正常业务流程 | 用户登录、规则创建、拦截命中 |
| `warn` | 预期内的异常 | 网络超时（有重试）、权限被拒（引导用户） |
| `error` | 非预期错误 | 崩溃、数据库写入失败、Token解析失败 |

### 13.4 客户端实现要点

**Flutter端（parent-app）**：
```
class Logger {
  static List<LogEntry> _buffer = [];
  static Timer? _uploadTimer;

  // 四个静态方法：debug / info / warn / error
  // 内部：构造LogEntry → 加入_buffer → 触发本地缓存+定时上上传
  // 上传策略：每5分钟一次，或_buffer达200条立即发
  // 建议：用 dio 拦截器自动捕获所有HTTP请求的响应码和耗时
}

// 使用示例
Logger.info("Auth", "用户登录成功", {"email": user.email});
Logger.error("Network", "规则拉取失败", ex, stackTrace);
```

**Kotlin端（child-app）**：
```
object Logger {
    private val buffer = ConcurrentLinkedQueue<LogEntry>()
    private var lastUploadTime = 0L

    // 四个方法：d / i / w / e
    // 内部：加入buffer → 检查是否需要上传
    // 上传：WorkManager 每15分钟调度一次
    // Room缓存：防止进程被杀丢日志
}
```

### 13.5 AI分析流程

```
1. 开发者发现异常
       ↓
2. 调用 API 导出日志
   curl -H "Authorization: Bearer <token>" \
     "https://youdomain.com/api/v1/logs/export?source=child-app&level=error&start=2026-07-01"
       ↓
3. 获得 app_logs_export.jsonl 文件 (JSON Lines格式, 每行一条JSON)
       ↓
4. 喂给AI工具（如Claude/ChatGPT）：
   "请分析这份日志，找出异常模式，并给出修复这些bug的代码"
       ↓
5. AI返回分析报告和修复代码
```

### 13.6 日志表中字段说明

| 字段 | 说明 |
|------|------|
| `source` | `parent-app` 或 `child-app` |
| `device_id` | 孩子端专用，家长端为空 |
| `owner_id` | 家长端专用，孩子端为空 |
| `level` | 日志级别 |
| `tag` | 模块标识，如 `Auth`, `Network`, `RuleMatcher` |
| `message` | 日志正文 |
| `stacktrace` | error级别时附带完整堆栈 |
| `logged_at` | 客户端产生日志的UTC时间 |
| `uploaded_at` | 服务端接收时间 |

---

## 十四、已知限制与风险

| 风险 | 说明 | 缓解措施 |
|------|------|----------|
| 无障碍服务被杀 | 部分厂商ROM会主动杀后台服务 | 定时检测（30秒）+引导用户重新开启+前台通知保活 |
| 国产ROM兼容性 | 华为/小米/OPPO/vivo各有权限策略 | 测试多品牌+提供权限引导页 |
| 恢复出厂设置 | 无法阻止，解绑后家长收到通知 | 设备离线超过15分钟推送告警 |
| 离线模式 | 孩子设备无网络时无法同步新规则 | 本地缓存最近一次拉取的规则 |
| 应用商店审核 | Google Play对无障碍服务审核严格 | 先用APK侧载分发 |
| 1核2G性能瓶颈 | 高并发时可能出现响应慢 | Go异步处理+连接池+数据库索引 |
| 安全模式启动 | 进入安全模式后所有第三方App停止运行 | 无法防护，文档中标注为已知限制 |
| 多家长管理 | 一台设备暂不支持多个家长账号同时管理 | MVP单家长，后续按需扩展 |

### 监控告警

```
后端健康检查: GET /health → 返回 { "status": "ok" }
uptime监控: 使用 UptimeRobot 或类似服务每5分钟探测
告警方式: 邮件/短信通知管理员服务异常
```


