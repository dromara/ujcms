# preview-bridge

页内预览桥接器（协议 **v2**，可视化构建器）的唯一来源：可视化构建器的传感器 + 装饰器部分。控制台拥有所有编辑逻辑；此桥接器仅负责测量几何信息（`layout`）、装饰编辑模式（轮廓 / 占位符 / 选中环）、上报点击事件（`select`）和拦截的导航（`navigate`），以及执行 `mode` / `refresh` / `reload` 命令。

## 交付目标

| 目标 | 路径 | 交付方式 |
|---|---|---|
| 模板轨道（FreeMarker） | `ujcms-cms/src/main/resources/static/_ujcms/preview-bridge.js` | 在此处构建的 IIFE，由 `PreviewBridgeInjectionFilter` 注入 —— 由渲染后端提供服务，永远不会出现版本偏差 |

构建产物的文件头注释（banner）携带 `src/bridge-core.ts` 的 `sha256`；核心代码变更后请重新构建。

## 构建

```bash
pnpm install
pnpm build   # 生成提交的 IIFE
```

切勿手动编辑 IIFE —— 请修改 `src/bridge-core.ts` 后重新构建。
