@echo off
REM Create SQL file to insert test tenant user
echo INSERT INTO tenants (tenantId, username, password^) VALUES ('testTenant', 'testuser', '{bcrypt}$2a$10$ab5u9WOWBuZ.474A3TiGy.geEZvOviygiBnNfZITbdC5ehKzGiYzW'); > create-user.sql

REM Run H2 RunScript tool to execute the SQL
java -cp "libs/h2-2.3.232.jar" org.h2.tools.RunScript -url "jdbc:h2:file:./data/chunkedupload" -user sa -script create-user.sql

REM Delete the SQL file
del create-user.sql

echo Done.