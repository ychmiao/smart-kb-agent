package com.example.smartkb;

import com.example.smartkb.chat.mapper.ChatMessageMapper;
import com.example.smartkb.chat.mapper.ConversationMapper;
import com.example.smartkb.document.mapper.DocumentMapper;
import com.example.smartkb.kb.mapper.KnowledgeBaseMapper;
import com.example.smartkb.llm.mapper.LlmCallLogMapper;
import com.example.smartkb.user.mapper.UserMapper;
import io.minio.MinioClient;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;

/**
 * ApplicationContext 启动测试。
 * <p>
 * 验证 Spring 上下文能够成功加载，不存在循环依赖等 Bean 创建失败问题。
 * 外部基础设施（MySQL、Redis、MinIO、Milvus）及 MyBatis-Plus Mapper 均使用 Mock。
 * 密钥类属性使用测试值填充以通过属性绑定校验。
 */
@SpringBootTest(properties = {
        "spring.main.allow-circular-references=false",
        "kb.jwt.secret=test-jwt-secret-for-unit-test-must-be-32-chars-long-at-least",
        "kb.minio.access-key=test-access-key",
        "kb.minio.secret-key=test-secret-key",
        "kb.llm.providers.deepseek.api-key=test-ds-key",
        "kb.llm.providers.qwen.api-key=test-qwen-key"
})
class SmartKbAgentApplicationTest {

    @MockBean
    private DataSource dataSource;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @MockBean
    private MinioClient minioClient;

    @MockBean
    private MilvusServiceClient milvusServiceClient;

    @MockBean
    private DocumentMapper documentMapper;

    @MockBean
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private ConversationMapper conversationMapper;

    @MockBean
    private ChatMessageMapper chatMessageMapper;

    @MockBean
    private LlmCallLogMapper llmCallLogMapper;

    @Test
    void contextLoads() {
        // Spring 上下文应成功创建，无循环依赖异常
        // 如果 Bean 创建阶段存在循环依赖，Spring 将抛出 BeanCurrentlyInCreationException
    }
}
