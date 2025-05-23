# $ docker build -t ujcms/ujcms:x.x.x .

# 如出现镜像无法拉取的情况，可以使用阿里云的dragonwell镜像
#FROM eclipse-temurin:11-jre AS builder
#FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:17-ubuntu AS builder
FROM eclipse-temurin:17-jre AS builder

WORKDIR /ujcms
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} ujcms.jar
RUN java -Djarmode=layertools -jar ujcms.jar extract

# 如出现镜像无法拉取的情况，可以使用阿里云的dragonwell镜像
#FROM dragonwell-registry.cn-hangzhou.cr.aliyuncs.com/dragonwell/dragonwell:17-ubuntu
FROM eclipse-temurin:17-jre
USER www-data
WORKDIR /ujcms

COPY --from=builder --chown=www-data:www-data ujcms/dependencies/ ./
COPY --from=builder --chown=www-data:www-data ujcms/spring-boot-loader/ ./
COPY --from=builder --chown=www-data:www-data ujcms/snapshot-dependencies/ ./
COPY --from=builder --chown=www-data:www-data ujcms/application/ ./

COPY --from=builder --chown=www-data:www-data ujcms/application/BOOT-INF/classes/application-docker.yaml ./BOOT-INF/classes/config/application.yaml
# 将初始文件拷贝至 /usr/src/ujcms，再由初始化脚本复制到 /ujcms/static，避免容器启动时文件被覆盖
COPY --chown=www-data:www-data src/main/webapp/ /usr/src/ujcms/
RUN rm /usr/src/ujcms/WEB-INF/*.xml; \
# 写入时间戳，用于判断 cp 文件是否需要更新
    date +%s > /usr/src/ujcms/cp/.timestamp;

VOLUME ["/ujcms/static"]

EXPOSE 8080
COPY docker/docker-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["java", "org.springframework.boot.loader.JarLauncher"]
