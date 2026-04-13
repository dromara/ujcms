# UJCMS

UJCMS 是一款基于 Java 的开源企业级网站内容管理系统（Java CMS），采用 Spring Boot、MyBatis、Spring Security、Vue 3、Vite、Element Plus 等技术开发，支持 `无头 CMS` 架构。系统提供 `模板 + 标签` 和 `Vue（React）+ API` 两种开发模式，分别适用于传统模板渲染与现代化前后端分离场景，为网站的建设、管理与维护提供高效、灵活、可靠的解决方案。

## 资源地址

* 官方网站：[https://www.ujcms.com](https://www.ujcms.com)
* 官方文档：[https://www.ujcms.com/docs/](https://www.ujcms.com/docs/)
* 安装包下载：[https://www.ujcms.com/downloads/](https://www.ujcms.com/downloads/)

## 演示站地址

* 演示站前台：[https://demo.ujcms.com](https://demo.ujcms.com)
* 演示站后台：[https://demo.ujcms.com/cp/](https://demo.ujcms.com/cp/)

## 环境要求

> 尽量不要使用小版本号太低的软件，可能会导致程序无法正常启动。例如 MySQL 8.0.12 会报错，请升级到 MySQL 8.0.20+。

**后端**

* JDK 17 或 21
* MySQL 8.0 (5.7.22+, 8.0.20+)
* Tomcat 10.1+ (仅 WAR 部署时需要)

**前端**

* Node 22.12+
* 可设置淘宝 npm 镜像加速：`npm config set registry https://registry.npmmirror.com/`
* pnpm（如未安装，执行 `npm install -g pnpm`）

## 创建数据库

1. 创建数据库。数据库名可为 `ujcms` 或其它任何名称。MySQL 字符集选 `utf8mb4`（不要使用 `utf8`，该字符集可能导致某些特殊字符出现乱码）。
2. 无需手动执行 SQL 文件：程序启动时会自动创建表及初始化数据；以后升级也同样不需要手动执行 SQL 升级脚本，会自动判断版本并进行表结构升级。
3. 确保数据库账号具备创建表、写入数据与更新结构的权限。

## 启动程序

> 注意：不要将程序放在有中文或空格的路径下。

在 IntelliJ IDEA 中点击 `File` - `Open`，选择项目文件夹（有 `pom.xml` 的文件夹）。会开始从 maven 服务器下载第三方 jar 包（如 spring 等），需要一定时间，请耐心等待。

### 配置数据库连接

打开 `ujcms-starter/src/main/resources/application.yaml`，修改：

* `spring.datasource.url`：数据库连接地址
* `spring.datasource.username`：数据库用户名
* `spring.datasource.password`：数据库密码

### 构建前台模板静态资源

安装并构建前台模板所需的第三方前端库（Bootstrap 等），用于生成前台模板的静态资源。在项目根目录执行：

```bash
pnpm --dir ujcms-starter install
pnpm --dir ujcms-starter build
```

### 编译后台前端（管理控制台）

将后台前端编译到 `ujcms-starter/src/main/webapp/cp`，以便直接访问（可不必启动后台前端）：

```bash
pnpm --dir ujcms-ui-console install
pnpm --dir ujcms-ui-console build:starter
```

### 启动命令

在项目根目录执行（不要进入 ujcms-starter 目录执行，否则会报错）：

```bash
./mvnw clean spring-boot:run
```

## 确认启动
首次运行程序，系统会自动创建数据库表和初始化数据库，需要一些时间，请耐心等待。直到出现类似 `com.ujcms.cms.starter.Application: Started Application in xxx seconds` 信息，代表程序启动完成。

## 访问程序

* 网站前台：`http://localhost:8080/`
* 网站后台：`http://localhost:8080/cp`
* 默认管理员账号：`admin`，密码：`password`

> 请在首次登录后尽快修改默认密码，并妥善保管管理员账号。

使用手机访问前台或者使用浏览器模拟手机访问前台，会自适应显示手机端的界面。

> 如使用 IntelliJ IDEA 的 `Run` 方式启动，则需要在 `Edit configurations...` 中点击 `Modify options`，勾选 `Working directory`，选择 `ujcms-starter` 项目作为工作路径，否则访问前台会出现找不到模板的错误。

> 如遇到前台页面没有样式的情况，请 `安装前台模板依赖`。如果依然没有样式，则是因为程序没有部署在 Tomcat 的根目录。如前台首页地址类似为 `http://localhost:8080/abc` ，即代表部署在 `/abc` 目录下，没有部署在根目录。解决办法请参考下面 `关于部署路径` 部分内容。

> 如程序无法正常编译，通常是因为 maven 没有正确下载 jar 依赖包。可以尝试在 IntelliJ IDEA 的 `Maven` 窗口点击刷新按钮 `Reload All Maven Projects` 按钮，尝试重新下载 jar 依赖包，或者点击菜单 `Build` - `Rebuild Project` 重新编译项目。

> 如首次使用 IntelliJ IDEA，没有配置 JDK，也会导致无法正常程序不能编译。可选中项目，点击 `File` - `Project Structure...`，在 `Project Settings - Project` 处，配置 `Project SDK`。

## 常见错误

程序启动时出现 `LockException: Could not acquire change log lock` 或 `Waiting for changelog lock...` 错误信息。解决办法：将数据库 `databasechangeloglock` 和 `flw_ev_databasechangeloglock` 表中数据清空（**请注意**，不是 `databasechangelog` 和 `flw_ev_databasechangelog` 表），或者删除数据库所有表（也可重建数据库），再次启动程序即可。原因：程序启动时会自动创建、升级数据库表结构，如果此时强行关闭程序，再次启动可能会出现这个问题。

程序启动时出现 `Specified key was too long; max key length is 767 bytes` 错误信息。解决办法：设置 MySQL 参数 `innodb_large_prefix=ON`。原因：从 MySQL 5.7.7 开始，`innodb_large_prefix` 参数默认已设置为 `ON`，但第三方面板或云服务商提供的 MySQL 可能修改默认配置。

程序启动时出现 `java.io.IOException: Problem reading font data` 错误信息。解决办法：安装 FontConfig 组件，执行 `yum install -y fontconfig`（CentOS 系列） 或 `apt-get install -y fontconfig`（Ubuntu 系列）。原因：系统没有安装字体组件，导致验证码生成组件无法读取字体。

## 前台模板

网站前台模板使用 `Freemarker` 作为模板引擎。通过修改模板文件，可以完全控制网站页面显示的内容。模板位置：

* 源码位置：`ujcms-starter/src/main/webapp/templates`
* war 部署位置：`templates`
* jar 部署位置：`static/templates`

## war 部署

**编译后台前端**

将后台前端编译到 `ujcms-starter/src/main/webapp/cp`：

```bash
pnpm --dir ujcms-ui-console install
pnpm --dir ujcms-ui-console build:starter
```

**Maven 打包**

在项目根目录运行：

```bash
./mvnw -P war clean package
```

会生成以下文件及文件夹：

* `ujcms-starter/target/ujcms-***.war` 文件
* `ujcms-starter/target/ujcms-***/` 文件夹

如使用目录方式部署，可将 `ujcms-starter/target/ujcms-***/` 目录下文件复制到 tomcat 的 `webapps/ROOT` 目录下（请先删除原 `tomcat/webapps` 目录下所有内容。如有重要文件，请先备份）。

**目录结构**

最后在 Tomcat 里完整的目录结构大致如下：

* `webapps/ROOT/cp/...`
* `webapps/ROOT/cp/assets/...`
* `webapps/ROOT/cp/index.html`
* `webapps/ROOT/templates/...`
* `webapps/ROOT/uploads/...`
* `webapps/ROOT/WEB-INF/...`

如使用 war 包部署，可将 `ujcms-***.war` 更名为 `ROOT.war`（注意大小写），复制到 tomcat 的 `webapps` 目录下（请先删除原 `tomcat/webapps` 目录下所有内容。如有重要文件，请先备份）。复制完成后目录结构如下：

* `webapps/ROOT.war`

war 部署要使用解压模式，tomcat 默认就是解压模式。如 tomcat 不会自动解压，请检查 `tomcat/conf/server.xml` 配置文件中 `<Host ... unpackWARs="true"... >` 配置项。

## jar 部署

**编译后台前端**

将后台前端编译到 `ujcms-starter/src/main/webapp/cp`：

```bash
pnpm --dir ujcms-ui-console install
pnpm --dir ujcms-ui-console build:starter
```

**Maven 打包**

在项目根目录运行：

```bash
./mvnw -P jar clean package
```

将打包的 `ujcms-starter/target/ujcms-***.jar` 文件复制到部署目录，并新建文件夹 `static`。

将 `ujcms-starter/src/main/webapp` 目录下所有文件复制到 `static` 目录下。

还可将 `ujcms-starter/src/main/resources/application.yaml` 配置文件复制到 `config` 目录。该配置文件将覆盖 `ujcms-***.jar` 内的原始配置（位于`BOOT-INF\classes\application.yaml`）。更新配置时，可避免修改 jar 包内的配置文件。

**目录结构**

完整目录结构大致如下：

* `ujcms-***.jar`
* `config/application.yaml`
* `static/cp/...`
* `static/cp/assets/...`
* `static/cp/index.html`
* `static/templates/...`
* `static/uploads/...`
* `static/WEB-INF/...`

在部署目录（即 `ujcms-***.jar` 所在目录）运行 `java -jar ujcms-***.jar` 命令，即可启动。

## 关于部署路径

程序通常部署在 Tomcat 根目录，首页访问地址类似于：

* `http://www.mysite.com/`
* `http://localhost/`
* `http://localhost:8080/`

然而，在某些特殊情况下（例如在 Eclipse 中默认启动 Tomcat 时），应用程序可能会被部署到某个子路径下，首页的访问地址类似于：

* `http://www.mysite.com/ujcms/`
* `http://localhost/ujcms/`
* `http://localhost:8080/ujcms/`

后台地址为：

* `http://www.mysite.com/ujcms/cp/`
* `http://localhost/ujcms/cp/`
* `http://localhost:8080/ujcms/cp/`

在这种情况下，网站前台可能会出现样式无法正常显示的问题。可以到后台 `配置 - 全局设置` 中设置 `上下文路径`，例如 `/ujcms`。其中斜杠 `/` 不能省略，`ujcms` 为部署路径。在开发环境中，部署路径通常为项目名称。

在开发环境中，建议尽量避免使用上下文路径，除非正式部署时也需要将应用程序部署到相同的路径下。否则，在开发环境中上传的图片在部署到正式环境时，可能无法正常显示。这是因为上传的图片地址会包含上下文路径信息（例如：`/ujcms/uploads/...`）。

Eclipse 默认的 Tomcat 启动方式会将应用程序部署到一个特定的临时目录。因此，上传的图片（包括通过系统后台新增和修改的模板）会保存在这个临时部署目录中，而不是程序所在的目录。当修改了 Eclipse 中的源代码或文件时，系统会自动重新部署应用程序，导致之前上传的图片被清空。如果在开发环境中发现上传的图片突然丢失，很可能就是这个原因。

## 关闭表结构自动升级

程序在启动时会检查并升级数据库表结构版本，如果此时强行关闭程序，再次启动时会出现 `LockException: Could not acquire change log lock` 或 `Waiting for changelog lock...` 错误。如果您受此问题困扰，可在首次启动程序后（已成功创建表结构和初始数据），关闭表结构升级功能。

打开 `application.yaml`（源码位置：`ujcms-starter/src/main/resources/application.yaml`，war 位置：`WEB-INF/classes/application.yaml`）文件，修改以下选项：

```yaml
# 关闭 liquibase 的表结构升级功能
spring.liquibase.enabled: false
# 关闭 flowable 的表结构升级功能
flowable.database-schema-update: none
# 关闭执行初始化脚本
ujcms.data-sql-enabled: false
```

程序升级时，需重新开启以上配置，否则数据库表结构无法自动升级。

## MySQL 表名大小写

如果在 Windows 环境使用 MySQL，且以后需要迁移到 Linux 环境的 MySQL，建议将 Windows 环境的 MySQL 配置为表名大小写敏感模式。

因为 Linux 环境下 MySQL 的表名是大小写敏感的；而在 Windows 环境下 MySQL 表名大小写不敏感，且会自动把大写的表名改为小写的表名。从 Windows 向 Linux 迁移数据时，本为大写表名的成为了小写表名，导致程序出错。需要手动把小写表名改回大写表名，费时费力且容易出错。

UJCMS 系统的表名都为小写，不管在 Windows 还是 Linux 下都没有问题，但第三方类库（如 Liquibase、Flowable、Quartz 等）创建的表可能为大写表名。所以在 Windows 环境下也把 MySQL 设置成表名大小写敏感，有利于以后迁移数据。

可修改 MySQL 配置文件 `my.ini`：

```ini
[mysqld]
# Windows 下表名也区分大小写，与 Linux 一致。
lower_case_table_names=2
```

此配置必须在 MySQL 初始化之前设置。一旦 MySQL 已经启动或初始化，再修改该配置，MySQL 将无法再次启动。

## 关于数据库迁移

迁移数据库时，需要将表结构和数据一起迁移。不可只迁移表结构，而不迁移数据。这会导致 Liquibase 重复建表，导致报错。

如果只想迁移表结构，则完全不用迁移。程序在连接到一个空数据库时，会自动创建表结构和插入初始化数据。

## 结构说明

* ujcms-code-generator: 代码生成器
* ujcms-common: 公共工具库
* ujcms-cms: CMS 核心功能
* ujcms-starter: Spring Boot 启动模块
* ujcms-ui-console: 后台管理前端

## 交流群

QQ 交流群：626599871

微信交流群：![UJCMS 交流群](https://res.ujcms.com/uploads/images/ujcms-group.png)
