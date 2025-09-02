#!/bin/bash
# Bash script to insert a test tenant user into H2 database using h2 shell
# Assumes h2 is available and database is at ./data/chunkedupload

echo "INSERT INTO tenants (tenantId, username, password) VALUES ('testTenant', 'testuser', '{bcrypt}$2a$10$ab5u9WOWBuZ.474A3TiGy.geEZvOviygiBnNfZITbdC5ehKzGiYzW');" > create-user.sql
java -cp "libs/h2-2.3.232.jar" org.h2.tools.RunScript -url "jdbc:h2:file:./data/chunkedupload" -user sa -script create-user.sql
rm create-user.sql