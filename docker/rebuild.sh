#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== 当前目录: $PROJECT_DIR ==="

# 1. 检查当前分支
cd "$PROJECT_DIR"
CURRENT_BRANCH=$(git branch --show-current)
echo "当前分支: $CURRENT_BRANCH"

if [ "$CURRENT_BRANCH" != "codex/tmp" ]; then
    echo "警告: 当前分支不是 codex/tmp，是否继续？(y/n)"
    read -r confirm
    if [ "$confirm" != "y" ]; then
        echo "已取消"
        exit 0
    fi
fi

# 2. 停止并移除旧容器
echo ""
echo "=== 停止并移除旧容器 ==="
docker compose -f docker/compose.yml stop chat2db chat2db-gateway 2>/dev/null || true
docker compose -f docker/compose.yml rm -f chat2db chat2db-gateway 2>/dev/null || true

# 3. 构建新镜像
echo ""
echo "=== 构建新镜像 ==="
docker compose -f docker/compose.yml build chat2db chat2db-gateway

# 4. 启动容器
echo ""
echo "=== 启动容器 ==="
docker compose -f docker/compose.yml up -d chat2db chat2db-gateway

# 5. 检查状态
echo ""
echo "=== 容器状态 ==="
docker compose -f docker/compose.yml ps chat2db chat2db-gateway

echo ""
echo "=== 完成 ==="
echo "Chat2DB 主服务: http://localhost:10824"
echo "Gateway 服务:   http://localhost:18080"
echo ""
echo "查看日志: docker compose -f docker/compose.yml logs -f chat2db"
