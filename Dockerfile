# $ docker build -t ujcms:x.x.x --build-arg JAR_FILE=target/ujcms-x.x.x.jar .

FROM eclipse-temurin:8-jre as builder
WORKDIR ujcms
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} ujcms.jar
RUN java -Djarmode=layertools -jar ujcms.jar extract

FROM eclipse-temurin:8-jre
WORKDIR ujcms
COPY --from=builder ujcms/dependencies/ ./
COPY --from=builder ujcms/spring-boot-loader/ ./
COPY --from=builder ujcms/snapshot-dependencies/ ./
COPY --from=builder ujcms/application/ ./

COPY --from=builder ujcms/application/BOOT-INF/classes/application-docker.yaml ./BOOT-INF/classes/config/application.yaml
COPY src/main/webapp/ ./static/
#COPY static/ ./static/

#VOLUME ["/ujcms/static", "/ujcms/config", "/ujcms/BOOT-INF/classes/license"]
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
