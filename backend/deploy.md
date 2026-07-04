# FamilyGuard 后端部署文档

> 目标读者：AI Agent（自动部署）
> 前提：VPS 已安装 Docker，代码已克隆到 /opt/family-guard

---

## 部署方式：Docker Compose（仅 PostgreSQL）+ 直接运行二进制

编译好的二进制文件：`/opt/family-guard/backend/server`

## Step 1: 启动 PostgreSQL

```bash
cd /opt/family-guard

# 创建 .env 文件
DB_PASS=$(openssl rand -hex 16)
JWT_SEC=$(openssl rand -hex 32)

cat > .env << EOF
DB_NAME=family_guard
DB_USER=postgres
DB_PASSWORD=${DB_PASS}
JWT_SECRET=${JWT_SEC}
EOF

# 仅启动 PostgreSQL 容器
docker compose up -d postgres

# 等待 PostgreSQL 就绪（约5秒）
sleep 5
docker compose exec postgres pg_isready -U postgres
```

## Step 2: 启动后端

```bash
cd /opt/family-guard/backend

chmod +x server

JWT_SECRET=$(grep JWT_SECRET /opt/family-guard/.env | cut -d= -f2) \
DB_HOST=127.0.0.1 \
DB_PORT=5432 \
DB_USER=postgres \
DB_PASSWORD=$(grep DB_PASSWORD /opt/family-guard/.env | cut -d= -f2) \
DB_NAME=family_guard \
SERVER_PORT=8080 \
./server &
```

## Step 3: 验证

```bash
curl http://localhost:8080/health
# 期望: {"status":"ok"}
```

## Step 4: 配置 Nginx 反向代理（80端口）

```bash
apt install -y nginx

cat > /etc/nginx/sites-available/familyguard << 'EOF'
server {
    listen 80;
    server_name _;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
EOF

ln -sf /etc/nginx/sites-available/familyguard /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl restart nginx
```

## Step 5: 设置 systemd 服务（开机自启）

```bash
DB_PASS=$(grep DB_PASSWORD /opt/family-guard/.env | cut -d= -f2)
JWT_SEC=$(grep JWT_SECRET /opt/family-guard/.env | cut -d= -f2)

cat > /etc/systemd/system/familyguard.service << EOF
[Unit]
Description=FamilyGuard Backend
After=network.target docker.service

[Service]
Type=simple
WorkingDirectory=/opt/family-guard/backend
ExecStart=/opt/family-guard/backend/server
Environment=DB_HOST=127.0.0.1
Environment=DB_PORT=5432
Environment=DB_USER=postgres
Environment=DB_PASSWORD=${DB_PASS}
Environment=DB_NAME=family_guard
Environment=JWT_SECRET=${JWT_SEC}
Environment=SERVER_PORT=8080
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable familyguard
systemctl start familyguard
```

## 验证最终状态

```bash
# PostgreSQL
docker compose -f /opt/family-guard/docker-compose.yml ps

# 后端服务
systemctl status familyguard

# API 健康检查
curl http://localhost/health
```

## 常用运维

```bash
# 重启后端
systemctl restart familyguard

# 查看日志
journalctl -u familyguard -f

# 重启 PostgreSQL
cd /opt/family-guard && docker compose restart postgres

# 更新后端（替换二进制后重启）
systemctl stop familyguard
cp /path/to/new/server /opt/family-guard/backend/server
chmod +x /opt/family-guard/backend/server
systemctl start familyguard
```
