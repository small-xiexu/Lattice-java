#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DB_NAME="${LATTICE_DEV_DB_NAME:-ai-rag-knowledge}"
PG_CONTAINER="${LATTICE_DEV_PG_CONTAINER:-vector_db}"
DDL_FILE="${LATTICE_SCHEMA_SQL:-$ROOT_DIR/src/main/resources/db/schema.sql}"

if [[ ! -f "$DDL_FILE" ]]; then
  echo "DDL 文件不存在: $DDL_FILE" >&2
  exit 1
fi

echo "重建 ai-rag-knowledge.lattice schema"
docker exec -i "$PG_CONTAINER" psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 <<'SQL'
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
DROP SCHEMA IF EXISTS lattice CASCADE;
CREATE SCHEMA lattice;
SQL

docker exec -i "$PG_CONTAINER" psql -U postgres -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$DDL_FILE"
echo "lattice schema 已按 src/main/resources/db/schema.sql 重建完成"
