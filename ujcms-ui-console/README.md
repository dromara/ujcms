# UJCMS-CP — 后台管理前端

UJCMS 后台管理界面，基于 **Vue 3 + Vite + TypeScript + ElementPlus + TailwindCSS + VueRouter + VueI18n** 构建。

> 需要启动 `ujcms-starter` 作为后端接口。如无后端接口，前端项目不可用。

## 环境要求

- Node 22.12+
- 可设置淘宝 npm 镜像加速：`npm config set registry https://registry.npmmirror.com/`
- pnpm（如未安装，执行 `npm install -g pnpm`）

## 快速开始

```bash
# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev
```

- 访问：[http://localhost:5173](http://localhost:5173)
- 用户名：admin，密码：password

## 构建与部署

```bash
pnpm build:starter
```

此命令会将后台前端编译到 `/ujcms-starter/src/main/webapp/cp`。启动 `ujcms-starter`，可以通过 `http://localhost:8080/cp` 访问。

**常见错误：** 构建时出现 `JavaScript heap out of memory`，设置环境变量以增大内存：

```bash
export NODE_OPTIONS=--max-old-space-size=8192
```

## 自定义标识

- 修改 `.env` 文件中的 `VITE_APP_TITLE=UJCMS后台管理` 配置，可改变浏览器页签上的标题。
- 修改 `.env` 文件中的 `VITE_APP_NAME=UJCMS` 配置，可改变登录页、后台左侧导航等处的`UJCMS`标识。
- 修改 `/src/layout/components/AppSidebar/SidebarLogo.vue`，可改变左侧导航 Logo SVG 图标。

## 前后端分离部署

**同域部署（推荐）**：将后台前端编译到 `/ujcms-starter/src/main/webapp/cp` 目录，前后端同域，无跨域问题。

**跨域部署**：前后端位于不同域名或端口时，需在前端服务器配置反向代理。

```bash
pnpm build
```

编译后的程序在 `dist` 目录，可将其独立部署。

Nginx 反向代理配置示例：

```nginx
# 代理 API 接口
location /api {
    proxy_pass http://www.example.com;
}

# 代理上传文件
location /uploads {
    proxy_pass http://www.example.com;
}
```

开发模式下，Vite 已内置代理配置（见 `vite.config.ts`），自动将 `/api` 和 `/uploads` 请求转发至 `http://localhost:8080`，无需额外配置。

## 二次开发：菜单与权限

**新增功能菜单**：可在 `src/router/index.ts` 文件中配置。

**新增功能权限**：可在 `src/data.ts` 文件中配置。配置好的权限会在 `角色管理 - 权限设置` 中的 `功能权限` 中显示。

配置内容：

```
export function getPermsTreeData(): any[] {
  const {
    global: { t },
  } = i18n;
  const perms = [
    ...
  ]
}
```

