DROP TABLE IF EXISTS tenants;

CREATE TABLE tenants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  username VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL
);

INSERT INTO tenants (tenant_id, username, password) VALUES ('test-tenant', 'user', '{bcrypt}$2a$10$ktqKDD4Cj9JU.7WsOySVPu92SwYGrE.XQeOpNCWuHxziSG1ozu3XO'); -- password: "password" (online bcrypt, cost 10)