## 目标
- 在每次调用 `doChatByStream` 时，为当前会话生成（或读取已存在的）标题 `title`，并写入当前发送的 `UserMessage.metadata`，从而随 `chat_memory.metadata` 一同持久化。
- 确保后续请求仍能获取既有标题，并避免随着窗口化记忆滑动而“丢失标题”。

## 关键现状
- 入口：`src/main/java/com/lin/linagent/app/EmotionApp.java` 的 `doChatByStream` 会创建 `UserMessage` 并写入 `userId` 到 `metadata`，使用 `chatId` 作为会话标识进行流式对话。
- 记忆：`MessageWindowChatMemory` + `CustomJdbcChatMemoryRepository`（MySQL 方言），会将 `message.getMetadata()` 序列化进 `chat_memory.metadata`。
- 读取：`MessageRowMapper` 会把 `metadata` 中的 `title` 回填到 `UserMessage.metadata`，已支持标题字段。

## 实现方案
1. 持久化位置与策略
- 将标题写入每次发送的 `UserMessage.metadata`：`metadata.put("title", <title>)`，结合现有仓库逻辑自动落表到 `chat_memory.metadata`。
- 为避免标题随着窗口滑动丢失：每次 `doChatByStream` 发送用户消息时，都确保 `metadata.title` 存在：
  - 若会话已有标题 → 复用该标题写入本次用户消息 metadata
  - 若会话无标题 → 生成一个新标题并写入

2. 标题生成与复用
- 在 `EmotionApp` 增加：
  - 字段：保存构造时创建的 `CustomJdbcChatMemoryRepository` 引用，供查询会话历史。
  - 方法 `private Optional<String> findExistingTitle(String conversationId)`：
    - 通过 `repository.findByConversationId(conversationId)` 读取窗口内消息，扫描 `UserMessage.metadata` 是否含 `title`，若有返回其值。
  - 方法 `private String generateTitle(String userText)`：
    - 基础规则（默认）：
      - 去除首尾空白、表情与多余标点，截断至 20 字（或 30 英文字符），保证直观、稳定、零额外模型耗费。
    - 可选增强（开关控制）：
      - 通过当前 `ChatClient` 额外调用一次模型，用系统提示词生成 8–12 字中文标题或 3–6 个英文词的短标题；失败则回退基础规则。
  - 方法 `private String getOrCreateTitle(String conversationId, String userText)`：
    - `findExistingTitle` 命中 → 直接返回；
    - 否则 → `generateTitle(userText)` 并返回。

3. 改造 `doChatByStream`
- 在创建 `UserMessage` 后、发起流式对话前：
  - 读取 `title = getOrCreateTitle(chatId, message)`；
  - 对当前 `userMessage.getMetadata()`：
    - `put("userId", userId)`（保留现有）
    - `put("title", title)`（新增）
- 保持其余逻辑不变（advisors 的 `ChatMemory.CONVERSATION_ID` 仍使用 `chatId`）。

4. 兼容性与副作用
- 存储层无需改动：`CustomJdbcChatMemoryRepository` 已写入与读取 `metadata`，`MessageRowMapper` 已支持 `title` 回填。
- 性能：每次请求新增一次按会话读取（窗口内）查询，复杂度低；如需进一步优化，可在 `EmotionApp` 中加入 `ConcurrentHashMap<String,String>` 缓存 `conversationId → title`，命中后不再查询。

5. 验证用例
- 新会话首条消息：应生成标题并随该消息落表；列表/详情可看到该标题。
- 同一会话后续消息：不再重新生成，沿用既有标题，并写入到本次消息的 `metadata`，确保窗口滑动后依旧存在标题。
- 旧会话在窗口滑动后：任意一次用户消息发送后，`title` 仍应存在于最新一条用户消息的 `metadata` 中。

## 变更清单
- 修改：`src/main/java/com/lin/linagent/app/EmotionApp.java`
  - 保留并暴露 `CustomJdbcChatMemoryRepository` 为字段
  - 新增 `findExistingTitle`、`generateTitle`、`getOrCreateTitle`
  - 在 `doChatByStream` 中为 `UserMessage.metadata` 写入 `title`
- 可选（增强）：在 `EmotionApp` 增加内存缓存以减少重复查询；增加配置项控制是否用模型生成标题。

## 回滚策略
- 所有变更仅限 `EmotionApp`，删除新增方法与一行 `metadata.put("title", ...)` 即可回滚；不影响表结构与其它组件。

请确认是否按以上方案执行代码修改与验证。