# Docker

## Build and run Chat2DB

```bash
docker compose -f docker/compose.yml build
docker compose -f docker/compose.yml up -d
```

The application listens on `10824` by default and persists data in `${HOME}/.chat2db-docker`.

## Common commands

```bash
docker compose -f docker/compose.yml logs -f chat2db
docker compose -f docker/compose.yml down
docker compose -f docker/compose.yml down -v
```

Override defaults with environment variables:

```bash
export CHAT2DB_TAG=local
export CHAT2DB_PORT=10824
export CHAT2DB_DATA_DIR=$HOME/.chat2db-docker
```

## Start local dependency services

```bash
docker compose -f docker/compose.dev-services.yml up -d
```

Services provided:
- MySQL: `localhost:3306`, database `ali_dbhub_test`, user `root`, password `ali_dbhub`
- PostgreSQL: `localhost:5432`, database `ali_dbhub_test`, user `ali_dbhub`, password `ali_dbhub`
- Redis: `localhost:6379`, password `12345678`
