# ujcms-ui-ssr-vue

UJCMS 前后端分离（Headless）示例站点，基于 Nuxt 4 + Vue 3.5 + TypeScript + Tailwind CSS v4。

## 项目简介

UJCMS 前台默认采用 FreeMarker 模板渲染，主题、模板市场、可视化编排与静态化均以此为基础。

本项目演示另一种架构：前台基于 Nuxt 独立开发与部署，数据全部来自 UJCMS 公共 REST API（`/api/**`）。

主要特点：

- **只有 4 个页面**：首页、栏目页、文章页、搜索页。项目刻意保持精简，方便阅读和理解。
- **只使用公共 API**：调用的接口与移动 App、小程序完全相同，后端没有为本项目提供任何专用接口。
- **服务端渲染（SSR）**：页面在服务端生成 HTML，对搜索引擎友好。

如果你的网站需要前后端分离，可以把本项目作为起点，再按自己的需求扩展。

## 环境要求

- Node.js `>=22.12.0`（或 `^20.19.0`）
- pnpm
- 一个已启动的 UJCMS 后端，默认地址为 `http://localhost:8080`

## 快速开始

```bash
pnpm install
pnpm dev
```

启动后访问 <http://localhost:3000>。

## 常用命令

| 命令             | 说明                                                         |
| ---------------- | ------------------------------------------------------------ |
| `pnpm dev`       | 启动开发服务器（端口 3000）                                  |
| `pnpm build`     | 生产环境构建，产物输出到 `.output` 目录                      |
| `pnpm start`     | 运行构建产物                                                 |
| `pnpm typecheck` | TypeScript 类型检查                                          |
| `pnpm lint`      | 代码检查（ESLint + Prettier）                                |
| `pnpm lint:fix`  | 自动修复代码风格问题                                         |
| `pnpm test:e2e`  | 冒烟测试（会自动构建并启动本项目，后端需已启动且有演示数据） |

## 配置后端地址

后端地址通过环境变量 `UJCMS_API_BASE` 指定，默认为 `http://localhost:8080`：

```bash
UJCMS_API_BASE=https://demo.ujcms.com pnpm dev
```

该变量在运行时读取，因此对构建产物同样有效，无需重新构建：

```bash
UJCMS_API_BASE=https://demo.ujcms.com pnpm start
```

## 请求是如何转发的

Nuxt 只负责渲染页面，以下路径会原样代理到 UJCMS 后端（配置见 `nuxt.config.ts`）：

| 路径                             | 内容             |
| -------------------------------- | ---------------- |
| `/api/**`                        | 公共 REST API    |
| `/uploads/**`                    | 上传的图片、附件 |
| `/download-file/**`              | 文件下载         |
| `/rss`                           | RSS 订阅         |
| `/sitemap.xml`、`/sitemap-*.xml` | 站点地图         |

取数分两种情况：

- **服务端渲染时**：Nuxt 服务端直接请求后端（`UJCMS_API_BASE`）。
- **浏览器中**：请求相对路径 `/api/**`，由 Nuxt 代理到后端，不存在跨域问题。

## 目录结构

```
app/
  pages/          页面：index（首页）、channel/[slug]（栏目）、article/[key]（文章）、search（搜索）
  components/     组件：页头、页脚、导航，以及文章、栏目、搜索相关的展示组件
  layouts/        页面布局
  api/            接口请求封装（http.ts 是统一的 fetch 封装）
  composables/    useSessionState：每次请求只获取一次的站点信息和配置
  utils/          格式化、分页等工具函数
server/
  middleware/     spring-passthrough：代理 /sitemap-article{n}.xml
shared/types/     后端 API 响应的 TypeScript 类型
tests/e2e/        冒烟测试（Playwright）
```

## 缓存与静态化

- **不做跨请求缓存**：每次访问都会向后端请求最新数据，后台修改内容后，刷新页面即可看到。
- **不做预渲染**：如果需要生成静态页面，请使用 FreeMarker 模板的静态化功能。

访问量较大的站点，建议自行在前面加上 CDN 或反向代理缓存。
