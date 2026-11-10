#!/bin/bash
# Sourced by the mysql image entrypoint on first start; docker_process_sql is provided by it.
# The application user's password comes from MYSQL_APP_USER_PASSWORD (docker-compose/.env).
set -eu
: "${MYSQL_APP_USER_PASSWORD:?MYSQL_APP_USER_PASSWORD must be set}"
docker_process_sql <<-EOSQL
	CREATE USER 'javatodev_development'@'%' IDENTIFIED BY '${MYSQL_APP_USER_PASSWORD}';
	CREATE DATABASE IF NOT EXISTS banking_core_service;
	CREATE DATABASE IF NOT EXISTS banking_core_fund_transfer_service;
	CREATE DATABASE IF NOT EXISTS banking_core_user_service;
	CREATE DATABASE IF NOT EXISTS banking_core_utility_payment_service;

	GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES ON banking_core_service.* TO 'javatodev_development'@'%';
	GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES ON banking_core_fund_transfer_service.* TO 'javatodev_development'@'%';
	GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES ON banking_core_user_service.* TO 'javatodev_development'@'%';
	GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES ON banking_core_utility_payment_service.* TO 'javatodev_development'@'%';
	FLUSH PRIVILEGES;
EOSQL
