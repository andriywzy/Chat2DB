# AI 平台化改造验收记录（2026-04-01）

## 背景

本次验收针对 Chat2DB AI 平台化重构后的部署结果，目标是确认以下链路仍然可用：

- 登录与鉴权
- AI 配置读取
- 主 AI SSE 对话链路
- 知识库列表查询
- 知识库 SSE 检索链路

本次验收在本地 `docker compose` 环境完成，使用的服务包括：

- `chat2db`
- `chat2db-gateway`
- `milvus`
- `milvus-etcd`
- `milvus-minio`

## 部署内容

- 前端重新构建并注入到 [chat2db-server/chat2db-server-web-start/src/main/resources/static/front](/Users/andriywzy/Documents/github/chat2db/Chat2DB/chat2db-server/chat2db-server-web-start/src/main/resources/static/front)
- 后端重新打包为 [chat2db-server/chat2db-server-web-start/target/chat2db-server-web-start.jar](/Users/andriywzy/Documents/github/chat2db/Chat2DB/chat2db-server/chat2db-server-web-start/target/chat2db-server-web-start.jar)
- `chat2db` 服务通过 `docker compose` 强制重建并重新启动

## 基础可用性

- `GET /login` 返回 `HTTP/1.1 200`
- `chat2db` 容器启动正常
- 启动日志显示数据库迁移版本为 `2.1.14`

## 登录验证

- 使用默认账号 `chat2db / chat2db` 登录成功
- 成功拿到 `CHAT2DB` cookie 和 token
- `GET /api/oauth/user_a` 返回当前管理员用户信息正常

## AI 配置验证

`GET /api/config/system_config/ai` 返回正常，当前配置为：

- `aiSqlSource = TONGYIQIANWENAI`
- `apiHost = https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`
- `model = qwen3.5-plus`
- `stream = true`

## 主 AI 链路验证

验证方式：

- 调用 `GET /api/ai/chat`
- 携带登录态
- 携带 `uid` 请求头
- 携带 `dataSourceId = 2`
- 参数：
  - `promptType = TEXT_GENERATION`
  - `message = Reply with OK only`

返回结果：

- 接口返回 `text/event-stream`
- 成功收到连接事件
- 成功收到内容分片 `OK`
- 成功收到 `[DONE]`

结论：

- AI 主编排链路可用
- provider registry / orchestrator / prompt builder / stream callback 在运行环境中工作正常

## 知识库链路验证

### 列表查询

`GET /api/ai/knowledge/document/list` 返回正常，可见已有 `READY` 状态知识库文档。

### 检索问答

验证方式：

- 调用 `GET /api/ai/knowledge/search`
- 携带登录态
- 携带 `uid` 请求头
- 参数：
  - `message = What text is in the uploaded knowledge document?`

返回结果：

- 接口返回 `text/event-stream`
- 成功收到连接事件
- 成功持续收到知识库召回后的回答分片

结论：

- 知识库列表查询正常
- 知识库检索增强链路正常
- SSE 返回链路正常

## 历史约束清理进展

2026-04-02 已完成以下兼容式收口：

- `/api/ai/chat` 的 `uid` 不再只能从请求头读取，现已支持：
  - query 参数
  - 请求头
  - 当前登录用户兜底
- `/api/ai/chat` 不再强依赖 `dataSourceId`
  - 当没有数据库连接上下文时，AI 走无连接上下文模式
  - 当提供 `dataSourceId` 时，仍可继续使用原有数据库上下文增强
- `/api/ai/knowledge/search` 的 `uid` 解析已与主 AI 链路对齐
  - 现已支持 query 参数
  - 现已兼容请求头
  - 现已支持当前登录用户兜底
- 前端 AI SSE 调用已统一切到“query 传 uid 为主”，header 仅保留兼容能力

## 验收结论

本次 AI 平台化改造已经成功部署到当前运行环境，且以下链路通过真实验证：

- 登录
- AI 配置读取
- 主 AI SSE 对话
- 知识库列表
- 知识库搜索 SSE

结论：本次平台化改造在保持现有外部能力可用方面是成功的。
