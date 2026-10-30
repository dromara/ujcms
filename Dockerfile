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
# 安装 zip、unzip。zip 供下方修正 lib/libreoffice-*.jar 的清单（zip -d）；unzip 供入口脚本的主题同步（UJCMS_THEME_SYNC，见
# ENTRYPOINT 上方注释）解包主题包
# 安装开源字体，供 LibreOffice 转换中文文档：fonts-noto-cjk（思源黑体 / 宋体，SIL OFL）；fonts-liberation2（Liberation 2.x，
# SIL OFL；1.x 的 fonts-liberation 是 GPL 加字体例外，不用）、fonts-crosextra-carlito（SIL OFL）、fonts-crosextra-caladea
#（Apache-2.0）与 Arial / Times New Roman / Courier New / Calibri / Cambria 度量兼容，fontconfig 自带规则会自动替换；
# 宋体、微软雅黑等中文字体名的替换规则见 docker/fontconfig/。
# 不要把 Windows / Office 自带的字体复制进镜像：它们的许可不允许随镜像再分发。部署方若持有这些字体的许可，
# 可把字体目录挂载到容器的 /usr/share/fonts/ 下使用（属部署方自己的许可范围）
#RUN apt-get update && apt-get --no-install-recommends install -y procps libreoffice default-mysql-client fontconfig fonts-noto-cjk fonts-liberation2 fonts-crosextra-carlito fonts-crosextra-caladea fonts-dejavu-core zip unzip && rm -rf /var/lib/apt/lists/*

# 也可安装国内镜像源后，再安装上述软件包
# bullseye 及之前为 /etc/apt/sources.list，bookworm 及之后为 /etc/apt/sources.list.d/debian.sources
# 阿里云镜像源：mirrors.aliyun.com/debian
# 腾讯云镜像源：mirrors.cloud.tencent.com/debian
#RUN sed -e 's|deb.debian.org/debian|mirrors.cloud.tencent.com/debian|g' \
#   -i /etc/apt/sources.list.d/debian.sources \
#   && apt-get update && apt-get --no-install-recommends install -y procps libreoffice default-mysql-client fontconfig fonts-noto-cjk fonts-liberation2 fonts-crosextra-carlito fonts-crosextra-caladea fonts-dejavu-core zip unzip && rm -rf /var/lib/apt/lists/*

# 中文字体名别名：文档里的 宋体 / 微软雅黑 / 黑体 / 楷体 / 仿宋 映射到思源字体，LibreOffice 转换时不再缺字
COPY docker/fontconfig/60-ujcms-cjk-aliases.conf /etc/fonts/conf.d/60-ujcms-cjk-aliases.conf

# 基础镜像的时区为 UTC，而数据库驱动按 JVM 时区读写时间、模板按 JVM 默认时区格式化时间：
# 不设置时前台显示和写入数据库的时间都比北京时间早 8 小时，定时任务与日志时间同样按 UTC。其它时区可在运行时以 -e TZ=... 覆盖
#（须与数据库所在的业务时区一致）
ENV TZ=Asia/Shanghai

# glibc 默认每个线程一个 malloc arena（64M 一块），Java 容器的本机内存会无故多出上百 MB；限制为 2 个，对性能无可感影响
ENV MALLOC_ARENA_MAX=2

# 使用非 root 用户运行容器，可能因权限问题，无法访问挂载目录
#USER 1000:1000
WORKDIR /ujcms

COPY --from=builder /builder/extracted/dependencies/ ./
# jodconverter-local-lo 自带的 LibreOffice UNO 包 lib/libreoffice-*.jar，其清单 Class-Path 为 "java_websocket.jar unoloader.jar ../"：
# 前两项在 lib/ 下本就不存在，"../" 指 LibreOffice 安装目录的 program/（这里用不上），却把整个 /ujcms 目录挂上了类路径
#（config/、static/ 都成了类路径资源），JVM 也因此拒绝生成 CDS 归档（"Error: non-empty directory"）。整个删掉清单即可
#（jar 不需要清单也能加载）。开源版镜像没有这个 jar，循环为空操作
RUN for j in lib/libreoffice-*.jar; do [ -e "$j" ] || continue; zip -q -d "$j" META-INF/MANIFEST.MF; done
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

# JVM 类数据共享（CDS）归档目录：CMD 的 -XX:+AutoCreateSharedArchive 让 JVM 在首次正常退出（docker stop）时把已加载的类归档到
# 这里，之后启动快约 1/3；JDK 或类路径变化时自动重建，不需要构建期训练。写在容器可写层即可（restart 不丢，重建容器才丢）；
# 想跨容器重建保留就把卷挂到 /ujcms/cds。目录 1777：以非 root 用户（USER 1000:1000）运行时也能写
RUN mkdir -m 1777 /ujcms/cds

VOLUME ["/ujcms/static"]
EXPOSE 8080

# 挂载目录 /ujcms/static 里的主题（themes/**）属站点数据：换镜像时入口脚本只迁移控制台 cp/，不覆盖主题，控制台里改过的模板
# 得以保留——新版本带来的模板改动也就到不了已安装的站点。演示 / 测试站可设环境变量 UJCMS_THEME_SYNC=true：换镜像后的首次启动，
# 入口脚本把镜像内置的主题目录（/usr/src/ujcms/themes/<站点ID>/<主题名> 与 themes/error）和主题包（UJCMS_INSTALL_LIBRARYDIR 下的
# *.zip，按包内 themeName 匹配各站点已安装的同名实例）整目录覆盖到 static/themes 下，模板随发版更新；不动数据库与 uploads/，
# 也不更新主题包库里的存量包。正式站勿开。强制重同步：删掉挂载目录里的 cp/.timestamp 再重启
COPY --chmod=755 docker/docker-entrypoint.sh /usr/local/bin/

ENTRYPOINT ["docker-entrypoint.sh"]
# 类路径：ujcms.jar（其清单引用 lib/ 下的依赖）加 ext/*.jar。ext/ 供部署方追加发行包没有的 JDBC 驱动（如人大金仓、
# openGauss、海量数据库）：把 jar 挂载到 /ujcms/ext/ 即可，不必重建镜像。MySQL / MariaDB 用镜像内置的 MariaDB Connector/J。
# JVM 参数（覆盖 command 时请保留 -cp 写法）：
#   -XX:+UseCompactObjectHeaders  紧凑对象头（JDK 25 正式特性），堆内存少 10%～20%，速度持平或略快
#   -XX:+ExitOnOutOfMemoryError   OOM 直接退出，交给 restart 策略拉起，不留下半死的进程
#   -XX:+AutoCreateSharedArchive  CDS 归档自动创建 / 复用（见上方 /ujcms/cds 注释）
# 堆大小不在这里定：给容器设内存上限（compose 的 deploy.resources.limits.memory 或 docker run -m），JVM 缺省取上限的 25% 作堆；
# 要改就以环境变量 JAVA_TOOL_OPTIONS 追加，如 -Xmx768m，或 -XX:MaxRAMPercentage=50（容器里只有本程序时 50% 合适）。
# 小内存 / 少核机器（≤2 核、堆 ≤1G）建议再加 -XX:+UseSerialGC：比默认的 G1 省约 10%～15% 堆外内存与两条 GC 线程，停顿仍在几十毫秒。
# 容器日志里看 GC：-Xlog:gc:stdout，每次 GC 一行，箭头后的数字即存活集，-Xmx 取它的 2～2.5 倍。
CMD ["java", "-XX:+UseCompactObjectHeaders", "-XX:+ExitOnOutOfMemoryError", "-XX:+AutoCreateSharedArchive", "-XX:SharedArchiveFile=/ujcms/cds/ujcms.jsa", "-cp", "ujcms.jar:ext/*", "com.ujcms.cms.starter.Application"]
