# Docker

## Build and run Chat2DB

The default compose stack now includes:

- `chat2db`: main Chat2DB application
- `chat2db-gateway`: local lightweight gateway
- `milvus`: standalone Milvus
- `milvus-etcd`
- `milvus-minio`

```bash
docker compose -f docker/compose.yml build
docker compose -f docker/compose.yml up -d
```

The application listens on `10824` by default and persists data in `${HOME}/.chat2db-docker`.
Milvus is exposed on `19530` by default, and the local gateway is exposed on `18080`.

By default:

- vector knowledge/schema requests go to the local `chat2db-gateway`
- the local gateway stores vectors in the compose-local Milvus instance
- non-vector gateway requests are proxied to `${GATEWAY_UPSTREAM_BASE_URL:-http://test.sqlgpt.cn/gateway}`

## Common commands

```bash
docker compose -f docker/compose.yml logs -f chat2db
docker compose -f docker/compose.yml logs -f chat2db-gateway
docker compose -f docker/compose.yml logs -f milvus
docker compose -f docker/compose.yml down
docker compose -f docker/compose.yml down -v
```

Override defaults with environment variables:

```bash
export CHAT2DB_TAG=local
export CHAT2DB_PORT=10824
export CHAT2DB_DATA_DIR=$HOME/.chat2db-docker
export CHAT2DB_GATEWAY_BASE_URL=http://chat2db-gateway:18080
export CHAT2DB_GATEWAY_MODEL_BASE_URL=http://chat2db-gateway:18080
export GATEWAY_UPSTREAM_BASE_URL=http://test.sqlgpt.cn/gateway
export MILVUS_HOST=milvus
export MILVUS_PORT=19530
export MILVUS_KNOWLEDGE_COLLECTION=chat2db_knowledge
export MILVUS_SCHEMA_COLLECTION=chat2db_schema
export MILVUS_METRIC_TYPE=COSINE
export MILVUS_TOP_K=5
```

If you want Chat2DB to use another Milvus instance, keep the local gateway in place and override:

```bash
export MILVUS_HOST=<your-milvus-host>
export MILVUS_PORT=<your-milvus-port>
docker compose -f docker/compose.yml up -d
```

If you want Chat2DB to use another gateway address entirely, override:

```bash
export CHAT2DB_GATEWAY_BASE_URL=http://<your-gateway-host>:<port>
export CHAT2DB_GATEWAY_MODEL_BASE_URL=http://<your-gateway-host>:<port>
docker compose -f docker/compose.yml up -d
```

## Start local dependency services

```bash
docker compose -f docker/compose.dev-services.yml up -d
```

Services provided:
- MySQL: `localhost:3306`, database `ali_dbhub_test`, user `root`, password `ali_dbhub`
- PostgreSQL: `localhost:5432`, database `ali_dbhub_test`, user `ali_dbhub`, password `ali_dbhub`
- Redis: `localhost:6379`, password `12345678`
