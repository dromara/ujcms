# $ docker build -t ujcms:x.x.x .

FROM bellsoft/liberica-openjre-debian:24-cds AS builder

WORKDIR /builder
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} ujcms.jar
RUN java -Djarmode=tools -jar ujcms.jar extract --layers --destination extracted
COPY src/main/resources/application-docker.yaml ./extracted/application/config/application.yaml


FROM bellsoft/liberica-openjre-debian:24-cds

# 安装 procps(debian 需安装，ubuntu 不需要)。否则没有 ps 命令，jodconverter 无法启动
# 安装 libreoffice。如开启 doc 导入及文库功能，则需要安装
# 安装 default-mysql-client(ubuntu 可安装 mysql-client)。如需使用数据库备份功能，则需要安装
#RUN apt-get update && apt-get --no-install-recommends install -y procps libreoffice default-mysql-client && rm -rf /var/lib/apt/lists/*

# 也可安装国内镜像源后，再安装 libreoffice default-mysql-client
# bullseye 及之前为 /etc/apt/sources.list，bookworm 及之后为 /etc/apt/sources.list.d/debian.sources
# 阿里云镜像源：mirrors.aliyun.com/debian
# 腾讯云镜像源：mirrors.cloud.tencent.com/debian
#RUN sed -e 's|deb.debian.org/debian|mirrors.cloud.tencent.com/debian|g' \
#   -i /etc/apt/sources.list.d/debian.sources \
#   && apt-get update && apt-get --no-install-recommends install -y procps libreoffice default-mysql-client && rm -rf /var/lib/apt/lists/*

# 文库功能 docx 转 pdf 时如缺少字体，可复制相应字体到镜像中。常用字体通常可在 C:\Windows\Fonts 中找到
#COPY docker/fonts/* /usr/share/fonts/zh-cn/

# 使用非 root 用户运行容器，可能因权限问题，无法访问挂载目录
#USER 1000:1000
WORKDIR /ujcms

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

COPY src/main/resources/application-docker.yaml ./config/application.yaml
# 将初始文件拷贝至 /usr/src/ujcms，再由初始化脚本复制到 /ujcms/static，避免文件内容被挂载目录覆盖
COPY src/main/webapp/ /usr/src/ujcms/

RUN rm -f /usr/src/ujcms/WEB-INF/*.xml; \
# 写入时间戳，用于判断 cp 文件是否需要更新
    date +%s > /usr/src/ujcms/cp/.timestamp;

VOLUME ["/ujcms/static"]
EXPOSE 8080

COPY --chmod=755 docker/docker-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["java", "-jar", "ujcms.jar"]
