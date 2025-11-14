# lin-agent-fronted

一个基于 Vue 3 + Vite 的前端项目，包含两个 AI 应用页面：AI 情感大师与 AI 超级智能体。项目已集成 SSE 流式对话、深度优化的页面布局与侧边栏交互、以及开发代理到后端 Spring Boot 接口。

## 技术栈
- Vue 3
- Vite
- Vue Router
- Axios
- SSE（Server-Sent Events）

## 功能概览
- 主页入口，快捷进入两个应用
- AI 情感大师：调用 `/api/ai/emotion/chat/sse` 进行 SSE 流式对话
- AI 超级智能体：调用 `/api/ai/manus/chat` 进行流式对话
- 多页统一的 DeepSeek 风格界面与字体
- 侧边栏展开/收起、悬停滑出预览、左上角展开按钮
- 历史对话可点击预填，快速复用
- 页间切换入口与 Logo 展示

## 目录结构
```
src/
  assets/
  pages/
    Home.vue
    EmotionChat.vue
    ManusChat.vue
  router/
    index.js
  services/
    sse.js
App.vue
main.js
vite.config.js
```

## 快速开始
- Node 版本要求：`^20.19.0 || >=22.12.0`
- 安装依赖：
```
npm install
```
- 启动开发：
```
npm run dev
```
- 预览构建产物：
```
npm run build
npm run preview
```

## 路由与页面
- `/`：主页
- `/emotion`：AI 情感大师
- `/manus`：AI 超级智能体

路由定义位于 `src/router/index.js`，页面为按需加载。

## 接口与代理
- 后端地址前缀：`http://localhost:8123/api`
- SSE 接口：
  - 情感大师：`GET /api/ai/emotion/chat/sse?message=...&chatId=...`
  - 超级智能体：`GET /api/ai/manus/chat?message=...`
- 开发代理：`vite.config.js`
```
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8123',
      changeOrigin: true,
    },
  },
}
```

## SSE 使用说明
`src/services/sse.js` 提供统一封装：
- `openEmotionSSE(message, chatId, onChunk, onDone, onError)`
- `openManusSSE(message, onChunk, onDone, onError)`

页面在发送消息后推入一条空回复，SSE 到来的片段会追加到该回复，实现流式渲染。

## 交互与布局
- 侧边栏：默认展开；收起后鼠标悬停左侧边缘或展开按钮可滑出预览
- 展开按钮：在收起状态固定左上角；悬停隐藏收起按钮；点击即时展开
- 收起按钮：展开后显示；悬停展开按钮时隐藏以避免干扰
- 历史对话：列表项可点击，点击后将其标题预填入输入框
- 页间切换：侧栏上半部分显示另一个应用的切换入口与 Logo
- 发送按钮：在输入框右侧对齐
- 字体：`Inter, Noto Sans SC, PingFang SC, Microsoft YaHei, Segoe UI, Roboto, system-ui, -apple-system, sans-serif`

## 样式要点
- 统一淡灰背景与叠加 Hover 效果：按钮与列表项悬停为“淡灰与原背景叠加”
- “新对话”按钮为淡蓝背景与加号 Logo，文案与图标水平对齐
- 回复卡片与输入区域使用统一的无衬线字体族

## 开发提示
- 若 5173/5174 端口占用，Vite 会自动选择可用端口
- 前端代理仅作用于以 `/api` 开头的请求，请确保后端在 `8123` 端口启动并允许 SSE

## 常见问题
- 白屏排查：优先检查浏览器控制台报错与网络面板中的 SSE 请求是否成功；注意模板标签闭合
- SSE 中断：网络不稳或后端异常会导致 SSE 关闭，前端会自动清理并停止追加

## 许可证
本项目用于示例与学习，许可证请依据实际需要补充。
