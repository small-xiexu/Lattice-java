#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEV_DB_NAME="${LATTICE_DEV_DB_NAME:-ai-rag-knowledge}"
DEV_DB_HOST="${LATTICE_DEV_DB_HOST:-127.0.0.1}"
DEV_DB_PORT="${LATTICE_DEV_DB_PORT:-5432}"
RESET_SCHEMA=false

usage() {
  cat <<'EOF'
用法:
  ./scripts/run-local-dev.sh [--reset-schema]

说明:
  - 固定走 local-dev profile
  - 固定连接 ai-rag-knowledge.lattice
  - 不再设置 Flyway 相关参数，应用启动不会自动建表
  - 传 --reset-schema 时，会显式执行 scripts/reset-lattice-schema.sh

可选环境变量:
  LATTICE_DEV_DB_NAME        默认 ai-rag-knowledge
  LATTICE_DEV_DB_HOST        默认 127.0.0.1
  LATTICE_DEV_DB_PORT        默认 5432
  LATTICE_DEV_PG_CONTAINER   默认 vector_db，仅 --reset-schema 使用
  SERVER_PORT                默认 18082
EOF
}

for arg in "$@"; do
  case "$arg" in
    --reset-schema)
      RESET_SCHEMA=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数: $arg" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ "$RESET_SCHEMA" == "true" ]]; then
  "$ROOT_DIR/scripts/reset-lattice-schema.sh"
fi

export SPRING_PROFILES_ACTIVE="local-dev"
export SERVER_PORT="${SERVER_PORT:-18082}"
export SPRING_DATASOURCE_URL="jdbc:postgresql://${DEV_DB_HOST}:${DEV_DB_PORT}/${DEV_DB_NAME}?currentSchema=lattice"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-postgres}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-postgres}"
export LATTICE_REDIS_HOST="${LATTICE_REDIS_HOST:-127.0.0.1}"
export LATTICE_REDIS_PORT="${LATTICE_REDIS_PORT:-6379}"

echo "启动 local-dev 开发环境:"
echo "  schema     : lattice"
echo "  datasource : ${SPRING_DATASOURCE_URL}"
echo "  port       : ${SERVER_PORT}"
echo "  redis      : ${LATTICE_REDIS_HOST}:${LATTICE_REDIS_PORT}"

cd "$ROOT_DIR"
exec mvn -q -s .codex/maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=local-dev
