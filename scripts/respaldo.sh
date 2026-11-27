#!/usr/bin/env bash
# Respaldo de Camino a la Iluminación en Linux/macOS o con Docker Compose.
#
#   scripts/respaldo.sh                 # usa mysqldump/mariadb-dump local y las variables DB_* de la aplicación
#   scripts/respaldo.sh --docker        # respalda la base del contenedor «db» de docker-compose.yml
#
# Variables: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD (como la app); RESPALDO_DIR (destino, por defecto
# ~/respaldos_camino_iluminacion) y RESPALDO_DIAS (rotación, por defecto 30). La contraseña nunca va en la línea
# de comandos: se pasa en la variable MYSQL_PWD solo al proceso de volcado.
set -euo pipefail

DOCKER=0
[ "${1:-}" = "--docker" ] && DOCKER=1

BASE="${DB_NAME:-camino_iluminacion}"
DESTINO="${RESPALDO_DIR:-$HOME/respaldos_camino_iluminacion}"
DIAS="${RESPALDO_DIAS:-30}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"

fallar() { echo "ERROR: $*" >&2; exit 1; }

[[ "$BASE" =~ ^[A-Za-z0-9_]+$ ]] || fallar "nombre de base de datos no válido: $BASE"
[[ "$DIAS" =~ ^[0-9]+$ ]] || fallar "RESPALDO_DIAS debe ser un número"
mkdir -p "$DESTINO"
DESTINO="$(cd "$DESTINO" && pwd)"
case "$DESTINO/" in "$REPO"/*) fallar "elige un destino fuera del repositorio ($REPO)";; esac

MARCA="$(date +%Y%m%d_%H%M%S)"
ARCHIVO="$DESTINO/${BASE}_${MARCA}.sql"
OPCIONES=(--single-transaction --routines --triggers --default-character-set=utf8mb4 --no-tablespaces)

umask 077
# Si algo falla a mitad de camino, no dejar un archivo a medias.
trap 'rm -f "$ARCHIVO"' EXIT
if [ "$DOCKER" = 1 ]; then
  cd "$REPO"
  # Dentro del contenedor ya existen MARIADB_USER y MARIADB_PASSWORD (los de .env).
  docker compose exec -T db sh -c 'MYSQL_PWD="$MARIADB_PASSWORD" exec mariadb-dump -u"$MARIADB_USER" '"${OPCIONES[*]}"' "$MARIADB_DATABASE"' > "$ARCHIVO"
else
  : "${DB_USER:?Falta la variable DB_USER}"
  : "${DB_PASSWORD:?Falta la variable DB_PASSWORD}"
  VOLCADO="$(command -v mariadb-dump || command -v mysqldump || true)"
  [ -n "$VOLCADO" ] || fallar "no se encontró mariadb-dump ni mysqldump"
  MYSQL_PWD="$DB_PASSWORD" "$VOLCADO" -h"${DB_HOST:-localhost}" -P"${DB_PORT:-3306}" -u"$DB_USER" \
    "${OPCIONES[@]}" "--result-file=$ARCHIVO" "$BASE"
fi

if ! tail -n 3 "$ARCHIVO" | grep -q "Dump completed"; then
  rm -f "$ARCHIVO"
  fallar "el respaldo quedó incompleto y se descartó"
fi
gzip -f "$ARCHIVO"
trap - EXIT
echo "OK base de datos: $ARCHIVO.gz ($(du -h "$ARCHIVO.gz" | cut -f1))"

if [ "$DIAS" -gt 0 ]; then
  find "$DESTINO" -maxdepth 1 -type f -name "${BASE}_*.sql.gz" -mtime +"$DIAS" -print -delete | sed 's/^/Borrado por antigüedad: /'
fi
