#!/bin/bash
set -e

# Start DB
docker-compose up -d mysql
echo "Waiting for MySQL..."
sleep 15

# Drop existing DB to ensure clean state
docker exec mediconnect-mysql mysql -uroot -prootpassword -e "DROP DATABASE IF EXISTS mediconnect_db; CREATE DATABASE mediconnect_db;"

# Compile and run spring boot to generate schema
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export SPRING_JPA_HIBERNATE_DDL_AUTO=create
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/mediconnect_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export SPRING_DATASOURCE_USERNAME=mediconnect
export SPRING_DATASOURCE_PASSWORD=mediconnect123

# Run in background and wait for it to start
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081" &
PID=$!
sleep 20
kill $PID

# Dump the schema
docker exec mediconnect-mysql mysqldump -uroot -prootpassword --no-data mediconnect_db > src/main/resources/db/migration/V1__baseline.sql

echo "Schema generated!"
