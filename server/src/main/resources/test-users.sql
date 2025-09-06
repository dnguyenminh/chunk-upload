-- SQL script to create a test tenant user for authentication
-- DROP TABLE IF EXISTS "upload_info";
-- DROP TABLE IF EXISTS "tenants";
--
-- CREATE TABLE upload_info
-- (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     upload_id VARCHAR(255) NOT NULL UNIQUE,
--     tenant_id BIGINT NOT NULL,
--     filename VARCHAR(255) NOT NULL,
--     checksum VARCHAR(255) NOT NULL,
--     status VARCHAR(50),
--     UPLOAD_DATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP
-- );
--
-- CREATE TABLE tenants
-- (
--     id       BIGINT AUTO_INCREMENT PRIMARY KEY,
--     tenant_id VARCHAR(255) NOT NULL,
--     username VARCHAR(255) NOT NULL UNIQUE,
--     password VARCHAR(255) NOT NULL
-- );

INSERT INTO tenants (tenant_id, username, password)
VALUES ('testTenant',
        'user',
        '{bcrypt}$2a$10$ab5u9WOWBuZ.474A3TiGy.geEZvOviygiBnNfZITbdC5ehKzGiYzW');