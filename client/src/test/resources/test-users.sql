-- SQL script to create a test tenant user for authentication
DROP TABLE IF EXISTS upload_info CASCADE;
DROP TABLE IF EXISTS tenants CASCADE;

CREATE TABLE upload_info
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id VARCHAR(255) NOT NULL UNIQUE,
    tenant_id BIGINT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    checksum VARCHAR(255) NOT NULL,
    status VARCHAR(50),
    upload_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_update_date_time TIMESTAMP
);

CREATE TABLE tenants
(
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

INSERT INTO tenants (tenant_id, username, password)
VALUES ('test-tenant', 'user',
        '{bcrypt}$2a$10$ktqKDD4Cj9JU.7WsOySVPu92SwYGrE.XQeOpNCWuHxziSG1ozu3XO'); -- password: "password" (online bcrypt, cost 10)