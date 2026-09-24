#!/bin/sh
set -eu

# Only runs when these new local PostgreSQL instances initialize their data directory.
# Application connections use a non-superuser, separate from the bootstrap role.
psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=app_user="$APP_DB_USER" --set=app_password="$APP_DB_PASSWORD" \
  --set=database="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"app_user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
GRANT CONNECT ON DATABASE :"database" TO :"app_user";
ALTER SCHEMA public OWNER TO :"app_user";
SQL
