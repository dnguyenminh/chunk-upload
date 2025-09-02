-- SQL script to create a test tenant user for authentication
INSERT INTO tenants (tenantId, username, password) VALUES (
  'testTenant',
  'testuser',
  '{bcrypt}$2a$10$ab5u9WOWBuZ.474A3TiGy.geEZvOviygiBnNfZITbdC5ehKzGiYzW'
);