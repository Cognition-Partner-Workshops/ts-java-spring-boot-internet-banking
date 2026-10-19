CREATE USER javatodev_development WITH PASSWORD 'oPItyPticIAt';

CREATE DATABASE banking_core_service;
CREATE DATABASE banking_core_fund_transfer_service;
CREATE DATABASE banking_core_user_service;
CREATE DATABASE banking_core_utility_payment_service;

GRANT ALL PRIVILEGES ON DATABASE banking_core_service TO javatodev_development;
GRANT ALL PRIVILEGES ON DATABASE banking_core_fund_transfer_service TO javatodev_development;
GRANT ALL PRIVILEGES ON DATABASE banking_core_user_service TO javatodev_development;
GRANT ALL PRIVILEGES ON DATABASE banking_core_utility_payment_service TO javatodev_development;

\connect banking_core_service
GRANT ALL ON SCHEMA public TO javatodev_development;
\connect banking_core_fund_transfer_service
GRANT ALL ON SCHEMA public TO javatodev_development;
\connect banking_core_user_service
GRANT ALL ON SCHEMA public TO javatodev_development;
\connect banking_core_utility_payment_service
GRANT ALL ON SCHEMA public TO javatodev_development;
