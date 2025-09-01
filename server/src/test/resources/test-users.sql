DROP TABLE IF EXISTS tenants;

CREATE TABLE tenants (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id VARCHAR(255) NOT NULL,
  username VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL
);

-- user: password
INSERT INTO tenants (tenant_id, username, password) VALUES ('test-tenant', 'user', '{bcrypt}$2a$10$ktqKDD4Cj9JU.7WsOySVPu92SwYGrE.XQeOpNCWuHxziSG1ozu3XO');

-- user1: password1
INSERT INTO tenants (tenant_id, username, password) VALUES ('tenant1', 'user1', '{bcrypt}$2a$12$NtxHpY75dX0OIshQEAiQM.PtpQv3eLccUkqiJCOn4nxD0kCi03oC6');

-- user2: password2
INSERT INTO tenants (tenant_id, username, password) VALUES ('tenant2', 'user2', '{bcrypt}$2a$12$H5yTlWmXqq8zmI/WQaMgK.48nBnPZdFvHCyD7bvKZu67/trYbBZo6');