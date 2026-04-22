#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEV_SCHEMA="${LATTICE_DEV_SCHEMA:-lattice}"
DEV_DB_NAME="${LATTICE_DEV_DB_NAME:-ai-rag-knowledge}"
DEV_DB_HOST="${LATTICE_DEV_DB_HOST:-127.0.0.1}"
DEV_DB_PORT="${LATTICE_DEV_DB_PORT:-5432}"
PG_CONTAINER="${LATTICE_DEV_PG_CONTAINER:-vector_db}"
RESET_SCHEMA=false

usage() {
  cat <<'EOF'
用法:
  ./scripts/run-local-dev.sh [--reset-schema]

说明:
  - 固定走 local-dev profile
  - 默认连接 ai-rag-knowledge.lattice
  - 会主动覆盖 schema / profile 相关的 SPRING_* 变量，避免沿用旧环境
  - 传 --reset-schema 时，会先 drop + recreate 当前开发 schema

可选环境变量:
  LATTICE_DEV_SCHEMA         默认 lattice
  LATTICE_DEV_DB_NAME        默认 ai-rag-knowledge
  LATTICE_DEV_DB_HOST        默认 127.0.0.1
  LATTICE_DEV_DB_PORT        默认 5432
  LATTICE_DEV_PG_CONTAINER   默认 vector_db
  SERVER_PORT                默认 18082
EOF
}

ensure_valid_identifier() {
  local value="$1"
  if [[ ! "$value" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "非法 schema 名称: $value" >&2
    exit 1
  fi
}

run_psql() {
  local sql="$1"
  docker exec "$PG_CONTAINER" psql -U postgres -d "$DEV_DB_NAME" -c "$sql"
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

ensure_valid_identifier "$DEV_SCHEMA"

export SPRING_PROFILES_ACTIVE="local-dev"
export SERVER_PORT="${SERVER_PORT:-18082}"
export SPRING_DATASOURCE_URL="jdbc:postgresql://${DEV_DB_HOST}:${DEV_DB_PORT}/${DEV_DB_NAME}?currentSchema=${DEV_SCHEMA}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-postgres}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-postgres}"
export SPRING_FLYWAY_ENABLED="true"
export SPRING_FLYWAY_SCHEMAS="${DEV_SCHEMA}"
export SPRING_FLYWAY_DEFAULT_SCHEMA="${DEV_SCHEMA}"
export LATTICE_REDIS_HOST="${LATTICE_REDIS_HOST:-127.0.0.1}"
export LATTICE_REDIS_PORT="${LATTICE_REDIS_PORT:-6379}"

if [[ "$RESET_SCHEMA" == "true" ]]; then
  echo "重建开发 schema: ${DEV_SCHEMA}"
  run_psql "DROP SCHEMA IF EXISTS ${DEV_SCHEMA} CASCADE; CREATE SCHEMA ${DEV_SCHEMA};"
else
  run_psql "CREATE SCHEMA IF NOT EXISTS ${DEV_SCHEMA};" >/dev/null
fi

echo "启动 local-dev 开发环境:"
echo "  schema     : ${DEV_SCHEMA}"
echo "  datasource : ${SPRING_DATASOURCE_URL}"
echo "  port       : ${SERVER_PORT}"
echo "  redis      : ${LATTICE_REDIS_HOST}:${LATTICE_REDIS_PORT}"

cd "$ROOT_DIR"
exec mvn -q -s .codex/maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=local-dev
