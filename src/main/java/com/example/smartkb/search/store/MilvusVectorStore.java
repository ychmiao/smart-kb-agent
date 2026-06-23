package com.example.smartkb.search.store;

import com.example.smartkb.config.MilvusProperties;
import com.example.smartkb.document.model.DocumentChunk;
import com.example.smartkb.search.exception.VectorStoreException;
import com.example.smartkb.search.model.VectorSearchResult;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milvus 向量存储操作类 —— 封装 collection 创建/删除、向量写入、检索和文档级删除。
 * <p>
 * collection 命名规则：{@code kb_{kbId}}。
 * 使用 HNSW 索引 + COSINE 相似度度量。
 * 创建 collection 时通过 synchronized + ConcurrentHashMap 防止并发重复创建。
 * 写入前校验向量维度是否与配置一致。
 */
@Slf4j
@Component
public class MilvusVectorStore {

    /** Milvus collection 名称前缀 */
    private static final String COLLECTION_PREFIX = "kb_";
    /** 主键字段（自增 int64） */
    private static final String PRIMARY_KEY_FIELD = "id";
    /** 文档 ID 字段 */
    private static final String DOC_ID_FIELD = "doc_id";
    /** chunk 序号字段 */
    private static final String CHUNK_INDEX_FIELD = "chunk_index";
    /** chunk 文本内容字段 */
    private static final String CONTENT_FIELD = "content";
    /** 引用摘要字段 */
    private static final String SOURCE_TEXT_FIELD = "source_text";
    /** 嵌入向量字段 */
    private static final String EMBEDDING_FIELD = "embedding";
    /** 向量索引名称 */
    private static final String VECTOR_INDEX_NAME = "idx_embedding";
    /** content 字段最大长度 */
    private static final int CONTENT_MAX_LENGTH = 4096;
    /** source_text 字段最大长度 */
    private static final int SOURCE_TEXT_MAX_LENGTH = 512;
    /** collection 分片数 */
    private static final int DEFAULT_SHARDS = 2;
    /** 检索时的 HNSW ef 参数 */
    private static final int SEARCH_EF = 64;
    /** 防止并发创建 collection 的锁池 */
    private static final Map<String, Object> COLLECTION_LOCKS = new ConcurrentHashMap<>();

    private final MilvusServiceClient milvusClient;
    private final MilvusProperties properties;

    public MilvusVectorStore(MilvusServiceClient milvusClient, MilvusProperties properties) {
        this.milvusClient = milvusClient;
        this.properties = properties;
    }

    public void insertChunks(Long kbId, List<DocumentChunk> chunks, List<List<Double>> embeddings) {
        if (chunks.isEmpty()) {
            return;
        }
        if (chunks.size() != embeddings.size()) {
            throw new VectorStoreException("文档分块与向量数量不一致");
        }
        validateEmbeddings(embeddings);

        String collectionName = collectionName(kbId);
        ensureCollection(collectionName);

        List<Long> docIds = chunks.stream().map(DocumentChunk::getDocumentId).toList();
        List<Integer> chunkIndexes = chunks.stream().map(DocumentChunk::getChunkIndex).toList();
        List<String> contents = chunks.stream().map(DocumentChunk::getContent).toList();
        List<String> sourceTexts = chunks.stream().map(DocumentChunk::getSourceText).toList();
        List<List<Float>> floatEmbeddings = embeddings.stream()
                .map(this::toFloatVector)
                .toList();

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(List.of(
                        new InsertParam.Field(DOC_ID_FIELD, docIds),
                        new InsertParam.Field(CHUNK_INDEX_FIELD, chunkIndexes),
                        new InsertParam.Field(CONTENT_FIELD, contents),
                        new InsertParam.Field(SOURCE_TEXT_FIELD, sourceTexts),
                        new InsertParam.Field(EMBEDDING_FIELD, floatEmbeddings)
                ))
                .build();
        checkResult(milvusClient.insert(insertParam), "写入向量数据失败");
        log.info("Chunks inserted into Milvus: collectionName={}, chunkCount={}",
                collectionName, chunks.size());
    }

    public void createKnowledgeBaseCollection(Long kbId) {
        ensureCollection(collectionName(kbId));
    }

    public void dropKnowledgeBaseCollection(Long kbId) {
        String collectionName = collectionName(kbId);
        Object lock = COLLECTION_LOCKS.computeIfAbsent(collectionName, key -> new Object());
        synchronized (lock) {
            try {
                dropCollectionIfExists(collectionName);
            } finally {
                COLLECTION_LOCKS.remove(collectionName, lock);
            }
        }
    }

    public List<VectorSearchResult> search(Long kbId, List<Double> queryEmbedding, int topK) {
        validateEmbedding(queryEmbedding);
        String collectionName = collectionName(kbId);
        if (!collectionExists(collectionName)) {
            return List.of();
        }

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withMetricType(MetricType.COSINE)
                .withVectorFieldName(EMBEDDING_FIELD)
                .withTopK(topK)
                .withOutFields(List.of(DOC_ID_FIELD, CHUNK_INDEX_FIELD, CONTENT_FIELD, SOURCE_TEXT_FIELD))
                .withFloatVectors(List.of(toFloatVector(queryEmbedding)))
                .withParams("{\"ef\":" + SEARCH_EF + "}")
                .build();
        R<SearchResults> response = milvusClient.search(searchParam);
        checkResult(response, "向量检索失败");

        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        List<?> docIds = wrapper.getFieldData(DOC_ID_FIELD, 0);
        List<?> chunkIndexes = wrapper.getFieldData(CHUNK_INDEX_FIELD, 0);
        List<?> contents = wrapper.getFieldData(CONTENT_FIELD, 0);
        List<?> sourceTexts = wrapper.getFieldData(SOURCE_TEXT_FIELD, 0);

        List<VectorSearchResult> results = new ArrayList<>(scores.size());
        for (int index = 0; index < scores.size(); index++) {
            results.add(new VectorSearchResult(
                    ((Number) docIds.get(index)).longValue(),
                    ((Number) chunkIndexes.get(index)).intValue(),
                    String.valueOf(contents.get(index)),
                    String.valueOf(sourceTexts.get(index)),
                    (double) scores.get(index).getScore()
            ));
        }
        return results;
    }

    public void deleteDocument(Long kbId, Long documentId) {
        String collectionName = collectionName(kbId);
        if (!collectionExists(collectionName)) {
            return;
        }
        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(DOC_ID_FIELD + " == " + documentId)
                .build();
        checkResult(milvusClient.delete(deleteParam), "删除文档向量失败");
        log.info("Document vectors deleted from Milvus: collectionName={}, documentId={}",
                collectionName, documentId);
    }

    private void ensureCollection(String collectionName) {
        if (collectionExists(collectionName)) {
            return;
        }
        Object lock = COLLECTION_LOCKS.computeIfAbsent(collectionName, key -> new Object());
        synchronized (lock) {
            try {
                if (!collectionExists(collectionName)) {
                    createCollection(collectionName);
                }
            } finally {
                COLLECTION_LOCKS.remove(collectionName, lock);
            }
        }
    }

    private void createCollection(String collectionName) {
        try {
            doCreateCollection(collectionName);
        } catch (RuntimeException exception) {
            dropCollectionQuietly(collectionName, exception);
            throw exception;
        }
    }

    private void doCreateCollection(String collectionName) {
        List<FieldType> fields = List.of(
                FieldType.newBuilder()
                        .withName(PRIMARY_KEY_FIELD)
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(true)
                        .build(),
                FieldType.newBuilder()
                        .withName(DOC_ID_FIELD)
                        .withDataType(DataType.Int64)
                        .build(),
                FieldType.newBuilder()
                        .withName(CHUNK_INDEX_FIELD)
                        .withDataType(DataType.Int32)
                        .build(),
                FieldType.newBuilder()
                        .withName(CONTENT_FIELD)
                        .withDataType(DataType.VarChar)
                        .withMaxLength(CONTENT_MAX_LENGTH)
                        .build(),
                FieldType.newBuilder()
                        .withName(SOURCE_TEXT_FIELD)
                        .withDataType(DataType.VarChar)
                        .withMaxLength(SOURCE_TEXT_MAX_LENGTH)
                        .build(),
                FieldType.newBuilder()
                        .withName(EMBEDDING_FIELD)
                        .withDataType(DataType.FloatVector)
                        .withDimension(properties.getDimension())
                        .build()
        );
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("Knowledge base chunk vectors")
                .withShardsNum(DEFAULT_SHARDS)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withFieldTypes(fields)
                .build();
        checkResult(milvusClient.createCollection(createParam), "创建 Milvus collection 失败");

        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(EMBEDDING_FIELD)
                .withIndexName(VECTOR_INDEX_NAME)
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"M\":16,\"efConstruction\":200}")
                .withSyncMode(true)
                .build();
        checkResult(milvusClient.createIndex(indexParam), "创建 Milvus 向量索引失败");

        LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSyncLoad(true)
                .build();
        checkResult(milvusClient.loadCollection(loadParam), "加载 Milvus collection 失败");
        log.info("Milvus collection created: collectionName={}, dimension={}",
                collectionName, properties.getDimension());
    }

    private void dropCollectionIfExists(String collectionName) {
        if (!collectionExists(collectionName)) {
            return;
        }
        DropCollectionParam dropParam = DropCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
        checkResult(milvusClient.dropCollection(dropParam), "删除 Milvus collection 失败");
        log.info("Milvus collection dropped: collectionName={}", collectionName);
    }

    private void dropCollectionQuietly(String collectionName, RuntimeException originalException) {
        try {
            dropCollectionIfExists(collectionName);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
            log.error("Failed to compensate partially created Milvus collection: collectionName={}",
                    collectionName, compensationException);
        }
    }

    private boolean collectionExists(String collectionName) {
        R<Boolean> response = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
        checkResult(response, "检查 Milvus collection 失败");
        return Boolean.TRUE.equals(response.getData());
    }

    private void validateEmbeddings(List<List<Double>> embeddings) {
        embeddings.forEach(this::validateEmbedding);
    }

    private void validateEmbedding(List<Double> embedding) {
        if (embedding == null || embedding.size() != properties.getDimension()) {
            int actualDimension = embedding == null ? 0 : embedding.size();
            throw new VectorStoreException(
                    "向量维度不匹配，期望 " + properties.getDimension() + "，实际 " + actualDimension
            );
        }
    }

    private List<Float> toFloatVector(List<Double> vector) {
        return vector.stream().map(Double::floatValue).toList();
    }

    private String collectionName(Long kbId) {
        return COLLECTION_PREFIX + kbId;
    }

    private void checkResult(R<?> response, String message) {
        if (response == null || response.getStatus() != R.Status.Success.getCode()) {
            String detail = response == null ? "empty response" : response.getMessage();
            throw new VectorStoreException(message + ": " + detail,
                    response == null ? null : response.getException());
        }
    }
}
