FROM mysql:8.4.0
# MYSQL_ROOT_PASSWORD and MYSQL_APP_USER_PASSWORD are injected at run time by docker-compose (see ../.env.example)
COPY ./privileges.sh /docker-entrypoint-initdb.d/
