# npm --prefix ujcms-starter/src/main/webapp/themes/1/default ci
# npm --prefix ujcms-starter/src/main/webapp/themes/1/default run build:vendor
# pnpm --dir ujcms-ui-console install
# pnpm --dir ujcms-ui-console build:starter
# ./mvnw clean package -DskipTests
# docker build -t ujcms:x.x.x .

# 构建阶段只做与平台无关的文件处理（拆分 jar、整理 webapp），固定在构建机的原生平台上执行：
# 多平台构建时不必在 QEMU 模拟下运行 JVM，各平台镜像共用同一份产物
FROM --platform=$BUILDPLATFORM bellsoft/liberica-openjre-debian:25-cds AS builder

WORKDIR /builder
ARG JAR_FILE=ujcms-starter/target/*.jar
COPY ${JAR_FILE} ujcms.jar
RUN java -Djarmode=tools -jar ujcms.jar extract --layers --destination extracted

COPY ujcms-starter/src/main/webapp/ webapp/
# 去掉仅 war 部署使用的 WEB-INF/*.xml；写入时间戳，用于判断 cp 文件是否需要更新
RUN rm -f webapp/WEB-INF/*.xml && date +%s > webapp/cp/.timestamp


FROM bellsoft/liberica-openjre-debian:25-cds

# 安装 procps(debian 需安装，ubuntu 不需要)。否则没有 ps 命令，jodconverter 无法启动
# 安装 libreoffice。文库的 Office 转 PDF 及 .wps/.rtf/.odt 导入需要；.docx/.doc 导入由内置引擎处理，不需要它
# 安装 default-mysql-client(ubuntu 可安装 mysql-client)。如需使用数据库备份功能，则需要安装
# 安装开源字体，供 LibreOffice 转换中文文档：fonts-noto-cjk（思源黑体 / 宋体，SIL OFL）；fonts-liberation2（Liberation 2.x，
# SIL OFL；1.x 的 fonts-liberation 是 GPL 加字体例外，不用）、fonts-crosextra-carlito（SIL OFL）、fonts-crosextra-caladea
#（Apache-2.0）与 Arial / Times New Roman / Courier New / Calibri / Cambria 度量兼容，fontconfig 自带规则会自动替换；
# 宋体、微软雅黑等中文字体名的替换规则见 docker/fontconfig/。
# 不要把 Windows / Office 自带的字体复制进镜像：它们的许可不允许随镜像再分发。部署方若持有这些字体的许可，
# 可把字体目录挂载到容器的 /usr/share/fonts/ 下使用（属部署方自己的许可范围）
#RUN apt-get update && apt-get --no-install-recommends install -y procps libreoffice default-mysql-client fontconfig fonts-noto-cjk fonts-liberation2 fonts-crosextra-carlito fonts-crosextra-caladea fonts-dejavu-core && rm -rf /var/lib/apt/lists/*

# 也可安装国内镜像源后，再安装上述软件包
# bullseye 及之前为 /etc/apt/sources.list，bookworm 及之后为 /etc/apt/sources.list.d/debian.sources
# 阿里云镜像源：mirrors.aliyun.com/debian
# 腾讯云镜像源：mirrors.cloud.tencent.com/debian
#RUN sed -e 's|deb.debian.org/debian|mirrors.cloud.tencent.com/debian|g' \
#   -i /etc/apt/sources.list.d/debian.sources \
#   && apt-get update && apt-get --no-install-recommends install -y procps libreoffice default-mysql-client fontconfig fonts-noto-cjk fonts-liberation2 fonts-crosextra-carlito fonts-crosextra-caladea fonts-dejavu-core && rm -rf /var/lib/apt/lists/*

# 中文字体名别名：文档里的 宋体 / 微软雅黑 / 黑体 / 楷体 / 仿宋 映射到思源字体，LibreOffice 转换时不再缺字
COPY docker/fontconfig/60-ujcms-cjk-aliases.conf /etc/fonts/conf.d/60-ujcms-cjk-aliases.conf

# 基础镜像的时区为 UTC，而数据库驱动按 JVM 时区读写时间、模板按 JVM 默认时区格式化时间：
# 不设置时前台显示和写入数据库的时间都比北京时间早 8 小时，定时任务与日志时间同样按 UTC。其它时区可在运行时以 -e TZ=... 覆盖
#（须与数据库所在的业务时区一致）
ENV TZ=Asia/Shanghai

# 使用非 root 用户运行容器，可能因权限问题，无法访问挂载目录
#USER 1000:1000
WORKDIR /ujcms

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# 第三方软件声明随镜像分发：NOTICE 与 licenses/（源码级），
# ujcms.jar 内的 META-INF/THIRD-PARTY.txt（Java 依赖清单），/usr/src/ujcms/cp/THIRD-PARTY-LICENSES.txt
#（控制台打包进的前端依赖），bom.json（CycloneDX 软件物料清单，即 jar 内 META-INF/sbom/application.cdx.json 的副本）
COPY NOTICE ./NOTICE
COPY licenses/ ./licenses/
COPY ujcms-starter/target/classes/META-INF/sbom/application.cdx.json ./bom.json

COPY docker/application.yaml ./config/application.yaml
# 将初始文件拷贝至 /usr/src/ujcms，再由初始化脚本复制到 /ujcms/static，避免文件内容被挂载目录覆盖
COPY --from=builder /builder/webapp/ /usr/src/ujcms/

VOLUME ["/ujcms/static"]
EXPOSE 8080

COPY --chmod=755 docker/docker-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["docker-entrypoint.sh"]
# 类路径：ujcms.jar（其清单引用 lib/ 下的依赖）加 ext/*.jar。ext/ 供部署方追加发行包没有的 JDBC 驱动（如人大金仓、
# openGauss、海量数据库）：把 jar 挂载到 /ujcms/ext/ 即可，不必重建镜像。MySQL / MariaDB 用镜像内置的 MariaDB Connector/J。
# 覆盖 command 时请保留 -cp 写法
CMD ["java", "-cp", "ujcms.jar:ext/*", "com.ujcms.cms.starter.Application"]
