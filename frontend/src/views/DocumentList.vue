<template>
  <div class="doc-page">
    <!-- Breadcrumb -->
    <div class="page-breadcrumb">
      <el-button text :icon="ArrowLeft" @click="goBack">返回知识库</el-button>
      <el-divider direction="vertical" />
      <span class="breadcrumb-kb-name">{{ kbName }}</span>
      <el-divider direction="vertical" />
      <span class="breadcrumb-current">文档管理</span>
    </div>

    <!-- Page header -->
    <div class="page-header">
      <div>
        <h2 class="page-title">文档管理</h2>
        <p class="page-desc">上传和管理知识库文档</p>
      </div>
      <el-upload
        :show-file-list="false"
        :before-upload="handleBeforeUpload"
        :http-request="handleUpload"
        accept=".pdf,.docx,.md,.txt"
      >
        <el-button type="primary" :icon="Upload" :loading="uploading">
          {{ uploading ? '上传中...' : '上传文档' }}
        </el-button>
      </el-upload>
    </div>

    <!-- Document list -->
    <el-card class="doc-table-card">
      <!-- Loading -->
      <div v-if="loading" class="table-loading">
        <el-skeleton :rows="5" animated />
      </div>

      <!-- Empty -->
      <div v-else-if="documents.length === 0" class="table-empty">
        <el-empty description="暂无文档，请上传">
          <el-upload
            :show-file-list="false"
            :before-upload="handleBeforeUpload"
            :http-request="handleUpload"
            accept=".pdf,.docx,.md,.txt"
          >
            <el-button type="primary" :icon="Upload">上传第一个文档</el-button>
          </el-upload>
        </el-empty>
      </div>

      <!-- Table -->
      <el-table v-else :data="documents" stripe style="width: 100%">
        <el-table-column label="文件名" min-width="240">
          <template #default="{ row }">
            <div class="file-name-cell">
              <el-icon :size="20" :color="fileIconColor(row.fileType)">
                <Document />
              </el-icon>
              <span>{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="fileType" label="类型" width="80" align="center" />

        <el-table-column label="大小" width="100" align="right">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag
              :type="statusType(row.status)"
              :hit="row.status === 2"
              size="small"
            >
              {{ statusText(row.status) }}
            </el-tag>
            <el-tooltip
              v-if="row.errorMsg"
              :content="row.errorMsg"
              placement="top"
            >
              <el-icon class="error-icon" color="#f56c6c"><WarningFilled /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="分块数" width="80" align="center">
          <template #default="{ row }">
            {{ row.chunkCount ?? '-' }}
          </template>
        </el-table-column>

        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="danger"
              size="small"
              :icon="Delete"
              :loading="deletingId === row.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Search test section -->
    <el-card class="search-card" v-if="documents.length > 0">
      <template #header>
        <div class="search-header">
          <span>语义搜索测试</span>
        </div>
      </template>
      <div class="search-body">
        <el-input
          v-model="searchQuery"
          placeholder="输入搜索关键词测试向量检索效果"
          :prefix-icon="Search"
          clearable
          @keyup.enter="handleSearchTest"
        >
          <template #append>
            <el-button @click="handleSearchTest" :loading="searching">
              搜索
            </el-button>
          </template>
        </el-input>

        <div v-if="searchResults.length > 0" class="search-results">
          <div
            v-for="(item, index) in searchResults"
            :key="index"
            class="search-result-item"
          >
            <div class="result-header">
              <el-tag size="small" type="info">
                {{ item.fileName }}
              </el-tag>
              <span class="result-score">
                相似度: {{ (item.score * 100).toFixed(1) }}%
              </span>
            </div>
            <p class="result-content">{{ item.content }}</p>
          </div>
        </div>

        <el-empty
          v-else-if="searchSearched && searchResults.length === 0"
          description="未找到匹配内容"
          :image-size="60"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Upload,
  Document,
  Delete,
  Search,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDocumentsApi,
  uploadDocumentApi,
  deleteDocumentApi,
  searchTestApi,
  type DocumentResponse,
  type RetrievedChunk,
} from '@/api/document'
import { listKnowledgeBasesApi } from '@/api/knowledgeBase'
import { formatFileSize, formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const kbId = computed(() => Number(route.params.kbId))
const loading = ref(false)
const documents = ref<DocumentResponse[]>([])
const kbName = ref('')
const uploading = ref(false)
const deletingId = ref<number | null>(null)

// Search test
const searchQuery = ref('')
const searching = ref(false)
const searchSearched = ref(false)
const searchResults = ref<RetrievedChunk[]>([])

const ALLOWED_TYPES = ['pdf', 'docx', 'md', 'txt']
const MAX_FILE_SIZE = 50 * 1024 * 1024 // 50 MB

function fileIconColor(type: string): string {
  const colors: Record<string, string> = {
    pdf: '#f56c6c',
    docx: '#409eff',
    md: '#67c23a',
    txt: '#909399',
  }
  return colors[type] || '#909399'
}

function statusType(status: number): 'warning' | 'success' | 'danger' | 'info' {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

function statusText(status: number): string {
  if (status === 0) return '处理中'
  if (status === 1) return '已完成'
  if (status === 2) return '失败'
  return '未知'
}

function goBack() {
  router.push('/kb')
}

async function fetchKbName() {
  try {
    const kbs = await listKnowledgeBasesApi()
    const kb = kbs.find((k) => k.id === kbId.value)
    if (kb) kbName.value = kb.name
  } catch {
    // ignore
  }
}

async function fetchDocuments() {
  loading.value = true
  try {
    documents.value = await listDocumentsApi(kbId.value)
  } catch {
    console.error('Failed to load documents')
  } finally {
    loading.value = false
  }
}

function handleBeforeUpload(file: File): boolean {
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !ALLOWED_TYPES.includes(ext)) {
    ElMessage.error('不支持的文件类型，仅支持 PDF、DOCX、MD、TXT')
    return false
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('文件大小不能超过 50MB')
    return false
  }
  return true
}

async function handleUpload(options: any) {
  const file = options.file as File
  uploading.value = true
  try {
    await uploadDocumentApi(kbId.value, file)
    ElMessage.success('上传成功，文档正在后台处理')
    await fetchDocuments()
  } catch {
    console.error('Upload failed')
  } finally {
    uploading.value = false
  }
}

async function handleDelete(doc: DocumentResponse) {
  if (doc.status === 0) {
    ElMessage.warning('文档正在处理中，请稍后再删除')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除「${doc.fileName}」吗？`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )

    deletingId.value = doc.id
    await deleteDocumentApi(doc.id)
    ElMessage.success('删除成功')
    await fetchDocuments()
  } catch {
    // cancelled or error
  } finally {
    deletingId.value = null
  }
}

async function handleSearchTest() {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }

  searching.value = true
  searchSearched.value = false
  try {
    searchResults.value = await searchTestApi(kbId.value, searchQuery.value)
  } catch {
    console.error('Search test failed')
  } finally {
    searching.value = false
    searchSearched.value = true
  }
}

onMounted(() => {
  fetchKbName()
  fetchDocuments()
})
</script>

<style scoped>
.doc-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-breadcrumb {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  font-size: 14px;
}

.breadcrumb-kb-name {
  color: #1a1a2e;
  font-weight: 500;
}

.breadcrumb-current {
  color: #6b7280;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a2e;
}

.page-desc {
  font-size: 14px;
  color: #6b7280;
  margin-top: 4px;
}

.doc-table-card {
  margin-bottom: 20px;
}

.table-loading {
  padding: 20px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-icon {
  margin-left: 4px;
}

.search-card {
  margin-top: 20px;
}

.search-header {
  font-weight: 600;
  font-size: 16px;
}

.search-body {
  padding: 0 4px;
}

.search-results {
  margin-top: 16px;
}

.search-result-item {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-bottom: 10px;
  background: #f9fafb;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.result-score {
  font-size: 12px;
  color: #6b7280;
}

.result-content {
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
}
</style>
