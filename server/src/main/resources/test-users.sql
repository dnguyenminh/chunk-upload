-- SQL script to create a test tenant user for authentication
DROP TABLE IF EXISTS upload_info;
DROP TABLE IF EXISTS tenants;

CREATE TABLE tenants
(
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE upload_info
(
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id VARCHAR(255) NOT NULL UNIQUE,
    checksum VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    upload_date_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_date_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    tenant_id BIGINT NOT NULL,
    CONSTRAINT fk_upload_info_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- Insert a tenant with a fixed id for test consistency
INSERT INTO tenants (id, tenant_id, username, password)
VALUES (1, 'testTenant', 'user', '{bcrypt}$2a$10$ab5u9WOWBuZ.474A3TiGy.geEZvOviygiBnNfZITbdC5ehKzGiYzW');

INSERT INTO tenants (tenant_id, username, password)
VALUES ('testTenant',
        'user',
        '{bcrypt}$2a$10$ab5u9WOWBuZ.474A3TiGy.geEZvOviygiBnNfZITbdC5ehKzGiYzW');