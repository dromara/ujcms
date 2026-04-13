# UJCMS 代码生成器

代码生成器可以很大程度提高开发效率。特别是实体类（Domain）与Mapper.xml中实体类属性与数据库字段的对应关系，以及常用的insert、update语句，这些都是重复性工作，手动编写非常枯燥且容易出错。

代码生成器基于MyBatis官方的代码生成插件`mybatis-generator`，在官方插件的基础上进行了增强，可以生成更多的类和代码，并且对生成的内容进行了优化。

## 数据库连接

修改以下两个文件，填写正确的数据库URL地址、数据库用户名、数据库密码：

- `src/main/resources/generatorCoreConfig.xml`
- `src/main/resources/generatorExtConfig.xml`

```xml
<jdbcConnection driverClass="com.mysql.cj.jdbc.Driver"
                connectionURL="jdbc:mysql://127.0.0.1:3306/ujcms?serverTimezone=UTC"
                userId="ujcms" password="password"/>
```

## 增加新表

修改代码生成配置文件，加入需要生成代码的表：

`src/main/resources/generatorExtConfig.xml`

```xml
<generatorConfiguration>
  <context>
    ...
    <table tableName="ujcms_example" domainObjectName="Example"/>
    ...
  </context>
</generatorConfiguration>
```

运行`src/main/java/com.ujcms.generator.CodeGenerator`类，即可生成代码。

如表结构有改动，则可在改动后，再次运行代码生成器，会自动更新domain和mapper.xml里相应的字段。

## 生成文件列表

代码生成器会生成以下类文件：

* `../ujcms-cms/src/main/java/com/ujcms/cms/ext/domain/base/ExampleBase.java`：每次运行生成器都会生成，**不要手动修改**，否则会被覆盖
* `../ujcms-cms/src/main/java/com/ujcms/cms/ext/domain/Example.java`：只在第一次运行时生成，可以修改
* `../ujcms-cms/src/main/java/com/ujcms/cms/ext/mapper/ExampleMapper.java`：只在第一次运行时生成，可以修改
* `../ujcms-cms/src/main/resources/com/ujcms/cms/ext/mapper/ExampleMapper.xml`：修改表结构后再次运行生成器会自动维护部分代码，可以修改
* `../ujcms-cms/src/main/java/com/ujcms/cms/ext/service/ExampleService.java`：只在第一次运行时生成，可以修改
* `../ujcms-cms/src/main/java/com/ujcms/cms/ext/service/args/ExampleArgs.java`：只在第一次运行时生成，可以修改
* `../ujcms-cms/src/main/java/com/ujcms/cms/ext/web/backendapi/ExampleController.java`：只在第一次运行时生成，可以修改

`ExampleBase`由代码生成器根据数据库字段维护，修改数据库表结构后，再次运行代码生成器即可。如需要增加额外的代码，可修改它的子类`Example`。

`ExampleMapper.xml`最为特殊，也是灵魂所在。修改表结构后，再次运行生成器，会自动维护`BaseResultMap`、`Column_List`、`insert`、`update`标签内容。但其余部分代码不会被修改或覆盖，所以可以增加额外代码。

## 其它配置项

```xml
<table tableName="ujcms_example" domainObjectName="Example">
    <!-- 是否生成Service类。默认：true -->
    <property name="service" value="false"/>
    <!-- 是否生成Controller类。默认：true -->
    <property name="controller" value="false"/>
    <!-- 是否需要分页。某些表数据较少（如字典、角色），不需要分页。默认：true -->
    <property name="pageable" value="false"/>
</table>
```
