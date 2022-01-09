# UJCMS

Java开源内容管理系统(java cms)。使用SpringBoot、MyBatis、Shiro、Lucene、FreeMarker、TypeScript、Vue3、ElementPlus等技术开发。

UJCMS是在Jspxcms多年的开发经验上，重新设计开发的Java CMS系统。针对原系统中的一些痛点问题，进行解决、优化和改进，并使用`AGPL-3`开源协议发布。

技术上尽量选择主流、先进、简单的架构，方便用户进行二次开发。持久化层用MyBatis替换了Hibernate；视图层用前后端分离的Vue3替换了JSP；数据库也进行了重新设计。设计上强调“简单”、“灵活”，避免繁杂的设计和实现，降低系统维护成本和二次开发难度。功能使用上也要求“简单”，避免复杂的使用逻辑。

* 官网地址：[https://www.ujcms.com](https://www.ujcms.com)
* 下载地址：[https://www.ujcms.com/download/](https://www.ujcms.com/download/)。提供安装包下载。
* 演示站前台：[https://demo.ujcms.com](https://demo.ujcms.com)。使用手机访问或者浏览器手机模式访问前台，会自动呈现手机页面。
* 演示站后台：[https://demo.ujcms.com/cp/](https://demo.ujcms.com/cp/)。演示用户登录后只拥有浏览后台功能，所有操作功能点击后都会显示无权访问（403）。如需进行操作测试，可以下载软件到本地安装。

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
* MySQL 5.7（兼容 5.6、8.0）。
* Tomcat 8.5、9.0 (Servlet 3.1+)。
* Maven 3.5 或更高版本。
* 系统后台兼容的浏览器：Chrome、Firefox、Edge。
* 前台页面兼容的浏览器取决于模板，使用者可以完全控制模板，理论上可以支持任何浏览器。演示模板支持IE10+(文库功能除外)、Chrome、Firefox、Edge。

## 数据导入数据库

1. 创建数据库。如使用MySQL，字符集选择`utf8mb4`（支持更多特殊字符如表情字符emoji，推荐）。不要选择`utf8`，该字符集可能导致某些特殊字符出现乱码。
2. 执行数据库脚本。数据库脚本在`database`目录下。

## 启动程序

1. 在eclipse中导入maven项目。点击eclipse菜单`File` - `Import`，选择`Maven` - `Existing Maven Projects`。创建好maven项目后，会开始从maven服务器下载第三方jar包（如spring等），需要一定时间，请耐心等待。（另外：Eclipse中会出现红叉的错误警告，如是JavaScript或HTML报错则无需理会，不影响程序正常运行。这是由于Eclipse校验规则误判所致，可以在Eclipse中设置禁止对js文件进行错误校验。）
2. 修改数据库连接。打开`/src/main/resources/application.propertis`文件，根据实际情况修改`spring.datasource.url`、`spring.datasource.username`、`spring.datasource.password`的值。
3. 启动程序。在eclipse中，右键点击项目名，选择`Run as` - `Java Application`，选择`Application - com.ujcms`，然后点击`OK`。也可在左侧导航中找到`com.ujcms.Application`类并右键点击，选择`Run as` - `Java Application`即可直接运行。
4. 使用 IntelliJ IDEA 开发的，步骤与 Eclipse 类似。打开工程后，修改数据库连接，然后直接点击右上角的绿色三角图标(`Run 'Application'`)，即可直接运行。也可在左侧导航中找到`com.ujcms.Application`类并右键点击，选择`Run 'Application'`。
5. 前台地址：[http://localhost:8080/](http://localhost:8080/)，使用手机访问前台或者使用浏览器模拟手机访问前台，会自适应显示手机端的界面。
6. 后台地址：[http://localhost:8080/cp/](http://localhost:8080/cp/)，用户名：admin，密码：password。后台前端基于`Vue 3`开发，如要修改后台界面，请另外下载`ujcms-cp`项目。

## 后端技术

* SpringBoot：提供了对Spring开箱即用的功能。简化了Spring配置，提供自动配置auto-configuration功能。
* SpringMVC：MVC框架，使用方便，Bug较少。
* Mybatis：持久化框架。
* FreeMarker：网站模板组件。
* Shiro：安全组件。配置简便。
* Lucene：全文检索组件。

## 前端技术

* TypeScript: JavaScript的一个超集。
* Vue 3：JavaScript框架。
* ElementPlus：Vue 3 UI 框架。
* Tailwind CSS: 功能类优先的 CSS 框架。
* Tinymce: 富文本编辑器。
