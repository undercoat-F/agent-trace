#!/bin/sh
set -eu

escape_sql() {
  printf "%s" "$1" | sed "s/'/''/g"
}

readonly_user=$(escape_sql "$MYSQL_RO_USER")
readonly_password=$(escape_sql "$MYSQL_RO_PASSWORD")
database_name=$(escape_sql "$MYSQL_DB")

mysql --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS '$readonly_user'@'%' IDENTIFIED BY '$readonly_password';
ALTER USER '$readonly_user'@'%' IDENTIFIED BY '$readonly_password';
GRANT SELECT ON \`$database_name\`.* TO '$readonly_user'@'%';
FLUSH PRIVILEGES;
SQL