#!/bin/sh
set -eu

cd "$(dirname "$0")"

new_secret() {
    hex=$(od -An -v -N 32 -t x1 /dev/urandom) || return 1
    printf '%s' "$hex" | tr -d '[:space:]'
}

if [ ! -e .env.portable ]; then
    database_password=$(new_secret)
    root_password=$(new_secret)
    service_key=$(new_secret)
    (
        umask 077
        set -C
        printf '%s\n' \
            'EQUIFLOW_MYSQL_HOST=mysql' \
            'EQUIFLOW_MYSQL_PORT=3306' \
            'EQUIFLOW_DATABASE_USER=equiflow' \
            "EQUIFLOW_DATABASE_PASSWORD=$database_password" \
            "EQUIFLOW_MYSQL_ROOT_PASSWORD=$root_password" \
            "EQUIFLOW_SERVICE_KEY=$service_key" \
            'EQUIFLOW_HTTP_PORT=8087' > .env.portable
    )
fi

docker compose --env-file .env.portable -f compose.yaml -f compose.portable.yaml build registry
docker compose --env-file .env.portable -f compose.yaml -f compose.portable.yaml up -d --no-build --wait
printf '%s\n' 'EquiFlow is ready at http://127.0.0.1:8087'
