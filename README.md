# UJCMS

Java开源内容管理系统(java cms)。使用SpringBoot、MyBatis、Shiro、Lucene、FreeMarker、TypeScript、Vue3、ElementPlus等技术开发。

UJCMS是在Jspxcms多年的开发经验上，重新设计开发的Java CMS系统。针对原系统中的一些痛点问题，进行解决、优化和改进，并使用`GPL-2`开源协议发布，可免费商用。

技术上尽量选择主流、先进、简单的架构，方便用户进行二次开发。持久化层用MyBatis替换了Hibernate；视图层用前后端分离的Vue3替换了JSP；数据库也进行了重新设计。设计上强调“简单”、“灵活”，避免繁杂的设计和实现，降低系统维护成本和二次开发难度。功能使用上也要求“简单”，避免复杂的使用逻辑。

* 官网地址：[https://www.ujcms.com](https://www.ujcms.com)
* 下载地址：[https://www.ujcms.com/download/](https://www.ujcms.com/download/)。提供安装包下载。
* 演示站前台：[https://demo.ujcms.com](https://demo.ujcms.com)。使用手机访问或者浏览器手机模式访问前台，会自动呈现手机页面。
* 演示站后台：[https://demo.ujcms.com/cp/](https://demo.ujcms.com/cp/)。演示用户登录后只拥有后台浏览功能，所有操作功能点击后都会显示无权访问（403）。如需进行操作测试，可以下载软件到本地安装。
* QQ交流群：626599871

## 技术及功能亮点

**自定义字段可查询**：所有的自定义字段都可查询增强了系统的灵活性。

**自定义字段可视化设计**：自定义字段使用拖拽式的可视化设计，所见即所得。

**URL地址SEO优化**：栏目和文章的动态地址可以通过系统的全局设置功能进行修改。默认的栏目和文章URL地址前缀为`/channel`和`/article`，可以根据自己的需要修改，如改为`/categories`和`/archives`。多站点的情况下，子站点URL地址也由原来的`www.example.com/site-abc`的形式改为更友好的`www.example.com/abc`的形式。

**清理垃圾附件**：系统使用时，可能会多传、误传图片等附件；在删除文章后，文章中的图片还保留在系统中，产生大量的未使用的垃圾图片和附件。系统中的附件管理可以查看所有未使用的图片和附件，并可对其进行删除。

**附件、模板、索引文件独立部署**：系统运行时产生的文件可以和程序分开，部署到独立的目录，方便系统备份、升级和管理。比如上传的图片和附件、前台的模板、索引文件，都可以部署到程序以外的目录。

**模板文件和CSS、JS在同一目录**：模板文件和CSS、JS分开的目录结构，会给模板制作和部署带来很大的不便性。而将模板文件和CSS、JS放在一起的设计，会方便很多。

**MyBatis参数化查询**：后台数据通常会需要通过不同字段进行搜索，对每个表都写大量的查询，无疑是一项繁重的工作。MyBatis参数化查询功能通过前台传递查询参数，即可实现任意字段及关联表的查询功能（如：Q_title=abc，Q_user-username=test），无需后台编写代码，大幅减少后端的开发工作量。

**主副表拆分**：对查询量大的复杂表进行主副表拆分，把常用的查询字段放到主表，不常用的字段放到副表，提升大数据量下的性能表现。

## 环境要求

* JDK 8。
* MySQL 5.7（8.0）。
* Tomcat 8.5、9.0 (Servlet 3.1+)。
* Maven 3.5 或更高版本。
* 系统后台兼容的浏览器：Chrome、Firefox、Edge。
* 前台页面兼容的浏览器取决于模板，使用者可以完全控制模板，理论上可以支持任何浏览器。演示模板支持IE10+(文库功能除外)、Chrome、Firefox、Edge。

## 创建数据库

1. 创建数据库。数据库名可为`ujcms`或其它任何名称。
  * MySQL，字符集选`utf8mb4`（不要选择`utf8`，该字符集可能导致某些特殊字符出现乱码）。
  * 达梦数据库，字符集选`UTF-8`（不要使用`GB18030`，该字符集可能导致某些特殊字符出现乱码）。
  * 人大金仓数据库，字符集选`UTF8`（不要使用`GBK`，该字符集可能导致某些特殊字符出现乱码）。
2. 无需执行SQL文件，程序启动时会自动创建表及初始化数据。以后程序升级同样不需要执行SQL升级脚本，程序启动时会判断当前软件版本及数据库表结构版本，自动进行数据库表结构升级。

## MySQL表名大小写问题

如果在Windows环境使用MySQL，且以后需要迁移到Linux环境的MySQL，建议将Windows环境的MySQL配置为表名大小写敏感模式。

因为Linux环境下MySQL的表名是大小写敏感的；而在Windows环境下MySQL表名大小写不敏感，且会自动把大写的表名改为小写的表名。从Windows向Linux迁移数据时，本为大写表名的成为了小写表名，导致程序出错。需要手动把小写表名改回大写表名，费时费力且容易出错。

UJCMS系统的表名都为小写，不管在Window还是Linux下都没有问题，但第三方的类库（如Liquibase、Flowable、Quartz等）创建的表则为大写表名。所以Windows环境下也把MySQL设置成表名大小写敏感，有利以后迁移数据。

可修改MySQL配置文件`my.ini`：

```
[mysqld]
# Windows下表名也区分大小写，与Linux一致。
lower_case_table_names=2
```

## 启动程序

1. 在Eclipse中导入maven项目。点击Eclipse菜单`File` - `Import`，选择`Maven` - `Existing Maven Projects`。创建好maven项目后，会开始从maven服务器下载第三方jar包（如spring等），需要一定时间，请耐心等待。（另外：Eclipse中会出现红叉的错误警告，如是JavaScript或HTML报错则无需理会，不影响程序正常运行。这是由于Eclipse校验规则误判所致，可以在Eclipse中设置禁止对js文件进行错误校验。）
2. 修改数据库连接。打开`/src/main/resources/application.yaml`文件，根据实际情况修改`spring.datasource.url`、`spring.datasource.username`、`spring.datasource.password`的值。其中`spring.datasource.url`中的数据库名要和上一步创建的数据库名一致。
3. 启动程序。在eclipse中，右键点击项目名，选择`Run as` - `Java Application`，选择`Application - com.ujcms`，然后点击`OK`。也可在左侧导航中找到`com.ujcms.cms.Application`类并右键点击，选择`Run as` - `Java Application`即可直接运行。
4. 使用 IntelliJ IDEA 开发的，步骤与 Eclipse 类似。打开工程后，等待Maven下载依赖，修改数据库连接，然后直接点击右上角的绿色三角图标(`Run 'Application'`)，即可直接运行。也可在左侧导航中找到`com.ujcms.cms.Application`类并右键点击，选择`Run 'Application'`。
5. 首次运行程序，会自动创建数据库表和初始化数据库，需要一些时间，请耐心等待，只要没有出现报错信息，说明程序还在启动中，不要急于关闭程序。直到出现类似`com.ujcms.cms.Application: Started Application in xxx seconds`信息，代表程序启动完成。如果程序首次启动，还在创建数据库表时，强行关闭了程序；再次启动程序可能会出现类似`LockException: Could not acquire change log lock`或`Waiting for changelog lock....`的报错信息；此时只要将数据库`databasechangeloglock`表中数据清空（注意，不是`databasechangelog`表），也可删除数据库所有表甚至重建数据库，再次启动程序即可继续创建数据库表和初始化数据，正常启动。
6. 前台地址：[http://localhost:8080/](http://localhost:8080/)，使用手机访问前台或者使用浏览器模拟手机访问前台，会自适应显示手机端的界面。如遇到前台页面没有样式的情况，则是因为没有部署在Tomcat的根目录。如前台首页地址类似为`http://localhost:8080/abc`，即代表部署在`/abc`目录下，没有部署在根目录。解决办法请参考下一章节内容。
7. 后台地址：[http://localhost:8080/cp/](http://localhost:8080/cp/)，用户名：admin，密码：password。后台前端基于`Vue 3`开发，如要修改后台界面，请另外下载`ujcms-cp`项目。

## 常见错误

如出现`flowable-eventregistry-db-changelog.xml::1::flowable:` `Specified key was too long; max key length is 767 bytes`等错误信息，则 MySQL 5.7 需要设置`innodb_large_prefix=ON`；MySQL 5.6 需要设置`innodb_large_prefix=1`。从 mysql 5.7.7 开始，`innodb_large_prefix`的默认值就是`ON`，因此只要MySQL版本大于5.7.7即可避免这个问题。

## 部署

* 使用maven进行打包`mvn package`。会生成`target/ujcms-***.war`文件和`target/ujcms-***`目录。
* 用目录方式部署的，可以将`target/ujcms-***/`目录下文件复制到tomcat的`webapps/ROOT`目录下（请先删除原tomcat/webapps目录下所有文件夹）。复制完成后有类似`webapps/ROOT/uploads` `webapps/ROOT/templates` `webapps/ROOT/WEB-INF`等文件夹。
* 也可使用war包部署，将`ujcms-***.war`更名为`ROOT.war`，复制到tomcat的`webapps`目录下（请先删除原tomcat/webapps目录下所有文件夹）。复制完成后文件地址为`webapps/ROOT.war`文件。war部署要使用解压模式，tomcat默认就是使用解压模式，如修改过tomcat配置，请检查`tomcat/conf/server.xml`配置文件中`<Host ... unpackWARs="true" ... >`的配置项。
* 需注意`/WEB-INF/classes/application.yaml`文件中的数据库地址、用户名、密码相关配置是否与部署环境的数据库一致。

## jar部署

程序默认打包为WAR格式。如需要使用springboot的jar方式启动，可按以下步骤修改：

* 使用maven进行打包`mvn package -P jar`。
* 将打包的`target/ujcms-***.jar`文件复制到部署目录。
* 在部署目录（即`ujcms-***.jar`所在目录）新建文件夹`static`。
* 将`src/main/webapp`目录下所有文件复制到上一步新建的`static`目录下。复制完成后，会有类似`static/WEB-INF` `static/templates`等文件夹。
* 在部署目录运行`java -jar ujcms-***.jar`命令，即可启动。

完整目录结构：

* ujcms-***.jar
* static/WEB-INF/...
* static/templates/...
* static/uploads/...
* static/cp/...

## 关于部署路径

程序通常在部署在Tomcat根目录，首页访问地址类似`http://www.mysite.com/` `http://localhost/` `http://localhost:8080/`。在一些特殊的场合，如在Eclipse默认的Tomcat启动方式，可能将程序部署在某一个路径下，首页访问地址类似`http://www.mysite.com/ujcms/` `http://localhost/ujcms/` `http://localhost:8080/ujcms/`。此时访问网站前台会出现样式不能正常显示的情况，可以到后台`配置 - 全局设置`中设置`上下文路径`，类似为`/ujcms`，其中斜杠`/`不能省略，`ujcms`为部署目录的路径，如在开发环境，则通常为项目名。

开发环境要避免使用上下文路径，除非网站正式部署时也要部署到相应的路径下，否则在开发环境下上传的图片部署到正式环境时，不能正常显示。因为上传图片时，图片地址会带有上下文路径的信息（如：`/ujcms/uploads/...`）。

Eclipse默认的tomcat启动方式会将程序部署到特定目录再启动，并不是直接在项目所在目录启动tomcat，这时上传的图片（包括通过系统后台新增和修改的模板）也保存在特定的部署目录，并不会保存在程序所在的目录。当修改了Eclipse源代码或文件，会自动重新部署程序，之前上传的图片会被清空。如果发现在开发环境下上传的图片突然都找不到了，很可能就是这个原因。

综上所述，强烈建议使用之前`启动程序`中介绍的方式启动程序。

## 前台模板

网站前台模板位于`/src/main/webapp/templates`目录，使用`Freemarker`技术。通过修改模板文件，可以完全控制网站页面显示的内容。

## 后端技术

* Spring Boot：提供了对Spring开箱即用的功能。简化了Spring配置，提供自动配置auto-configuration功能。
* Spring MVC：MVC框架，使用方便，Bug较少。
* Spring Security：安全组件。
* Mybatis：持久化框架。
* FreeMarker：网站模板组件。
* Lucene：全文检索组件。

## 前端技术

* TypeScript: JavaScript的一个超集。
* Vue3：JavaScript框架。
* ElementPlus：Vue 3 UI 框架。
* Vite: 下一代前端开发与构建工具。
* Tailwind CSS: 功能类优先的 CSS 框架。
* VueRouter: Vue 路由组件。
* VueI18n: Vue 国际化组件。
* Tinymce: 富文本编辑器。

## 目录结构

* src
  * main
    * java
      * com/ujcms/cms
        * core
          * domain：实体类
          * generator：生成功能（包括静态页生成，全文索引生成）
          * listener：监听类（包括数据删除的监听）
          * lucene：全文检索功能
          * mapper：MyBateis Mapper Java 文件
          * security：安全相关功能
          * service：服务层功能
          * support：各种支持类
          * web
            * api：前台API接口
            * backendapi：后台API接口
            * directive：前台Freemarker自定义标签
            * frontend：前台页面Controller
            * support：Web支持类（包括web拦截器等）
          * ContextConfig.java：Context配置类
          * LuceneConfig.java：全文索引配置类
          * ShiroConfig.java：Shiro安全配置类
          * TaskExecutorConfig.java：任务执行器配置类
        * Application.java 启动类
      * com/ujcms/util：公共工具类
    * resources
      * com/ujcms/cms/core/mapper：MyBatis Mapper XML 配置文件。
      * db
        * changelog：数据库表结构更新日志文件。
        * data.mysql.sql：数据库初始化数据SQL脚本。
      * application.yaml：程序配置文件。包括数据库URL、数据库用户名、数据库密码等信息。
      * messages.properties：国际化资源文件。
    * webapp：
      * cp：后台前端页面。
      * templates：网站前台模板。
      * uploads：用户上传文件。
      * WEB-INF/lucene：全文检索索引文件。
* .editorconfig：设置编辑器文件的格式，如缩进方式、最大行数等。
* .gitignore：设置不需要提交到git管理的文件和目录。
* CHANGELOG.md：版本更新日志。
* LICENSE：许可协议。
* gulpfile.js：前台构建文件。具有拷贝jquery、bootstrap等文件至前台模板目录`/src/main/webapp/template/1/default/_files`等功能。
* package.json：前台模板依赖的js、css组件，如jquery、bootstrap等。
* pom.xml：Maven配置文件。

