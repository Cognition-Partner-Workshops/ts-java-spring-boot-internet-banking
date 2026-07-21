-- banking_core_service.banking_core_user definition

CREATE TABLE banking_core_user (
    id                    BIGSERIAL NOT NULL,
    email                 varchar(255) DEFAULT NULL,
    first_name            varchar(255) DEFAULT NULL,
    identification_number varchar(255) DEFAULT NULL,
    last_name             varchar(255) DEFAULT NULL,
    PRIMARY KEY (id)
);

-- banking_core_service.banking_core_account definition

CREATE TABLE banking_core_account (
    id                BIGSERIAL NOT NULL,
    actual_balance    decimal(19, 2) DEFAULT NULL,
    available_balance decimal(19, 2) DEFAULT NULL,
    number            varchar(255)   DEFAULT NULL,
    status            varchar(255)   DEFAULT NULL,
    type              varchar(255)   DEFAULT NULL,
    user_id           bigint         DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FKt5uqy9p0v3rp3yhlgvm7ep0ij FOREIGN KEY (user_id) REFERENCES banking_core_user(id)
);

CREATE INDEX FKt5uqy9p0v3rp3yhlgvm7ep0ij ON banking_core_account(user_id);

-- banking_core_service.banking_core_utility_account definition

CREATE TABLE banking_core_utility_account (
    id            BIGSERIAL NOT NULL,
    number        varchar(255) DEFAULT NULL,
    provider_name varchar(255) DEFAULT NULL,
    PRIMARY KEY (id)
);
