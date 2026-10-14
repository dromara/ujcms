# ujcms-starter

UJCMS Spring Boot 启动模块，是整个项目的入口。负责组装所有业务模块、注册全局 Bean、配置 Web MVC 拦截器，并打包为可运行的 JAR 或 WAR。

## 目录结构

```
ujcms-starter/
├── src/main/
│   ├── java/com/ujcms/cms/starter/
│   │   └── Application.java          # Spring Boot 入口；注册全局 Bean、配置 Web MVC
│   ├── resources/
│   │   ├── application.yaml          # 主配置文件（Docker 镜像另叠加根目录 docker/application.yaml，数据库连接取自环境变量）
│   │   └── application-level3.yaml   # 等保三级 / 密评三级 预设（仅商业版高级版、旗舰版提供），作为附加 Profile 启用（如 --spring.profiles.active=level3）
│   └── webapp/
│       ├── themes/                   # FreeMarker 主题（按站点 ID 分目录）
│       │   ├── 1/default/            # 站点 1 默认主题（自带 package.json，见下文）
│       │   └── error/                # 错误页模板（404、500 等）
│       ├── cp/                       # 管理控制台构建产物（由 ujcms-ui-console 生成，不纳入版本库）
│       ├── uploads/                  # 上传文件
│       ├── theme-packages/           # 主题包库（运行时生成，不纳入版本库）
│       └── WEB-INF/
│           ├── web.xml               # Servlet 配置（WAR 部署用）
│           ├── lucene/               # Lucene 全文索引数据
│           └── backup/               # 数据库、主题、上传文件的备份存档
└── pom.xml
```

## 前台主题静态资源

前台主题的第三方库（cropperjs、sm-crypto、pdfjs 等）、图标和 Tailwind 样式由主题自带的 npm 脚本构建。以默认主题为例：

```bash
cd src/main/webapp/themes/1/default && npm install && npm run build
```

其中 `assets/vendor/` 为构建产物，不纳入版本库，首次运行前必须先构建。详见项目根目录 README 的「构建前台模板静态资源」部分。

## 与其他模块的关系

```
ujcms-common
     ↓
ujcms-cms
     ↓
ujcms-starter      ←── 启动入口，聚合所有模块
```
