# 财经权限审查 · 高保真交互原型

[在线预览](https://finance-access-review-20261016.garden-rook-7646.chatgpt.site/) · [交互设计说明](../docs/原型与交互设计_V1.0.md)

当前源码同步2026-10-19已审核原型73dadf6：检查项环形图与前三项摘要、22px侧栏名称、冗余小标题删除；页面使用虚构演示数据。原型不连接 Java API，浏览器本地状态不具有服务端权限隔离和审计效力。正式工程入口为根目录 README。

## 启动

需要 Node.js 20 或更高兼容版本，首次恢复依赖需访问 npm registry。在本目录运行：

```bash
npm ci --ignore-scripts
npm run prepare:assets
npm run check
npm start
```

打开 http://127.0.0.1:4173 。不建议直接以 file:// 打开 ES Module 页面。

## 依赖与可复现性

- Vue 复用 `../frontend/vendor/vue.esm-browser.prod.js`，不重复提交同一依赖。
- SheetJS 固定 `xlsx@0.18.5`，版本、下载地址和完整性信息写入 package-lock.json。
- `asset-checksums.json` 记录已发布原型依赖文件的 SHA-256；恢复脚本逐个核验，内容不符即失败。
- `vendor/` 的两个生成文件已忽略；许可证随源码提交。业务源码与已发布原型对应文件逐字节一致。

原型数据用于交互评审，请勿用于处理真实企业数据。依赖选型和正式数据处理以 Java 服务端运行版为准。
