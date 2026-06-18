# 基于 RAG 的智能知识库问答 Agent 系统项目生成提示词

你是一名资深 Java 后端工程师，请帮我生成一个适合 Java 秋招简历展示的 **基于 RAG 的智能知识库问答 Agent 系统**。

项目要求：代码工程化程度高，能够本地运行，适合写进简历和面试讲解。不要做成过度复杂的企业级微服务系统，而是采用 **Spring Boot 单体模块化架构**，保证核心功能完整、代码清晰、可运行、可扩展。

---

## 一、项目基本信息

项目名：`smart-kb-agent`

项目定位：

> 一个基于 RAG 架构的智能知识库问答系统，支持文档上传、文档解析、文本分块、向量化入库、语义检索、多轮对话、查询重写、SSE 流式输出，以及 DeepSeek 主模型 + 通义千问备用模型的熔断降级。

技术栈：

| 类型 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.2.x |
| Java 版本 | JDK 17 |
| ORM | MyBatis-Plus 3.5.x |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7 |
| 向量数据库 | Milvus 2.4 |
| 文件存储 | MinIO |
| 文档解析 | Apache Tika |
| AI 调用 | OpenAI 兼容接口，DeepSeek + 通义千问 |
| 熔断降级 | Resilience4j |
| 流式输出 | SSE |
| 构建工具 | Maven |
| 接口文档 | SpringDoc OpenAPI |

---

## 二、项目结构

请生成如下项目结构：

```text
smart-kb-agent/
├── pom.xml
├── docker-compose.yml
├── README.md
├── sql/
│   └── init.sql
└── src/
    └── main/
        ├── java/com/example/smartkb/
        │   ├── SmartKbAgentApplication.java
        │   │
        │   ├── common/              # 通用响应、异常、常量、工具类
        │   ├── config/              # Redis、MinIO、Milvus、线程池、Resilience4j 配置
        │   ├── user/                # 简单用户登录注册、JWT
        │   ├── kb/                  # 知识库管理
        │   ├── document/            # 文档上传、解析、分块、入库
        │   ├── llm/                 # 统一大模型调用网关
        │   ├── search/              # 查询重写、向量检索、结果封装
        │   └── chat/                # 多轮对话、SSE 流式输出、历史管理
        │
        └── resources/
            ├── application.yml
            └── mapper/
```

注意：不要使用多模块 Maven。为了方便本地运行和面试讲解，采用单体项目 + 分包设计。

---

## 三、核心功能要求

### 1. 用户模块

实现简单用户认证即可，不要做复杂权限系统。

接口：

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
```

要求：

- 用户密码使用 BCrypt 加密；
- 登录成功返回 accessToken 和 refreshToken；
- accessToken 有效期 15 分钟；
- refreshToken 有效期 7 天；
- 登录后通过 JWT 解析 userId；
- 使用拦截器将 userId 保存到 UserContext。

---

### 2. 知识库模块

接口：

```text
POST /api/kb/create
GET /api/kb/list
DELETE /api/kb/{id}
```

知识库字段：

```sql
id
name
description
user_id
collection_name
create_time
update_time
is_deleted
```

要求：

- 每个用户可以创建多个知识库；
- 每个知识库对应一个 Milvus collection；
- collection_name 格式为：`kb_{kbId}`。

---

### 3. 文档管理模块

接口：

```text
POST /api/document/upload
GET /api/document/list?kbId=1
DELETE /api/document/{id}
```

上传参数：

```text
MultipartFile file
Long kbId
```

文档上传处理流程：

1. 校验文件类型，仅支持 pdf、docx、md、txt；
2. 校验文件大小，最大 50MB；
3. 上传原始文件到 MinIO；
4. 在 MySQL 中插入文档记录，状态为“处理中”；
5. 使用 Spring 事件或者线程池异步处理文档；
6. 使用 Apache Tika 解析文档文本；
7. 调用 SemanticChunker 进行文本分块；
8. 调用 Embedding 模型生成向量；
9. 将 chunk 和向量写入 Milvus；
10. 更新文档状态为“完成”或者“失败”。

文档状态：

```text
0 处理中
1 处理完成
2 处理失败
```

---

### 4. 文本分块模块

实现 `SemanticChunker`。

分块要求：

- 优先按照段落分割；
- 如果段落过长，再按照句号、问号、叹号分割；
- 每个 chunk 最大长度默认 500 字；
- 相邻 chunk 保留 100 字重叠；
- 每个 chunk 保存以下元数据：

```java
documentId
kbId
chunkIndex
content
sourceText
```

sourceText 为 chunk 前 50 个字，用于前端展示引用来源。

---

### 5. 大模型统一网关模块

实现 `LlmGatewayService`，业务代码禁止直接调用 DeepSeek 或通义千问 API。

接口设计：

```java
public interface LlmGatewayService {

    String chat(String requestId, String prompt);

    Flux<String> streamChat(String requestId, String prompt);

    List<Double> embedding(String requestId, String text);
}
```

支持两个模型供应商：

```text
DeepSeek：主模型，用于对话生成
Qwen：备用模型，用于对话生成和 Embedding
```

配置示例：

```yaml
kb:
  llm:
    providers:
      deepseek:
        base-url: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY}
        chat-model: deepseek-chat
        priority: 1
      qwen:
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        api-key: ${QWEN_API_KEY}
        chat-model: qwen-plus
        embedding-model: text-embedding-v3
        priority: 2
```

调用策略：

1. Chat 优先调用 DeepSeek；
2. DeepSeek 调用失败时，自动降级到 Qwen；
3. Embedding 固定使用 Qwen；
4. 所有模型调用都要记录日志到 `kb_llm_call_log` 表；
5. 日志记录异步执行，避免影响主流程。

---

### 6. Resilience4j 熔断降级

对 DeepSeek 和 Qwen 分别配置 CircuitBreaker。

配置要求：

```yaml
resilience4j:
  circuitbreaker:
    instances:
      deepseek:
        failure-rate-threshold: 50
        sliding-window-size: 10
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 60s
      qwen:
        failure-rate-threshold: 50
        sliding-window-size: 10
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 60s
```

要求实现：

- 当 DeepSeek 连续失败或失败率超过阈值时，熔断；
- DeepSeek 熔断期间直接跳过 DeepSeek，调用 Qwen；
- 如果所有模型都失败，抛出 `AllLlmProviderFailedException`；
- 上层返回友好提示：`AI 服务暂时不可用，请稍后再试`。

---

### 7. 查询重写与意图识别

实现 `QueryRewriteService`。

接口：

```java
public QueryRewriteResult rewrite(String question, List<ChatMessage> recentHistory);
```

返回对象：

```java
@Data
public class QueryRewriteResult {
    private Boolean needRetrieval;
    private String rewrittenQuery;
}
```

功能要求：

- 判断当前问题是否需要检索知识库；
- 如果是问候、感谢、闲聊，`needRetrieval=false`；
- 如果问题包含“它、这个、刚才那个、上面说的”等指代词，需要结合最近 2 轮对话改写成完整问题；
- 如果问题本身完整，`rewrittenQuery` 等于原问题；
- 要求大模型严格返回 JSON；
- JSON 解析失败时降级为：

```java
needRetrieval = true
rewrittenQuery = 原始问题
```

Prompt 模板：

```text
你是一个查询理解助手，需要完成两个任务：
1. 判断用户最新问题是否需要检索知识库才能回答；
2. 如果需要检索，请结合历史对话将问题改写为语义完整、适合向量检索的独立问题。

历史对话：
{history}

用户最新问题：
{question}

判断规则：
- 问候、感谢、闲聊，不需要检索，needRetrieval=false；
- 包含“它、这个、刚才那个、上面说的”等指代词时，需要结合历史补全；
- 问题本身清晰完整时，rewrittenQuery 直接使用原问题。

请严格按照 JSON 格式输出，不要输出 Markdown，不要输出解释：

{
  "needRetrieval": true,
  "rewrittenQuery": "改写后的问题"
}
```

---

### 8. 向量检索模块

实现 `RetrievalService`。

接口：

```java
public List<RetrievedChunk> retrieve(Long kbId, String query, Integer topK);
```

流程：

1. 调用 `LlmGatewayService.embedding()` 将查询文本向量化；
2. 在 Milvus collection `kb_{kbId}` 中检索 TopK；
3. 查询 MySQL，补充文档名称；
4. 返回检索结果。

返回对象：

```java
@Data
public class RetrievedChunk {
    private Long docId;
    private Integer chunkIndex;
    private String content;
    private String sourceText;
    private Double score;
    private String fileName;
}
```

注意：第一版不要接入 Cohere Rerank 或 BGE Reranker。可以在 README 的“后续优化”中说明支持扩展重排序模块。

---

### 9. 对话模块

实现流式问答接口：

```text
POST /api/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

请求体：

```json
{
  "conversationId": 1,
  "kbId": 1,
  "question": "这个功能怎么用？"
}
```

如果 `conversationId` 为空，则自动创建新会话。

处理流程：

1. 从 Redis 获取最近 10 条历史消息；
2. 调用 `QueryRewriteService` 做查询重写和意图判断；
3. 通过 SSE 先返回 rewrite 事件；
4. 如果 `needRetrieval=true`，调用 `RetrievalService` 检索 Top5 文档片段；
5. 构造 RAG Prompt；
6. 调用 `LlmGatewayService.streamChat()` 生成流式回答；
7. 通过 SSE 逐段返回 token；
8. 返回 sources 事件，包含引用文档；
9. 返回 done 事件；
10. 异步保存用户问题和 AI 回答到 MySQL；
11. 更新 Redis 对话历史。

SSE 格式：

```text
data: {"type":"rewrite","needRetrieval":true,"rewrittenQuery":"..."}
data: {"type":"token","content":"这是"}
data: {"type":"token","content":"回答"}
data: {"type":"sources","sources":[{"docId":1,"fileName":"xxx.pdf","excerpt":"..."}]}
data: {"type":"done"}
```

RAG Prompt：

```text
你是一个专业的知识库问答助手。
请严格基于下面的参考文档回答用户问题。
如果参考文档中没有相关信息，请回答：
“根据现有知识库，无法找到相关信息。”
不要编造内容。

参考文档：
{chunks}

历史对话：
{history}

用户问题：
{question}

请在回答末尾用【参考来源：文档名】标明引用来源。
```

闲聊 Prompt：

```text
你是一个友好的智能助手。
当前问题不需要查询知识库，请直接自然地回答用户。

历史对话：
{history}

用户问题：
{question}
```

---

## 四、数据库设计

请生成 `sql/init.sql`。

### kb_user

```sql
CREATE TABLE kb_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);
```

### kb_knowledge_base

```sql
CREATE TABLE kb_knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    user_id BIGINT NOT NULL,
    collection_name VARCHAR(100),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);
```

### kb_document

```sql
CREATE TABLE kb_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(20),
    file_size BIGINT,
    minio_path VARCHAR(500),
    chunk_count INT DEFAULT 0,
    status TINYINT DEFAULT 0,
    error_msg TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);
```

### kb_conversation

```sql
CREATE TABLE kb_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);
```

### kb_message

```sql
CREATE TABLE kb_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    source_docs JSON,
    rewritten_query VARCHAR(500),
    need_retrieval TINYINT DEFAULT 1,
    llm_provider VARCHAR(20),
    token_count INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### kb_llm_call_log

```sql
CREATE TABLE kb_llm_call_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64),
    call_type VARCHAR(20),
    provider_name VARCHAR(20),
    is_success TINYINT,
    latency_ms INT,
    error_msg TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## 五、docker-compose.yml

生成 `docker-compose.yml`，包含：

```text
MySQL 8
Redis 7
Milvus standalone 2.4
etcd
MinIO
Attu
```

要求：

- MySQL 端口：3306；
- Redis 端口：6379，密码 `kb123456`；
- Milvus 端口：19530、9091；
- MinIO 端口：9000、9001；
- Attu 端口：8000；
- 所有服务加入同一网络 `kb-net`；
- MySQL 启动时执行 `./sql/init.sql`；
- MinIO 自动创建 `documents` 和 `milvus` 两个 bucket。

---

## 六、代码规范要求

请严格遵守以下要求：

1. 所有实体类使用 Lombok；
2. Controller 参数使用 `@Valid` 校验；
3. 所有接口统一返回 `Result<T>`；
4. 全局异常统一由 `GlobalExceptionHandler` 处理；
5. 使用 MyBatis-Plus 的 `BaseMapper`、`IService`、`ServiceImpl`；
6. 涉及数据库多步操作的方法添加事务；
7. 文档解析和模型调用日志使用异步线程池；
8. 关键流程打印日志；
9. 不允许出现 `TODO`；
10. 不允许省略 import；
11. 不允许只写伪代码；
12. 所有配置文件必须完整；
13. README 必须包含启动步骤和接口说明；
14. 代码要尽量简洁，适合学生本地运行和面试讲解。

---

## 七、README 要求

生成完整 README，必须包含：

### 1. 项目介绍

突出：

- RAG 知识库问答；
- 文档上传解析；
- 向量检索；
- 多轮对话；
- 查询重写；
- 多模型熔断降级；
- SSE 流式输出。

### 2. 架构图

用 ASCII 图表示：

```text
用户
 ↓
Spring Boot API
 ↓
ChatService
 ↓
QueryRewriteService
 ↓
RetrievalService
 ↓
Milvus 向量检索
 ↓
Prompt 构造
 ↓
LlmGatewayService
 ↓
DeepSeek / Qwen
```

### 3. 快速启动

包括：

```bash
docker-compose up -d
mvn clean package -DskipTests
java -jar target/smart-kb-agent.jar
```

环境变量：

```bash
DEEPSEEK_API_KEY=xxx
QWEN_API_KEY=xxx
JWT_SECRET=xxx
```

### 4. 核心接口表格

包含：

```text
注册
登录
创建知识库
上传文档
查看文档列表
流式问答
查看会话历史
```

### 5. 简历亮点

请输出 6 条适合写进简历的项目亮点，例如：

- 基于 RAG 架构实现知识库问答；
- 设计语义分块策略；
- 使用 Milvus 实现向量召回；
- 设计查询重写机制解决多轮对话指代问题；
- 基于 Resilience4j 实现模型熔断降级；
- 基于 SSE 实现流式响应。

### 6. 面试可讲点

请额外列出面试中可以讲的 8 个问题和简短回答方向，例如：

- 为什么使用 RAG？
- 为什么要文本分块？
- chunk 太大或太小有什么问题？
- 为什么需要查询重写？
- DeepSeek 不支持 Embedding 怎么办？
- 熔断和降级怎么实现？
- SSE 和 WebSocket 区别？
- 如果检索不到相关内容怎么处理？

---

## 八、输出顺序

请按照以下顺序输出完整项目代码：

```text
1. docker-compose.yml
2. sql/init.sql
3. pom.xml
4. application.yml
5. common 包代码
6. config 包代码
7. user 包代码
8. kb 包代码
9. document 包代码
10. llm 包代码
11. search 包代码
12. chat 包代码
13. README.md
```

每个文件必须标注路径，例如：

```text
文件路径：src/main/java/com/example/smartkb/common/Result.java
```

代码用对应代码块包裹。

---

## 九、项目边界

第一版不要实现以下内容：

```text
Spring Cloud Gateway
Nacos 注册中心
多模块 Maven
Cohere Rerank
BGE 本地重排序服务
复杂 RBAC 权限系统
历史自动摘要压缩
Kubernetes 部署
```

这些内容可以写在 README 的“后续优化方向”中。

---

## 十、最终目标

请生成一个可以作为 Java 秋招简历项目的完整后端项目。重点体现：

```text
Java 后端工程能力
RAG 应用能力
向量数据库使用能力
Redis 缓存能力
异步任务处理能力
大模型调用封装能力
熔断降级设计能力
SSE 流式输出能力
```

代码要能运行，结构要清晰，功能要完整，适合学生学习、答辩和秋招面试。
