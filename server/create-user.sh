#!/bin/bash
# Bash script to insert a test tenant user into H2 database using h2 shell
# Assumes h2 is available and database is at ./data/chunkedupload

#!/bin/bash
# Usage: ./create-user.sh tenantId username password

if [[ "$1" == "--help" || "$1" == "-h" ]]; then
  echo "Usage: ./create-user.sh tenantId username password"
  echo "All parameters are required and must be non-empty."
  echo "Example:"
  echo "  ./create-user.sh myTenant myUser myPassword"
  exit 1
fi

if [[ -z "$1" ]]; then
  echo "Error: tenantId is required."
  echo "Usage: ./create-user.sh tenantId username password"
  exit 2
fi
if [[ -z "$2" ]]; then
  echo "Error: username is required."
  echo "Usage: ./create-user.sh tenantId username password"
  exit 2
fi
if [[ -z "$3" ]]; then
  echo "Error: password is required."
  echo "Usage: ./create-user.sh tenantId username password"
  exit 2
fi

tenantId="$1"
username="$2"
password="$3"

# Run the utility
java -cp "libs/spring-security-crypto-6.1.7.jar:libs/spring-boot-3.1.8.jar:libs/spring-boot-autoconfigure-3.1.8.jar:libs/spring-context-6.1.7.jar:libs/spring-data-jpa-3.1.8.jar:libs/jakarta.persistence-api-3.1.0.jar:build/classes/java/main" vn.com.fecredit.chunkedupload.util.CreateUserUtility "$tenantId" "$username" "$password"

echo "Done."