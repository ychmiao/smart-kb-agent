# Smart KB Agent 功能验收清单

> 需求基线：`docs/project-prompt.md`  
> 最新核对日期：2026-06-21  
> 核对范围：仓库当前全部业务代码、配置、SQL、README 与测试代码（排除 `.git`、`target` 和 IDE 元数据）。  
> 状态口径：**已实现**表示仓库中存在完整的对应实现；**部分实现**表示主流程存在但明确缺少需求环节；**未实现**表示未找到对应实现。代码存在不等于已完成外部依赖联调。

## 验证结果摘要

| 项目 | 结果 |
|---|---|
| `docker compose config --quiet` | 通过 |
| `mvn clean package -DskipTests` | 通过（87 生产源文件 + 新增 ErrorCode.java） |
| `mvn test` | 80 个测试全部通过 |
| `mvn spring-boot:run` + `/actuator/health` | UP |
| Docker Desktop 全部 6 服务 healthy | 已验证 |
| 端到端非 Embedding 链路（注册→建库→登录→SSE 闲聊） | 通过 |
| Maven 使用的 JDK | Java 17.0.18 |

## 1. 基础项目结构

- **是否已实现**：**已实现**。
- **证据**：`pom.xml`、`SmartKbAgentApplication.java`、全部 8 个领域分包（common/config/user/kb/document/llm/search/chat）。
- **当前缺失点**：无结构性缺失。

## 2. Maven 依赖

- **是否已实现**：**已实现**。
- **证据**：`pom.xml` 声明完整。
- **当前缺失点**：无。

## 3. application.yml 配置

- **是否已实现**：**已实现**。
- **证据**：`application.yml` + 5 个 `@ConfigurationProperties` 绑定类。
- **当前缺失点**：未启动真实基础设施做属性绑定测试。

## 4. docker-compose.yml

- **是否已实现**：**已实现**。
- **证据**：`docker-compose.yml`、`sql/init.sql`；`docker compose config --quiet` 通过。
- **当前缺失点**：无。

## 5. sql/init.sql

- **是否已实现**：**已实现**。
- **证据**：6 张表及索引已在真实 MySQL 8 上执行并检验。
- **当前缺失点**：无。

## 6. common 通用模块

- **是否已实现**：**已实现**。
- **证据**：`Result`、`BusinessException`、`GlobalExceptionHandler`、`UserContext`、`ErrorCode`。
- **当前缺失点**：错误码已集中为 `ErrorCode` 常量类；现有业务代码仍使用散落整数，可后续逐步迁移。

## 7. user 登录注册和 JWT

- **是否已实现**：**已实现**。
- **证据**：`AuthController`、`UserServiceImpl`、`JwtUtils`、`JwtAuthenticationInterceptor`、`WebMvcConfig`。
- **测试**：JwtUtilsTest（10 个：生成/解析、类型混用、过期、无效签名、多用户）。
- **当前缺失点**：没有 AuthController MockMvc 集成测试（需要完整 Spring 上下文）。

## 8. kb 知识库模块

- **是否已实现**：**已实现**。
- **证据**：`KnowledgeBaseController`、`KnowledgeBaseServiceImpl`；创建时立即创建 `kb_{kbId}` collection；列表/删除/归属校验完整。
- **测试**：KnowledgeBaseServiceImplTest（10 个：创建/失败、删除/顺序/Milvus 失败/MySQL 失败/不存在/处理中阻塞/MinIO 失败/幂等）。
- **当前缺失点**：无。

## 9. document 文档上传模块

- **是否已实现**：**已实现**。
- **证据**：`DocumentController`、`DocumentServiceImpl`；上传校验、MinIO 上传、异步处理、列表、删除。
- **测试**：DocumentServiceImplTest（6 个）+ DocumentCleanupServiceImplTest（10 个）。
- **当前缺失点**：无 MockMvc 集成测试。

## 10. Tika 文档解析

- **是否已实现**：**已实现**。
- **证据**：`TikaConfig`、`DocumentProcessingService` 真实调用 `tika.parseToString()`。
- **测试**：DocumentProcessingServiceTest（2 个：完成/失败路径）。
- **当前缺失点**：没有使用真实文档文件的集成测试。

## 11. SemanticChunker 文本分块

- **是否已实现**：**已实现**。
- **证据**：`SemanticChunker`（段落优先/句末边界/500 字/100 重叠/sourceText）。
- **测试**：SemanticChunkerTest（9 个：长度/重叠/段落/中文句号/英文句号/空/空白/sourceText/配置校验）。
- **当前缺失点**：无。

## 12. MinIO 文件存储

- **是否已实现**：**已实现**。
- **证据**：`MinioFileStorage`（上传/严格删除/静默补偿删除/下载/bucket 自动创建）。
- **当前缺失点**：没有 MinIO 集成测试；`removeQuietly` 仅用于上传后补偿，正常删除使用 `remove()`。

## 13. Redis 缓存

- **是否已实现**：**已实现**。
- **证据**：`ChatHistoryService`（最近 10 条/7 天 TTL/回源 MySQL）。
- **当前缺失点**：没有 Redis 集成测试。

## 14. LLM 统一网关

- **是否已实现**：**已实现**。
- **证据**：`LlmGatewayService`、`LlmGatewayServiceImpl`、`OpenAiCompatibleClient`。
- **测试**：LlmGatewayServiceImplTest（6 个）+ OpenAiCompatibleClientTest（6 个 MockWebServer 协议级测试）。
- **当前缺失点**：无真实供应商联调（依赖 API Key）。

## 15. Qwen Embedding

- **是否已实现**：**已实现**（代码存在，因缺少 QWEN_API_KEY 无法真实验证）。
- **证据**：`LlmGatewayServiceImpl.embedding()` 固定路由 Qwen。
- **测试**：MockWebServer 测试覆盖 Embedding 请求/响应/HTTP 错误。
- **当前缺失点**：无真实模型调用。

## 16. DeepSeek / Qwen Chat

- **是否已实现**：**已实现**（代码存在，Chat 真实链路已验证；Chat 已通过 DeepSeek 主链路验证）。
- **证据**：`LlmGatewayServiceImpl`（DeepSeek → Qwen 降级/同步/流式）。
- **测试**：同步/流式降级、双失败、CB 开路跳过。
- **当前缺失点**：流式首 token 后中断降级未以真实模型验证。

## 17. Milvus 向量入库

- **是否已实现**：**已实现**（代码存在，因缺少 QWEN_API_KEY 无法完整验证）。
- **证据**：`MilvusVectorStore`（create/insert/search/delete/drop collection）。
- **当前缺失点**：无真实 Milvus 集成测试。

## 18. RetrievalService 向量检索

- **是否已实现**：**已实现**（代码存在，因缺少 QWEN_API_KEY 无法完整验证）。
- **证据**：`RetrievalServiceImpl`（Embedding → Milvus 检索 → 补文件名）。
- **测试**：RetrievalServiceImplTest（4 个：成功/空结果/缺失文件名/越权）。
- **当前缺失点**：无真实 Embedding + Milvus 集成测试。

## 19. QueryRewriteService 查询重写

- **是否已实现**：**已实现**。
- **证据**：`QueryRewriteServiceImpl`（LLM JSON 严格解析/最近 2 轮/失败降级）。
- **测试**：QueryRewriteServiceImplTest（4 个：指代/闲聊/JSON 无效/模型失败）。
- **当前缺失点**：未以真实模型验证 JSON 遵循率。

## 20. chat SSE 流式问答

- **是否已实现**：**已实现**。
- **证据**：`ChatController` `/api/chat/stream`、`ChatServiceImpl`（rewrite → token* → sources → done）。
- **测试**：ChatServiceImplTest（5 个：检索/闲聊/全供应商失败/BusinessException/通用异常→error+done）。
- **当前缺失点**：无真实 SSE 集成测试。

## 21. MySQL 消息持久化

- **是否已实现**：**已实现**。
- **证据**：`ChatPersistenceService`（异步/事务保存 user+assistant/`llm_provider`/`token_count`）。
- **当前缺失点**：无持久化集成测试。

## 22. kb_llm_call_log 模型调用日志

- **是否已实现**：**已实现**。
- **证据**：`LlmCallLogService.recordAsync()`（异步/含 requestId、类型、供应商、状态、耗时、脱敏错误）。
- **当前缺失点**：无集成测试。

## 23. Resilience4j 熔断降级

- **是否已实现**：**已实现**。
- **证据**：application.yml + `CircuitBreakerOperator` + `executeSupplier`。
- **测试**：LlmGatewayServiceImplTest 覆盖同步/流式 CB 跳过+降级。
- **当前缺失点**：未验证配置阈值与半开恢复真实行为。

## 24. README 文档

- **是否已实现**：**已实现**。
- **证据**：README.md（项目介绍/架构图/启动步骤/接口表/安全说明/简历亮点/面试问题）。
- **当前缺失点**：无。

## 25. 项目编译和启动验证

- **是否已实现**：**部分完成**（非 Embedding 链路已验证；Embedding 链路需 QWEN_API_KEY）。
- **证据**：
  - `mvn clean package -DskipTests` → BUILD SUCCESS
  - `mvn test` → 80 tests, 0 failures, 0 errors
  - Docker Compose 6/6 服务 healthy
  - 应用启动 → `/actuator/health` → UP
  - 端到端：注册→登录→建库→SSE 闲聊已通过
- **当前缺失点**：含 Embedding 的完整主链路无法验证（缺 QWEN_API_KEY）。
