# Smart KB Agent

基于 RAG（Retrieval-Augmented Generation）的智能知识库问答后端。项目面向本地学习、课程答辩与 Java 秋招项目展示，采用 Spring Boot 单体模块化架构，计划实现文档解析、语义分块、向量检索、多轮对话、查询重写、多模型熔断降级和 SSE 流式输出。

> 当前状态：项目规范与目录骨架已建立，业务功能尚未实现。本文中的接口和启动命令是后续实现目标，不代表当前版本已经可运行。

## 核心能力

- 上传并解析 PDF、DOCX、Markdown、TXT 文档
- 按段落和句子进行带重叠的语义分块
- 使用 Qwen Embedding 与 Milvus 构建向量检索
- 结合最近对话完成意图识别与查询重写
- 基于检索结果构造受约束的 RAG Prompt
- DeepSeek 主模型失败时通过 Resilience4j 降级至 Qwen
- 使用 Redis 保存近期会话上下文，MySQL 持久化业务数据
- 通过 SSE 推送重写、回答片段、引用来源和完成事件

## 技术栈

Java 17、Spring Boot 3.2.x、MyBatis-Plus 3.5.x、MySQL 8、Redis 7、Milvus 2.4、MinIO、Apache Tika、Resilience4j、SpringDoc OpenAPI、Maven、Docker Compose。

## 目标架构

```text
用户
  ↓
Spring Boot API
  ↓
ChatService ───────────────→ Redis / MySQL
  ↓
QueryRewriteService
  ↓（需要检索）
RetrievalService → Qwen Embedding → Milvus
  ↓
RAG Prompt 构造
  ↓
LlmGatewayService
  ├── DeepSeek（主模型）
  └── Qwen（熔断降级）
  ↓
SSE：rewrite → token → sources → done
```

文档入库链路：

```text
上传 → MinIO 原文件 → MySQL 处理记录 → 异步解析
     → Apache Tika → SemanticChunker → Qwen Embedding → Milvus
```

## 目录结构

```text
smart-kb-agent/
├── AGENTS.md
├── README.md
├── docs/
│   ├── project-prompt.md
│   └── implementation-plan.md
├── sql/
├── src/main/java/com/example/smartkb/
│   ├── common/
│   ├── config/
│   ├── user/
│   ├── kb/
│   ├── document/
│   ├── llm/
│   ├── search/
│   └── chat/
├── src/main/resources/mapper/
└── src/test/java/com/example/smartkb/
```

各空目录使用 `.gitkeep` 保留；进入对应开发阶段后应删除占位文件。

## 规划中的快速启动方式

前置条件：JDK 17、Maven 3.9+、Docker 与 Docker Compose。

PowerShell 环境变量示例：

```powershell
$env:DEEPSEEK_API_KEY="your-key"
$env:QWEN_API_KEY="your-key"
$env:JWT_SECRET="replace-with-a-long-random-secret"
```

完成基础设施和应用配置后，计划使用以下命令启动：

```bash
docker compose up -d
mvn clean package -DskipTests
java -jar target/smart-kb-agent.jar
```

## 规划中的核心接口

| 功能 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 注册 | POST | `/api/auth/register` | 创建用户并 BCrypt 加密密码 |
| 登录 | POST | `/api/auth/login` | 返回 accessToken 与 refreshToken |
| 刷新令牌 | POST | `/api/auth/refresh` | 使用 refreshToken 换取新令牌 |
| 创建知识库 | POST | `/api/kb/create` | 创建知识库及对应 Milvus collection |
| 知识库列表 | GET | `/api/kb/list` | 查询当前用户的知识库 |
| 上传文档 | POST | `/api/document/upload` | 上传至 MinIO 并异步解析入库 |
| 文档列表 | GET | `/api/document/list?kbId={id}` | 查看知识库文档及处理状态 |
| 流式问答 | POST | `/api/chat/stream` | 返回 `text/event-stream` |
| 会话历史 | GET | 待实现阶段确定 | 查看持久化会话消息 |

普通 HTTP 接口统一返回 `Result<T>`；SSE 接口使用事件流协议。

## 实现路线图

详细阶段、验收标准与依赖关系见 [docs/implementation-plan.md](docs/implementation-plan.md)。整体顺序为：工程基线 → 基础设施 → 认证与知识库 → 文档入库 → LLM 网关 → 检索重写 → 流式对话 → 联调收尾。

## 项目边界

首版不实现 Spring Cloud Gateway、Nacos、多模块 Maven、复杂 RBAC、Cohere/BGE 重排序、历史自动摘要和 Kubernetes 部署。它们只作为后续扩展方向，不应进入首版主链路。

## 预期简历亮点

- 基于 RAG 架构实现从文档入库、向量召回到答案生成的完整链路。
- 设计按段落与句子切分、支持窗口重叠的文本分块策略。
- 使用 Qwen Embedding 和 Milvus 完成语义检索及文档来源补全。
- 通过查询重写解决多轮对话中的指代消解和检索意图判断。
- 基于 Resilience4j 实现 DeepSeek 到 Qwen 的熔断与自动降级。
- 基于 SSE 实现重写结果、回答 token 与引用来源的分阶段流式响应。

## 面试可讲点

| 问题 | 回答方向 |
|---|---|
| 为什么使用 RAG？ | 将模型生成约束在私有、可更新、可引用的知识范围内，降低幻觉。 |
| 为什么需要文本分块？ | Embedding 和上下文窗口有限；分块能提高语义粒度与召回质量。 |
| chunk 太大或太小有什么问题？ | 太大会混合主题并浪费上下文，太小会丢失语义；通过长度、边界和重叠权衡。 |
| 为什么需要查询重写？ | 将依赖上下文的指代问题改成独立检索语句，提高向量召回准确度。 |
| DeepSeek 不提供 Embedding 怎么办？ | 统一网关将对话和向量化能力分离，Embedding 固定路由到 Qwen。 |
| 熔断和降级如何配合？ | 熔断器快速跳过不健康供应商，网关再按优先级调用备用供应商。 |
| SSE 和 WebSocket 如何选择？ | 本项目以服务端单向增量输出为主，SSE 协议更简单并支持 HTTP 语义。 |
| 检索不到相关内容怎么办？ | 设置相关性门槛，明确返回知识库无相关信息，禁止模型自行编造。 |

## 后续优化方向

- 在召回后增加可插拔的 Rerank 层。
- 增加文档处理任务重试、补偿与可观测指标。
- 对长会话增加摘要压缩和分层记忆。
- 增加基于 Testcontainers 的基础设施集成测试。

