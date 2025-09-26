# $ docker build -t ujcms/ujcms:x.x.x .

#FROM eclipse-temurin:11-jre-noble AS builder
FROM eclipse-temurin:17-jre-noble AS builder

WORKDIR /ujcms
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} ujcms.jar
RUN java -Djarmode=layertools -jar ujcms.jar extract

#FROM eclipse-temurin:11-jre-noble
FROM eclipse-temurin:17-jre-noble

# 安装 libreoffice。如开启 doc 导入及文库功能，则需要安装
# 安装 mysql-client。如需使用数据库备份功能，则需要安装
#RUN apt-get update && apt-get --no-install-recommends install -y libreoffice mysql-client && rm -rf /var/lib/apt/lists/*

# 也可安装国内镜像源后，再安装 libreoffice mysql-client
# 22.04 及之前为 /etc/apt/sources.list，24.04 及之后为 /etc/apt/sources.list.d/ubuntu.sources
# 阿里云镜像源：mirrors.aliyun.com/ubuntu
# 腾讯云镜像源：mirrors.cloud.tencent.com/ubuntu
#RUN sed -e 's|archive.ubuntu.com/ubuntu|mirrors.cloud.tencent.com/ubuntu|g' \
#   -e 's|security.ubuntu.com/ubuntu|mirrors.cloud.tencent.com/ubuntu|g' \
#   -i /etc/apt/sources.list.d/ubuntu.sources \
#   && apt-get update && apt-get --no-install-recommends install -y libreoffice mysql-client && rm -rf /var/lib/apt/lists/*

# 文库功能 docx 转 pdf 时如缺少字体，可复制相应字体到镜像中。常用字体通常可在 C:\Windows\Fonts 中找到
#COPY docker/fonts/* /usr/share/fonts/zh-cn/

# 使用非 root 用户运行容器，可能因权限问题，无法访问挂载目录
#USER 1000:1000
WORKDIR /ujcms

COPY --from=builder ujcms/dependencies/ ./
COPY --from=builder ujcms/spring-boot-loader/ ./
COPY --from=builder ujcms/snapshot-dependencies/ ./
COPY --from=builder ujcms/application/ ./

COPY --from=builder ujcms/application/BOOT-INF/classes/application-docker.yaml ./BOOT-INF/classes/config/application.yaml
# 将初始文件拷贝至 /usr/src/ujcms，再由初始化脚本复制到 /ujcms/static，避免文件内容被挂载目录覆盖
COPY src/main/webapp/ /usr/src/ujcms/

RUN rm -f /usr/src/ujcms/WEB-INF/*.xml; \
# 写入时间戳，用于判断 cp 文件是否需要更新
    date +%s > /usr/src/ujcms/cp/.timestamp;

VOLUME ["/ujcms/static"]
EXPOSE 8080

COPY --chmod=755 docker/docker-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["java", "org.springframework.boot.loader.JarLauncher"]
