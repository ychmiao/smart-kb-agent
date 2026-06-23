<template>
  <div class="kb-page">
    <!-- Page header -->
    <div class="page-header">
      <div>
        <h2 class="page-title">知识库</h2>
        <p class="page-desc">管理和查看您的知识库</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">
        新建知识库
      </el-button>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="4" animated />
    </div>

    <!-- Empty state -->
    <div v-else-if="kbList.length === 0" class="empty-container">
      <el-empty description="暂无知识库">
        <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">
          创建第一个知识库
        </el-button>
      </el-empty>
    </div>

    <!-- Knowledge base grid -->
    <div v-else class="kb-grid">
      <el-card
        v-for="kb in kbList"
        :key="kb.id"
        class="kb-card"
        shadow="hover"
        @click="enterKb(kb)"
      >
        <div class="kb-card-header">
          <el-icon :size="28" color="#667eea"><Collection /></el-icon>
          <el-dropdown
            trigger="click"
            @command="(cmd: string) => handleCardCommand(cmd, kb)"
            @click.stop
          >
            <el-button text class="more-btn" @click.stop>
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="documents" :icon="Document">
                  文档管理
                </el-dropdown-item>
                <el-dropdown-item command="delete" :icon="Delete" divided>
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <h3 class="kb-card-title">{{ kb.name }}</h3>
        <p class="kb-card-desc">
          {{ kb.description || '暂无描述' }}
        </p>

        <div class="kb-card-footer">
          <span class="kb-card-time">
            <el-icon><Clock /></el-icon>
            {{ formatDateTime(kb.createTime) }}
          </span>
          <el-button
            text
            type="primary"
            size="small"
            :icon="ChatDotSquare"
            @click.stop="goChat(kb)"
          >
            问答
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- Create dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="新建知识库"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="70px"
      >
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="createForm.name"
            placeholder="知识库名称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="知识库描述（选填）"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Plus,
  Collection,
  MoreFilled,
  Document,
  Delete,
  Clock,
  ChatDotSquare,
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listKnowledgeBasesApi,
  createKnowledgeBaseApi,
  deleteKnowledgeBaseApi,
  type KnowledgeBaseResponse,
} from '@/api/knowledgeBase'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const creating = ref(false)
const kbList = ref<KnowledgeBaseResponse[]>([])
const showCreateDialog = ref(false)

const createFormRef = ref<FormInstance>()
const createForm = reactive({
  name: '',
  description: '',
})
const createRules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { max: 100, message: '名称不超过 100 字符', trigger: 'blur' },
  ],
  description: [{ max: 2000, message: '描述不超过 2000 字符', trigger: 'blur' }],
}

async function fetchKbList() {
  loading.value = true
  try {
    kbList.value = await listKnowledgeBasesApi()
  } catch {
    console.error('Failed to load knowledge bases')
  } finally {
    loading.value = false
  }
}

function enterKb(kb: KnowledgeBaseResponse) {
  router.push(`/kb/${kb.id}/documents`)
}

function goChat(kb: KnowledgeBaseResponse) {
  router.push(`/kb/${kb.id}/chat`)
}

function handleCardCommand(cmd: string, kb: KnowledgeBaseResponse) {
  if (cmd === 'documents') {
    router.push(`/kb/${kb.id}/documents`)
  } else if (cmd === 'delete') {
    handleDelete(kb)
  }
}

async function handleDelete(kb: KnowledgeBaseResponse) {
  try {
    await ElMessageBox.confirm(
      `确定要删除知识库「${kb.name}」吗？该操作会同时删除所有关联的文档和向量数据，不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
    await deleteKnowledgeBaseApi(kb.id)
    ElMessage.success('删除成功')
    await fetchKbList()
  } catch {
    // Cancelled or error
  }
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  creating.value = true
  try {
    await createKnowledgeBaseApi({
      name: createForm.name,
      description: createForm.description || undefined,
    })
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    createForm.name = ''
    createForm.description = ''
    await fetchKbList()
  } catch {
    console.error('Failed to create KB')
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  fetchKbList()
})
</script>

<style scoped>
.kb-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
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

.loading-container {
  padding: 40px;
  background: #fff;
  border-radius: 8px;
}

.empty-container {
  padding: 80px 0;
  background: #fff;
  border-radius: 8px;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.kb-card {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.kb-card:hover {
  transform: translateY(-2px);
}

.kb-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.more-btn {
  color: #9ca3af;
}

.kb-card-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.kb-card-desc {
  font-size: 14px;
  color: #6b7280;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
  margin-bottom: 16px;
}

.kb-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-card-time {
  font-size: 12px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
