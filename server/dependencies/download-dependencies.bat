REM Diagnostic: List all jars in libs before running the app
echo ==== JARs in libs ====
dir libs\*.jar /b
echo ======================
@echo off
setlocal

REM Download all runtime dependencies

REM Spring Boot & Spring Framework (resolved versions)
curl -L -o spring-boot-3.5.5.jar https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/3.5.5/spring-boot-3.5.5.jar
curl -L -o spring-boot-starter-3.5.5.jar https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter/3.5.5/spring-boot-starter-3.5.5.jar
curl -L -o spring-boot-starter-web-3.5.5.jar https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-web/3.5.5/spring-boot-starter-web-3.5.5.jar
curl -L -o spring-boot-starter-security-3.5.5.jar https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-security/3.5.5/spring-boot-starter-security-3.5.5.jar
curl -L -o spring-boot-starter-data-jpa-3.5.5.jar https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-data-jpa/3.5.5/spring-boot-starter-data-jpa-3.5.5.jar
curl -L -o spring-boot-autoconfigure-3.5.5.jar https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-autoconfigure/3.5.5/spring-boot-autoconfigure-3.5.5.jar
curl -L -o spring-context-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-context/6.2.10/spring-context-6.2.10.jar
curl -L -o spring-core-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-core/6.2.10/spring-core-6.2.10.jar
curl -L -o spring-beans-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-beans/6.2.10/spring-beans-6.2.10.jar
curl -L -o spring-expression-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-expression/6.2.10/spring-expression-6.2.10.jar
curl -L -o spring-aop-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-aop/6.2.10/spring-aop-6.2.10.jar
curl -L -o spring-jcl-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-jcl/6.2.10/spring-jcl-6.2.10.jar
curl -L -o spring-web-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-web/6.2.10/spring-web-6.2.10.jar
curl -L -o spring-webmvc-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-webmvc/6.2.10/spring-webmvc-6.2.10.jar
curl -L -o spring-tx-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-tx/6.2.10/spring-tx-6.2.10.jar
curl -L -o spring-orm-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-orm/6.2.10/spring-orm-6.2.10.jar
curl -L -o spring-jdbc-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-jdbc/6.2.10/spring-jdbc-6.2.10.jar
curl -L -o spring-aspects-6.2.10.jar https://repo1.maven.org/maven2/org/springframework/spring-aspects/6.2.10/spring-aspects-6.2.10.jar

REM REMOVE ALL OLDER SPRING JARS
del libs\spring-boot-starter-data-jpa-3.2.6.jar 2>nul
del libs\spring-boot-starter-security-3.2.6.jar 2>nul
del libs\spring-boot-starter-web-3.2.6.jar 2>nul
del libs\spring-core-6.0.12.jar 2>nul
del libs\spring-context-6.0.12.jar 2>nul
del libs\spring-aop-6.0.12.jar 2>nul
del libs\spring-security-core-6.2.2.jar 2>nul
del libs\spring-security-config-6.2.2.jar 2>nul
del libs\spring-security-crypto-6.2.2.jar 2>nul
del libs\spring-security-web-6.2.2.jar 2>nul
del libs\spring-data-jpa-3.2.6.jar 2>nul

REM Security
curl -L -o spring-security-core-6.5.3.jar https://repo1.maven.org/maven2/org/springframework/security/spring-security-core/6.5.3/spring-security-core-6.5.3.jar
curl -L -o spring-security-config-6.5.3.jar https://repo1.maven.org/maven2/org/springframework/security/spring-security-config/6.5.3/spring-security-config-6.5.3.jar
curl -L -o spring-security-web-6.5.3.jar https://repo1.maven.org/maven2/org/springframework/security/spring-security-web/6.5.3/spring-security-web-6.5.3.jar
curl -L -o spring-security-crypto-6.5.3.jar https://repo1.maven.org/maven2/org/springframework/security/spring-security-crypto/6.5.3/spring-security-crypto-6.5.3.jar

REM Data JPA & JDBC
curl -L -o spring-data-jpa-3.5.3.jar https://repo1.maven.org/maven2/org/springframework/data/spring-data-jpa/3.5.3/spring-data-jpa-3.5.3.jar
curl -L -o spring-data-commons-3.5.3.jar https://repo1.maven.org/maven2/org/springframework/data/spring-data-commons/3.5.3/spring-data-commons-3.5.3.jar
curl -L -o hibernate-core-6.6.26.Final.jar https://repo1.maven.org/maven2/org/hibernate/orm/hibernate-core/6.6.26.Final/hibernate-core-6.6.26.Final.jar
curl -L -o hibernate-commons-annotations-7.0.3.Final.jar https://repo1.maven.org/maven2/org/hibernate/common/hibernate-commons-annotations/7.0.3.Final/hibernate-commons-annotations-7.0.3.Final.jar
curl -L -o h2-2.3.232.jar https://repo1.maven.org/maven2/com/h2database/h2/2.3.232/h2-2.3.232.jar
curl -L -o hikariCP-6.3.2.jar https://repo1.maven.org/maven2/com/zaxxer/HikariCP/6.3.2/HikariCP-6.3.2.jar

REM Jackson
curl -L -o jackson-annotations-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.19.2/jackson-annotations-2.19.2.jar
curl -L -o jackson-core-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.19.2/jackson-core-2.19.2.jar
curl -L -o jackson-databind-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.19.2/jackson-databind-2.19.2.jar
curl -L -o jackson-datatype-jdk8-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jdk8/2.19.2/jackson-datatype-jdk8-2.19.2.jar
curl -L -o jackson-datatype-jsr310-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.19.2/jackson-datatype-jsr310-2.19.2.jar
curl -L -o jackson-module-parameter-names-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/module/jackson-module-parameter-names/2.19.2/jackson-module-parameter-names-2.19.2.jar

REM Micrometer
curl -L -o micrometer-observation-1.15.3.jar https://repo1.maven.org/maven2/io/micrometer/micrometer-observation/1.15.3/micrometer-observation-1.15.3.jar
curl -L -o micrometer-commons-1.15.3.jar https://repo1.maven.org/maven2/io/micrometer/micrometer-commons/1.15.3/micrometer-commons-1.15.3.jar

REM Logback & SLF4J
curl -L -o logback-classic-1.5.18.jar https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.5.18/logback-classic-1.5.18.jar
curl -L -o logback-core-1.5.18.jar https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.5.18/logback-core-1.5.18.jar
curl -L -o slf4j-api-2.0.17.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar
curl -L -o jul-to-slf4j-2.0.17.jar https://repo1.maven.org/maven2/org/slf4j/jul-to-slf4j/2.0.17/jul-to-slf4j-2.0.17.jar
curl -L -o log4j-to-slf4j-2.24.3.jar https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-to-slf4j/2.24.3/log4j-to-slf4j-2.24.3.jar
curl -L -o log4j-api-2.24.3.jar https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3/log4j-api-2.24.3.jar

REM Jakarta & AspectJ
curl -L -o jakarta-annotation-api-2.1.1.jar https://repo1.maven.org/maven2/jakarta/annotation/jakarta.annotation-api/2.1.1/jakarta.annotation-api-2.1.1.jar
curl -L -o jakarta-persistence-api-3.1.0.jar https://repo1.maven.org/maven2/jakarta/persistence/jakarta.persistence-api/3.1.0/jakarta.persistence-api-3.1.0.jar
curl -L -o jakarta-transaction-api-2.0.1.jar https://repo1.maven.org/maven2/jakarta/transaction/jakarta.transaction-api/2.0.1/jakarta.transaction-api-2.0.1.jar
curl -L -o jakarta-xml-bind-api-4.0.2.jar https://repo1.maven.org/maven2/jakarta/xml/bind/jakarta.xml.bind-api/4.0.2/jakarta.xml.bind-api-4.0.2.jar
curl -L -o jakarta-activation-api-2.1.3.jar https://repo1.maven.org/maven2/jakarta/activation/jakarta.activation-api/2.1.3/jakarta-activation-api-2.1.3.jar
curl -L -o aspectjweaver-1.9.24.jar https://repo1.maven.org/maven2/org/aspectj/aspectjweaver/1.9.24/aspectjweaver-1.9.24.jar

REM Other dependencies
curl -L -o janino-3.1.12.jar https://repo1.maven.org/maven2/org/codehaus/janino/janino/3.1.12/janino-3.1.12.jar
curl -L -o commons-compiler-3.1.12.jar https://repo1.maven.org/maven2/org/codehaus/janino/commons-compiler/3.1.12/commons-compiler-3.1.12.jar
curl -L -o jboss-logging-3.6.1.Final.jar https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.6.1.Final/jboss-logging-3.6.1.Final.jar
curl -L -o jandex-3.2.0.jar https://repo1.maven.org/maven2/io/smallrye/jandex/3.2.0/jandex-3.2.0.jar
curl -L -o classmate-1.7.0.jar https://repo1.maven.org/maven2/com/fasterxml/classmate/1.7.0/classmate-1.7.0.jar
curl -L -o byte-buddy-1.17.7.jar https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.17.7/byte-buddy-1.17.7.jar
curl -L -o antlr4-runtime-4.13.0.jar https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.13.0/antlr4-runtime-4.13.0.jar
curl -L -o jaxb-runtime-4.0.5.jar https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-runtime/4.0.5/jaxb-runtime-4.0.5.jar
curl -L -o jaxb-core-4.0.5.jar https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-core/4.0.5/jaxb-core-4.0.5.jar
curl -L -o angus-activation-2.0.2.jar https://repo1.maven.org/maven2/org/eclipse/angus/angus-activation/2.0.2/angus-activation-2.0.2.jar
curl -L -o txw2-4.0.5.jar https://repo1.maven.org/maven2/org/glassfish/jaxb/txw2/4.0.5/txw2-4.0.5.jar
curl -L -o istack-commons-runtime-4.1.2.jar https://repo1.maven.org/maven2/com/sun/istack/istack-commons-runtime/4.1.2/istack-commons-runtime-4.1.2.jar

REM Logging dependencies
curl -L -o logback-classic-1.5.18.jar https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.5.18/logback-classic-1.5.18.jar
curl -L -o logback-core-1.5.18.jar https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.5.18/logback-core-1.5.18.jar
curl -L -o slf4j-api-2.0.17.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar
curl -L -o jul-to-slf4j-2.0.17.jar https://repo1.maven.org/maven2/org/slf4j/jul-to-slf4j/2.0.17/jul-to-slf4j-2.0.17.jar
curl -L -o log4j-to-slf4j-2.24.3.jar https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-to-slf4j/2.24.3/log4j-to-slf4j-2.24.3.jar
curl -L -o log4j-api-2.24.3.jar https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3/log4j-api-2.24.3.jar
REM Servlet & Tomcat dependencies
curl -L -o jakarta-servlet-api-6.0.0.jar https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar
curl -L -o tomcat-embed-core-10.1.44.jar https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/10.1.44/tomcat-embed-core-10.1.44.jar
curl -L -o tomcat-embed-el-10.1.44.jar https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-el/10.1.44/tomcat-embed-el-10.1.44.jar
curl -L -o tomcat-embed-websocket-10.1.44.jar https://repo1.maven.org/maven2/org/apache/tomcat/embed/tomcat-embed-websocket/10.1.44/tomcat-embed-websocket-10.1.44.jar
REM JPA & Hibernate dependencies
curl -L -o hibernate-core-6.6.26.Final.jar https://repo1.maven.org/maven2/org/hibernate/orm/hibernate-core/6.6.26.Final/hibernate-core-6.6.26.Final.jar
curl -L -o hibernate-commons-annotations-7.0.3.Final.jar https://repo1.maven.org/maven2/org/hibernate/common/hibernate-commons-annotations/7.0.3.Final/hibernate-commons-annotations-7.0.3.Final.jar
curl -L -o jakarta-persistence-api-3.1.0.jar https://repo1.maven.org/maven2/jakarta/persistence/jakarta.persistence-api/3.1.0/jakarta.persistence-api-3.1.0.jar
curl -L -o jakarta-transaction-api-2.0.1.jar https://repo1.maven.org/maven2/jakarta/transaction/jakarta.transaction-api/2.0.1/jakarta.transaction-api-2.0.1.jar
curl -L -o jboss-logging-3.6.1.Final.jar https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.6.1.Final/jboss-logging-3.6.1.Final.jar
curl -L -o antlr4-runtime-4.13.0.jar https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.13.0/antlr4-runtime-4.13.0.jar
curl -L -o classmate-1.7.0.jar https://repo1.maven.org/maven2/com/fasterxml/classmate/1.7.0/classmate-1.7.0.jar
curl -L -o byte-buddy-1.17.7.jar https://repo1.maven.org/maven2/net/bytebuddy/byte-buddy/1.17.7/byte-buddy-1.17.7.jar
curl -L -o jandex-3.2.0.jar https://repo1.maven.org/maven2/io/smallrye/jandex/3.2.0/jandex-3.2.0.jar