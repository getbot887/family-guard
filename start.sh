#!/bin/bash
# FamilyGuard 后端启动脚本
cd "$(dirname "$0")/backend"
export JWT_SECRET=local-dev-key
export GIN_MODE=debug
export SERVER_PORT=8080

# 杀死旧进程
pkill -f "familyguard-server" 2>/dev/null

# 启动
./server &
sleep 1
echo "后端已启动: http://localhost:8080"
echo "预置账号: admin@familyguard.com / admin123456"
