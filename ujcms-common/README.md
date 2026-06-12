# ujcms-common

UJCMS 公共工具库，提供安全、数据库、文件存储、全文检索、模板引擎、Web 工具等通用基础能力，供 `ujcms-cms`、`ujcms-starter` 等模块共享使用。

## 包结构

```
com.ujcms.common
├── captcha      # 验证码：图形验证码生成、IP 登录限制、令牌管理
├── db           # 数据库工具：MyBatis 类型处理器、树形结构、排序、Liquibase 扩展
├── file         # 文件存储：本地、FTP、MinIO(S3) 多后端，ZIP 工具
├── freemarker   # FreeMarker 扩展：分页方法、BBCode、格式化、自定义模板加载器
├── function     # 函数式工具类
├── image        # 图片处理：缩略图（Thumbnailator）、ImageMagick (im4java) 支持
├── ip           # IP 归属地查询（ip2region）
├── lucene       # Lucene 全文检索封装：操作模板、高亮、指数衰减评分
├── misc         # 杂项工具：媒体处理（JAVE）、序列化工具
├── query        # 动态查询：QueryParser 解析前端查询参数，生成 MyBatis 条件
├── security     # 安全：JWT(HmacSM3)、OAuth2(微信/QQ/微博)、XSS 过滤、SM2/SM3 加密、PBKDF2
├── sms          # 短信：IP 级发送频率限制
└── web          # Web 工具：请求/响应工具、URL 构建、上传、拦截器、异常处理
```

## 主要功能

### 安全（security）

- **JWT**：基于国密 SM3（HmacSM3）算法的 JWS 签名与验证（`HmacSm3JwsSigner` / `HmacSm3JwsVerifier`）
- **OAuth2**：微信、QQ、微博第三方登录客户端
- **密码编码**：`Pbkdf2WithHmacSm3PasswordEncoder`，使用国密 SM3 的 PBKDF2 密码哈希
- **XSS 防护**：`XssFilter` + `XssHttpServletRequestWrapper`，基于 OWASP HTML Sanitizer
- **登录保护**：IP 过度尝试异常、验证码错误异常、账号未激活/已注销异常

### 文件存储（file）

| 后端 | 类 |
|------|----|
| 本地文件系统 | `LocalFileHandler` |
| FTP 服务器 | `FtpFileHandler`（基于 Apache Commons Pool2 连接池）|
| MinIO / S3 兼容 | `MinIoFileHandler` |

统一接口 `FileHandler`，上层代码无需关心存储后端。

### 动态查询（query）

`QueryParser` 将前端传入的排序、过滤参数解析为 MyBatis 可用的条件对象，防止 SQL 注入。`BaseQueryArgs` 提供所有查询参数的基类。

### 全文检索（lucene）

`LuceneOperations` 封装 Lucene 索引的增删改查，`HighlightDocument` 提供搜索结果高亮，`ExpDecayValueSource` 实现时间衰减相关性评分。

### 数据库工具（db）

- 自定义 MyBatis 类型处理器：`CharBooleanTypeHandler`、`NumericBooleanTypeHandler`、`JsonStringTypeHandler`
- 树形结构支持（`tree/`）
- 字段排序支持（`order/`）
- Liquibase 多数据库扩展（`liquibase/`）

### FreeMarker 扩展（freemarker）

| 组件 | 说明 |
|------|------|
| `PagingMethod` | 分页 URL 生成 |
| `AddParamMethod` | URL 参数追加 |
| `BbCodeMethod` | BBCode 转 HTML |
| `FormatMethod` | 数字/日期格式化 |
| `OsTemplateLoader` | 操作系统路径模板加载器，支持运行时切换模板 |

### 验证码（captcha）

- 图形验证码生成（jcaptcha）
- 验证码令牌服务 `CaptchaTokenService`
- 基于 Caffeine 缓存的 IP 登录频率限制

### IP 归属地（ip）

基于 ip2region 的离线 IP 归属地查询，`IpSeeker` 提供统一查询接口，`Region` 封装国家/省/市/ISP 信息。

### 图片处理（image）

- Thumbnailator：Java 原生图片缩略图
- im4java：调用本机 ImageMagick 进行高质量图片转换

## 依赖技术栈

| 类别 | 依赖 |
|------|------|
| 框架 | Spring Boot 3.5、Spring Security、Spring MVC |
| ORM | MyBatis + PageHelper |
| 安全 | BouncyCastle（SM2/SM3）、Spring OAuth2 |
| 存储 | MinIO、Apache Commons Net (FTP)、Commons Compress |
| 检索 | Apache Lucene 8.11 |
| 模板 | FreeMarker + freemarker-java8 |
| 图片 | Thumbnailator、im4java |
| 媒体 | JAVE (FFmpeg 封装) |
| IP | ip2region |
| HTML | jsoup |
| 缓存 | Caffeine |
| 迁移 | Liquibase |
| API 文档 | springdoc-openapi |

## 与其他模块的关系

`ujcms-common` 不依赖任何上层业务模块，是整个项目最底层的共享库。
