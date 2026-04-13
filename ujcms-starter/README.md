# ujcms-starter

UJCMS Spring Boot 启动模块，是整个项目的入口。负责组装所有业务模块、注册全局 Bean、配置 Web MVC 拦截器，并打包为可运行的 JAR 或 WAR。

## 目录结构

```
ujcms-starter/
├── src/main/
│   ├── java/com/ujcms/starter/
│   │   └── Application.java          # Spring Boot 入口；注册全局 Bean、配置 Web MVC
│   ├── resources/
│   │   ├── application.yaml          # 配置文件
│   │   └── application-docker.yaml   # Docker 环境配置文件
│   └── webapp/
│       ├── templates/                # FreeMarker 模板（按站点 ID 分目录）
│       │   └── 1/default/            # 站点 1 默认主题模板
│       └── WEB-INF/
│           ├── web.xml               # Servlet 配置（WAR 部署用）
│           ├── lucene/               # Lucene 全文索引数据
│           └── backup/               # 数据库、模板、上传文件的备份存档
├── pom.xml
└── gulpfile.mjs                      # 前端静态资源构建脚本
```

## 与其他模块的关系

```
ujcms-common
     ↓
ujcms-cms
     ↓
ujcms-starter      ←── 启动入口，聚合所有模块
```
