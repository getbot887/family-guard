# KidSafe 部署指南

## 一、配置总览

```
┌──────────────────────────────────────────────┐
│               VPS (1核2G)                      │
│                                               │
│  docker-compose.yml  ←  通过 .env 注入配置     │
│       ↓                                       │
│  ┌──────────┐  ┌──────────┐                   │
│  │ PostgreSQL│←│ Go 后端  │                   │
│  │ :5432    │  │ :8080    │                   │
│  └──────────┘  └────┬─────┘                   │
│                      │                         │
│                      │ 反向代理 (Caddy/Nginx)    │
│                      │ → https://你的域名.com   │
│                      ↓                         │
│                ┌──────────┐                    │
│                │  域名指向  │                    │
│                └──────────┘                    │
└──────────────────────────────────────────────┘
       ↑                    ↑
       │ HTTPS              │ HTTPS
  Flutter家长端App      Kotlin孩子端App
  (api_config.dart)    (NetworkUtils.kt)
```

## 二、域名配置位置

### 2.1 后端（VPS上）

后端启动后监听 `:8080`，用 Caddy 或 Nginx 反向代理到域名。

**Caddy**（推荐，自动HTTPS）：
```caddyfile
kidsafe.yourdomain.com {
    reverse_proxy backend:8080
}```

**Nginx**：
```nginx
server {
    server_name kidsafe.yourdomain.com;
    location / {
        proxy_pass http://127.0.0.1:8080;
    }
}
```

### 2.2 Flutter家长端

**文件**：`parent-app/lib/core/config/api_config.dart`

```dart
// 开发环境（Android模拟器）
// static const String baseUrl = 'http://10.0.2.2:8080/api/v1';

// 生产环境（VPS域名）
static const String baseUrl = 'https://kidsafe.yourdomain.com/api/v1';
```

### 2.3 Kotlin孩子端

**文件**：`kidsafe/src/main/kotlin/com/kidsafe/util/NetworkUtils.kt`

```kotlin
// 开发环境
// private const val BASE_URL = "http://10.0.2.2:8080/api/v1"

// 生产环境（VPS域名）
private const val BASE_URL = "https://kidsafe.yourdomain.com/api/v1"
```

## 三、VPS部署步骤

```bash
# 1. 把项目复制到VPS
scp -r kidsafe root@your-vps:/opt/

# 2. 进入目录
cd /opt/kidsafe

# 3. 配置环境变量
cp .env.example .env
vim .env   # 修改 JWT_SECRET 和 DB_PASSWORD

# 4. 安装 Caddy（自动HTTPS）
sudo apt install caddy

# 5. 配置 Caddy
sudo vim /etc/caddy/Caddyfile
# 添加：
# kidsafe.yourdomain.com {
#     reverse_proxy localhost:8080
# }

# 6. 启动服务
docker-compose up -d

# 7. 验证
curl https://kidsafe.yourdomain.com/health
# 返回 {"status":"ok"}
```

## 四、域名填写清单

| 文件 | 行 | 需要填什么 |
|------|-----|-----------|
| `docker-compose.yml` | - | 无需改（用 .env 注入） |
| `.env` | JWT_SECRET | 随机字符串 |
| `parent-app/lib/.../api_config.dart` | baseUrl | 你的域名 |
| `child-app/.../NetworkUtils.kt` | BASE_URL | 你的域名 |
