## 启动程序

清理 Tomcat。找到Tomcat的目录，将webapps目录下原有文件夹全部删除（包括ROOT文件夹）。建议使用干净的 Tomcat，不要部署其它应用。

部署程序。将下载包中的 ROOT 文件夹拷贝到 tomcat/webapps 目录下。拷贝完成后，目录结构类似：

* `webapps/ROOT/cp/`
* `webapps/ROOT/templates/`
* `webapps/ROOT/uploads/`
* `webapps/ROOT/WEB-INF/`

连接数据库。打开 `/ROOT/WEB-INF/classes/application.yaml` 文件，根据实际情况修改以下配置：

* `spring.datasource.url`：数据库连接地址
* `spring.datasource.username`：数据库用户名
* `spring.datasource.password`：数据库密码

启动 Tomcat。双击 tomcat/bin/startup.bat 文件。
