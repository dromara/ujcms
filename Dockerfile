# $ docker build -t ujcms:x.x.x --build-arg JAR_FILE=target/ujcms-x.x.x.jar .

#FROM eclipse-temurin:11-jre AS builder
#FROM eclipse-temurin:17-jre AS builder
#FROM eclipse-temurin:17.0.14_7-jre-alpine AS builder
FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:17-ubuntu AS builder

WORKDIR /ujcms
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} ujcms.jar
RUN java -Djarmode=layertools -jar ujcms.jar extract

FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:17-ubuntu
WORKDIR /ujcms
COPY --from=builder ujcms/dependencies/ ./
COPY --from=builder ujcms/spring-boot-loader/ ./
COPY --from=builder ujcms/snapshot-dependencies/ ./
COPY --from=builder ujcms/application/ ./

COPY --from=builder ujcms/application/BOOT-INF/classes/application-docker.yaml ./BOOT-INF/classes/config/application.yaml
COPY src/main/webapp/ ./static/

#VOLUME ["/ujcms/static", "/ujcms/config", "/ujcms/BOOT-INF/classes/license"]
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
