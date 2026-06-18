# AGENTS.md

## 项目定位

`smart-kb-agent` 是一个面向学习、答辩和 Java 秋招展示的 RAG 智能知识库后端。项目采用 Spring Boot 单体模块化架构，优先保证主链路完整、结构清晰、本地可运行和便于讲解，避免为了“企业级”而过度设计。

所有实现应以 `docs/project-prompt.md` 为需求基线；需求冲突时，优先遵循用户当次明确指令，其次遵循本文件和项目提示。

## 技术栈

| 领域 | 选型 |
|---|---|
| 语言与运行时 | Java 17 |
| 后端框架 | Spring Boot 3.2.x |
| 构建工具 | Maven，单模块 |
| ORM | MyBatis-Plus 3.5.x |
| 关系数据库 | MySQL 8 |
| 缓存 | Redis 7 |
| 向量数据库 | Milvus 2.4 Standalone |
| 对象存储 | MinIO |
| 文档解析 | Apache Tika |
| AI 接口 | OpenAI 兼容协议；DeepSeek 主对话模型，Qwen 备用对话及 Embedding 模型 |
| 容错 | Resilience4j CircuitBreaker |
| 流式响应 | Spring WebFlux `Flux` + SSE |
| API 文档 | SpringDoc OpenAPI |
| 本地依赖编排 | Docker Compose |

## 架构与目录

项目使用单体应用、按业务领域分包，不拆分多模块 Maven。

```text
smart-kb-agent/
├── AGENTS.md                    # 开发约束与协作规范
├── README.md                    # 项目说明、运行方式和路线图
├── docs/                        # 需求及设计文档
├── sql/                         # MySQL 初始化脚本
├── src/main/java/com/example/smartkb/
│   ├── common/                  # 响应模型、异常、常量、上下文、通用工具
│   ├── config/                  # 基础设施、线程池、安全与容错配置
│   ├── user/                    # 注册、登录、JWT 与用户上下文
│   ├── kb/                      # 知识库生命周期管理
│   ├── document/                # 上传、解析、分块、向量化与入库
│   ├── llm/                     # 模型供应商适配与统一调用网关
│   ├── search/                  # 查询重写、意图识别和向量检索
│   └── chat/                    # 会话、消息、RAG 编排与 SSE
├── src/main/resources/
│   └── mapper/                  # 必要的 MyBatis XML 映射
└── src/test/java/com/example/smartkb/ # 与生产包对应的测试
```

每个业务包内部按需要使用 `controller`、`service`、`mapper`、`entity`、`dto`、`vo` 等子包。不要预先创建没有实际用途的抽象层。

## Java 代码规范

- 包名全小写，类名使用 UpperCamelCase，方法和变量使用 lowerCamelCase，常量使用 UPPER_SNAKE_CASE。
- 所有源文件使用 UTF-8、4 空格缩进；禁止通配符 import，禁止省略 import。
- 实体类使用 Lombok；DTO/VO 可按可读性使用 Lombok，但不要用 `@Data` 掩盖不应公开的可变状态。
- Controller 只负责协议适配、参数校验和响应转换，不承载业务流程；请求 DTO 使用 Bean Validation，并在入口添加 `@Valid`。
- 普通 HTTP 接口统一返回 `Result<T>`；SSE 接口按事件流协议返回，不包裹为普通 JSON 响应。
- 异常统一交给 `GlobalExceptionHandler`；业务层抛出有明确错误码和语义的异常，不吞异常。
- MyBatis-Plus 使用 `BaseMapper`、`IService`、`ServiceImpl`；复杂查询才使用 XML，避免把业务规则写进 SQL。
- 涉及多次数据库写入且要求原子性的方法必须声明事务；异步任务不能假设继承调用方事务。
- 文档处理与 LLM 调用日志使用命名线程池异步执行；禁止直接使用公共 `ForkJoinPool`。
- 关键流程记录结构化日志，至少包含 `requestId` 和关键资源 ID；不得记录密码、JWT、API Key、完整敏感文档或超长 Prompt。
- 业务代码只能依赖 `LlmGatewayService`，不得直接调用具体模型供应商。
- 配置使用 `@ConfigurationProperties` 绑定；密钥只从环境变量读取，不提交真实凭据。
- 时间、状态、角色和事件类型避免魔法值，使用常量或枚举表达。
- 新增功能必须配套测试；文本分块、查询重写降级、模型路由和权限归属校验优先写单元测试。

## API 与数据约定

- API 前缀统一为 `/api`，资源命名保持一致，不随意混用不同风格。
- 用户只能访问本人知识库、文档和会话；所有按 ID 操作都必须校验资源归属。
- Access Token 有效期 15 分钟，Refresh Token 有效期 7 天；密码使用 BCrypt。
- 文档仅支持 `pdf`、`docx`、`md`、`txt`，最大 50 MB；扩展名与内容类型均需校验。
- 文档状态固定为处理中、完成、失败，失败原因需可诊断但不能泄露敏感信息。
- Milvus collection 命名为 `kb_{kbId}`；删除知识库或文档时要考虑 MySQL、MinIO、Milvus 间的一致性和补偿。
- Chat 优先 DeepSeek，失败或熔断后降级 Qwen；Embedding 固定使用 Qwen；全部失败时抛出 `AllLlmProviderFailedException`。
- SSE 事件顺序为 `rewrite`、零到多个 `token`、`sources`、`done`；异常事件需有稳定格式并正常结束连接。

## 提交前检查

- 使用 JDK 17 完成编译和测试：`mvn clean verify`。
- 启动依赖后验证健康检查、认证、知识库、文档处理和流式问答主链路。
- 检查配置示例、数据库脚本、Docker Compose 与 README 命令保持同步。
- 检查日志和 Git 变更中不存在密钥、令牌、个人数据或大体积生成文件。
- 对新增公共接口补充 OpenAPI 注解或可被 SpringDoc 正确推导的模型信息。

## 禁止事项

- 禁止拆成多模块 Maven，禁止引入 Spring Cloud Gateway、Nacos 或微服务基础设施。
- 首版禁止引入 Cohere Rerank、BGE Reranker、复杂 RBAC、历史自动摘要、Kubernetes。
- 禁止业务代码绕过 `LlmGatewayService` 直连 DeepSeek/Qwen。
- 禁止提交硬编码密码、API Key、JWT Secret 或本地绝对路径。
- 禁止空实现、伪代码、`TODO`、`FIXME`、静默捕获异常和仅为通过编译而返回假数据。
- 禁止在 Controller 堆积业务逻辑，禁止跨领域直接操作其他模块 Mapper。
- 禁止无边界重试；重试、熔断和超时必须显式配置，且避免放大下游故障。
- 禁止把完整文档内容、用户问题、模型密钥或 Token 打入日志。
- 禁止未经评估添加重量级依赖、重复工具库或与当前需求无关的基础设施。

