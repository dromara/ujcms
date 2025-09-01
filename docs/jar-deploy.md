## 启动程序

所需文件。需要以下文件及文件夹：

* `ujcms-***.jar`
* `config/application.yaml`
* `static/ROOT/cp/...`
* `static/ROOT/templates/...`
* `static/ROOT/uploads/...`
* `static/ROOT/WEB-INF/...`

连接数据库。打开 `config/application.yaml` 文件，根据实际情况修改以下配置：

* `spring.datasource.url`：数据库连接地址
* `spring.datasource.username`：数据库用户名
* `spring.datasource.password`：数据库密码

启动程序。在程序所在目录（即 `ujcms-***.jar` 所在目录）运行以下命令：

```
java -jar ujcm-***.jar
```
