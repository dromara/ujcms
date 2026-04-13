# ujcms-cms

UJCMS 内容管理系统核心模块，实现文章、栏目、用户、权限、工作流、全文检索、模板渲染等全部 CMS 业务逻辑。

## 包结构

```
com.ujcms.cms
├── core                         # 核心 CMS 功能
│   ├── domain                   # 实体类（MyBatis Generator 生成部分在 generated/ 中）
│   ├── mapper                   # MyBatis Mapper 接口及 XML 映射
│   ├── service                  # 业务服务层，service/args/ 为查询参数类
│   ├── lucene                   # Lucene 全文检索：索引域定义
│   ├── security                 # CMS 安全扩展：权限校验、租户隔离
│   ├── aop                      # 操作日志 AOP 切面
│   ├── component                # 独立组件：JWT、密码、短信、模板、访问统计
│   ├── generator                # 代码/静态页生成器
│   ├── listener                 # 应用事件监听器
│   ├── support                  # 全局常量、枚举、工具类
│   └── web
│       ├── api/                 # 公开 REST API（前缀 /api/）
│       ├── backendapi/          # 后台管理 API（前缀 /cp/）
│       ├── frontend/            # FreeMarker 模板渲染控制器
│       ├── directive/           # FreeMarker 自定义标签
│       └── support/             # Web 层公共基类与工具
└── ext                          # 扩展功能（与 core 结构平行）
    ├── domain                   # 扩展实体：调查问卷、投票、留言板、访问统计等
    ├── mapper / service         # 扩展 Mapper 与服务
    └── web
        ├── api/                 # 扩展公开 API
        ├── backendapi/          # 扩展后台 API
        ├── frontend/            # 扩展前台控制器
        └── directive/           # 扩展 FreeMarker 标签
```

## 核心实体

### 内容管理

| 实体 | 说明 |
|------|------|
| `Site` | 站点，支持多站点 |
| `Channel` | 栏目，树形结构（`ChannelTree`） |
| `Article` / `ArticleExt` | 文章主体与扩展字段 |
| `Tag` | 文章标签 |
| `Block` / `BlockItem` | 区块（推荐位）及其内容 |
| `Attachment` | 附件管理 |
| `Model` | 内容模型（自定义字段） |

### 用户与权限

| 实体 | 说明 |
|------|------|
| `User` / `UserExt` | 用户主体与扩展（含第三方 OpenID） |
| `Role` | 角色（含栏目/文章权限映射） |
| `Group` | 用户组（访问控制） |
| `Org` / `OrgTree` | 组织机构，树形结构 |
| `Config` | 全局系统配置 |

### 扩展功能（ext）

| 实体 | 说明 |
|------|------|
| `Vote` / `VoteOption` | 投票 |
| `MessageBoard` | 留言板 |
| `VisitLog` / `VisitStat` | 访问日志与统计 |
| `Collection` | 内容采集配置 |

## Web 层接口

### 公开 API（/api/）

面向前台页面和第三方客户端：

- `ArticleController` — 文章查询
- `ChannelController` — 栏目查询
- `JwtAuthController` — JWT 登录/刷新/注销
- `RegisterController` — 用户注册
- `UserController` — 个人信息
- `UploadController` — 文件上传
- `VoteController`、`MessageBoardController` — 扩展功能

### 后台管理 API（/api/backend/）

面向管理后台：

- 内容：`ArticleController`、`ChannelController`、`BlockController`、`TagController`
- 用户权限：`UserController`、`RoleController`、`GroupController`、`OrgController`
- 系统：`SiteController`、`ModelController`、`DictController`、`GlobalSettingsController`
- 工具：`GeneratorController`（静态页生成）、`TaskController`、`UploadController`

### 模板渲染（FreeMarker 前台）

`web/frontend/` 下的控制器处理 FreeMarker 模板请求：

- `HomepageController` — 首页
- `ArticleController` — 文章详情页
- `ChannelController` — 栏目列表页
- `SearchController` — 全文检索结果页
- `RssController` — RSS 订阅
- `SiteMapController` — 站点地图

### FreeMarker 自定义标签（directive）

| 标签 | 说明 |
|------|------|
| `ArticleListDirective` | 文章列表 |
| `ArticleDirective` | 单篇文章 |
| `ArticlePageDirective` | 文章分页 |
| `ArticlePrevDirective` / `ArticleNextDirective` | 上一篇/下一篇 |
| `ChannelListDirective` | 栏目列表 |
| `BlockItemListDirective` | 区块内容列表 |
| `TagListDirective` / `TagPageDirective` | 标签列表/分页 |
| `EsArticleListDirective` / `EsArticlePageDirective` | ES 全文检索文章 |
| `SiteListDirective` | 多站点列表 |
| `DictListDirective` / `ModelListDirective` | 字典/模型列表 |

## 数据库迁移

使用 Liquibase 管理，变更记录位于 `src/main/resources/db/changelog/`。

## 与其他模块的关系

```
ujcms-common
     ↓
ujcms-cms          ←── 核心业务
     ↓
ujcms-starter      （Spring Boot 启动入口）
```
