# Smart KB Agent

基于 RAG（Retrieval-Augmented Generation）的智能知识库问答后端。面向本地学习、课程答辩与 Java 秋招项目展示，采用 Spring Boot 单体模块化架构，实现了文档解析、语义分块、向量检索、多轮对话、查询重写、多模型熔断降级和 SSE 流式输出。

> 当前状态：核心代码已完成，80 个测试全部通过，应用可启动（health UP）。已通过 Docker Compose 基础设施端到端验证（注册→建库→文档上传→SSE 聊天环节）。Embedding 和检索型 RAG 链路因缺少 `QWEN_API_KEY` 无法真实验证。

## 核心能力

- 上传并解析 PDF、DOCX、Markdown、TXT 文档（Apache Tika + 类型/大小校验）
- 按段落和句子进行带 100 字重叠的语义分块（SemanticChunker）
- 使用 Qwen Embedding（固定路由）与 Milvus 构建向量检索（HNSW/COSINE）
- 结合最近 2 轮对话完成意图识别与查询重写（LLM 驱动 JSON 解析 + 失败降级）
- 基于检索结果构造受约束的 RAG Prompt
- DeepSeek 主模型失败时通过 Resilience4j CircuitBreaker 降级至 Qwen
- 使用 Redis 保存近期 10 条会话上下文，MySQL 持久化业务数据（含 provider、token 统计）
- 通过 SSE 推送重写、回答片段、引用来源和完成事件
- 会话历史查询（分页 + 资源归属校验）

## 技术栈

Java 17、Spring Boot 3.2.x、MyBatis-Plus 3.5.x、MySQL 8、Redis 7、Milvus 2.4 Standalone、MinIO、Apache Tika、Resilience4j、SpringDoc OpenAPI、Maven、Docker Compose。

## 架构

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
SSE：rewrite → token* → sources → done
```

文档入库链路：

```text
上传 → MinIO 原文件 → MySQL 处理记录 → 异步解析
     → Apache Tika → SemanticChunker → Qwen Embedding → Milvus
```

## 目录结构

```text
smart-kb-agent/
├── AGENTS.md                 # 开发约束与协作规范
├── README.md
├── docker-compose.yml        # MySQL、Redis、Milvus、MinIO 等基础设施
├── pom.xml
├── sql/
│   └── init.sql              # 6 张业务表（DDL + 索引）
├── docs/
│   ├── project-prompt.md
│   ├── audit-report-2026-06-21.md
│   └── implementation-plan.md
├── src/main/java/com/example/smartkb/
│   ├── common/               # Result、BusinessException、GlobalExceptionHandler、UserContext
│   ├── config/               # Redis、MinIO、Milvus、Tika、线程池、WebMVC、MyBatis-Plus
│   ├── user/                 # 注册、登录、JWT（拦截器 + UserContext）
│   ├── kb/                   # 知识库 CRUD + Milvus collection 生命周期
│   ├── document/             # 上传（类型/大小校验）、Tika 解析、语义分块、异步处理、文档清理服务
│   ├── llm/                  # 统一网关（DeepSeek + Qwen）、CircuitBreaker、调用日志
│   ├── search/               # 查询重写、向量检索（Milvus）
│   └── chat/                 # 多轮对话、SSE 流式输出、会话历史
├── src/main/resources/
│   ├── application.yml
│   └── mapper/
└── src/test/java/com/example/smartkb/  # 80 个单元测试
    ├── chat/
    ├── common/
    ├── document/
    ├── kb/
    ├── llm/
    ├── search/
    └── user/
```

## 快速启动

### 前置条件

- JDK 17
- Maven 3.9+
- Docker 与 Docker Compose

### 1. 设置环境变量

```bash
# Linux / macOS
export DEEPSEEK_API_KEY="your-deepseek-api-key"
export QWEN_API_KEY="your-qwen-api-key"      # 必需；缺少时 Embedding 和检索型 RAG 问答不可用
export JWT_SECRET="$(openssl rand -base64 64)"
export MINIO_ACCESS_KEY="minioadmin"
export MINIO_SECRET_KEY="minioadmin"
```

```powershell
# Windows PowerShell
$env:DEEPSEEK_API_KEY="your-deepseek-api-key"
$env:QWEN_API_KEY="your-qwen-api-key"
$env:JWT_SECRET="replace-with-a-long-random-secret"
$env:MINIO_ACCESS_KEY="minioadmin"
$env:MINIO_SECRET_KEY="minioadmin"
```

### 2. 启动基础设施

```bash
docker compose up -d
# 等待所有服务健康（约 30 秒）
docker compose ps
# 所有 6 个服务应显示 (healthy)
```

### 3. 编译与启动

```bash
mvn clean package -DskipTests
java -jar target/smart-kb-agent.jar
```

应用启动后访问：
- 健康检查：http://localhost:8080/actuator/health
- API 文档：http://localhost:8080/swagger-ui.html
- Attu（Milvus 管理）：http://localhost:8000

### 4. 验证主链路

```bash
# 注册
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123456"}'

# 登录（保存 token）
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123456"}' | \
  grep -o '"accessToken":"[^"]*"' | sed 's/"accessToken":"//;s/"//')

# 创建知识库
curl -s -X POST http://localhost:8080/api/kb/create \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"我的知识库"}'

# SSE 流式问答（GPT/DeepSeek 对话）
curl -s -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"kbId":1,"question":"hello"}'
```

### 常见问题

| 问题 | 解决 |
|------|------|
| MinIO 启动报 unhealthy | docker-compose.yml 已修复：使用 `mc ready local` 代替 `curl` 做健康检查 |
| 中文请求体报 JSON 解析错误 | 确保客户端以 UTF-8 编码发送（Windows curl 默认 GBK，需添加 `charset=utf-8` 头） |
| 文档上传后状态一直"处理中" | 检查是否有 QWEN_API_KEY；如果没有，Embedding 步骤会失败 |
| SSE 流输出 error + done | 检查 DeepSeek API Key 是否有效，以及网络能否访问 api.deepseek.com |

## 核心接口

| 功能 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 注册 | POST | `/api/auth/register` | 创建用户，BCrypt 加密密码 |
| 登录 | POST | `/api/auth/login` | 返回 accessToken（15m）与 refreshToken（7d） |
| 刷新令牌 | POST | `/api/auth/refresh` | 使用 refreshToken 换取新令牌 |
| 创建知识库 | POST | `/api/kb/create` | 同时创建 Milvus collection `kb_{id}` |
| 知识库列表 | GET | `/api/kb/list` | 当前用户的知识库 |
| 删除知识库 | DELETE | `/api/kb/{id}` | 逻辑删除 + 清理 Milvus collection |
| 上传文档 | POST | `/api/document/upload` | 校验类型/大小 → MinIO → 异步解析入库 |
| 文档列表 | GET | `/api/document/list?kbId={id}` | 文档及处理状态 |
| 删除文档 | DELETE | `/api/document/{id}` | 清理 MinIO + Milvus + MySQL |
| 流式问答 | POST | `/api/chat/stream` | SSE 事件流：`rewrite → token* → sources → done` |
| 会话列表 | GET | `/api/chat/conversations?kbId={id}` | 分页查询当前用户会话 |
| 消息历史 | GET | `/api/chat/messages/{conversationId}` | 分页查询会话消息 |

普通 HTTP 接口统一返回 `Result<T>`；SSE 接口使用 `text/event-stream` 协议。

## 安全说明

- 密码使用 BCrypt 加密存储。
- JWT 采用**无状态策略**：Access Token（15 分钟）+ Refresh Token（7 天），类型分离，不存储于 Redis。
- 令牌撤销可通过引入 Redis 白名单/版本号实现，当前版本为简化本地运行和面试展示保持无状态。
- 所有密钥和 API Key 从环境变量读取，不提交到代码仓库。

## 项目边界

首版不实现 Spring Cloud Gateway、Nacos、多模块 Maven、复杂 RBAC、Cohere/BGE 重排序、历史自动摘要和 Kubernetes 部署。它们只作为后续扩展方向，不应进入首版主链路。

## 简历亮点

- 基于 RAG 架构实现从文档入库、向量召回到答案生成的完整链路。
- 设计按段落与句子切分、支持 100 字窗口重叠的语义分块策略。
- 使用 Qwen Embedding 和 Milvus（HNSW/COSINE）完成语义检索及文档来源补全。
- 通过 LLM 驱动的查询重写解决多轮对话中的指代消解和检索意图判断。
- 基于 Resilience4j 实现 DeepSeek → Qwen 的熔断与自动降级，全部失败返回友好提示。
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
| 流式输出时多模型降级如何避免回答错乱？ | 流式场景下不能无脑做多模型降级重试。如果 DeepSeek 已经吐出部分 token 给前端，此时切换 Qwen 重新生成会导致用户看到重复内容或语气/结论不一致的拼接回答。方案是维护请求级 hasEmittedAnyToken 局部状态：首 token 前可无感知降级，首 token 后只报错不重试，引导用户重新提问。 |
| 检索不到相关内容怎么办？ | 设置相关性门槛，明确返回知识库无相关信息，禁止模型自行编造。 |

## 后续优化方向

- 在召回后增加可插拔的 Rerank 层（Cohere / BGE）。
- 增加文档处理任务重试、补偿与可观测指标。
- 对长会话增加摘要压缩和分层记忆。
- 增加基于 Testcontainers 的基础设施集成测试。


