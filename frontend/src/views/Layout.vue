<template>
  <el-container class="layout-container">
    <!-- Sidebar -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="layout-sidebar">
      <div class="sidebar-header">
        <span v-if="!isCollapsed" class="sidebar-logo">Smart KB</span>
        <el-icon v-else :size="24" color="#fff"><Management /></el-icon>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        background-color="#1a1a2e"
        text-color="#a0aec0"
        active-text-color="#fff"
        router
      >
        <el-menu-item index="/kb">
          <el-icon><FolderOpened /></el-icon>
          <template #title>知识库</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <el-button
          :icon="isCollapsed ? 'Fold' : 'Expand'"
          text
          class="collapse-btn"
          @click="isCollapsed = !isCollapsed"
        >
          <template #icon>
            <el-icon>
              <Fold v-if="!isCollapsed" />
              <Expand v-else />
            </el-icon>
          </template>
        </el-button>
        <el-dropdown trigger="click" @command="handleDropdown">
          <el-button text class="user-btn">
            <el-icon><User /></el-icon>
            <span v-if="!isCollapsed" class="user-name">用户</span>
            <el-icon v-if="!isCollapsed" class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-aside>

    <!-- Main content -->
    <el-container>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  FolderOpened,
  Management,
  User,
  ArrowDown,
  SwitchButton,
  Fold,
  Expand,
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isCollapsed = ref(false)

const activeMenu = computed(() => {
  // Return the base path for highlighting
  return route.path.startsWith('/kb') ? '/kb' : route.path
})

async function handleDropdown(command: string) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
      authStore.clear()
      router.push('/login')
    } catch {
      // Cancelled
    }
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-sidebar {
  display: flex;
  flex-direction: column;
  background-color: #1a1a2e;
  transition: width 0.3s;
  overflow: hidden;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar-logo {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
}

.layout-sidebar .el-menu {
  border-right: none;
  flex: 1;
}

.sidebar-footer {
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.collapse-btn,
.user-btn {
  width: 100%;
  color: #a0aec0;
}

.user-btn:hover,
.collapse-btn:hover {
  color: #fff;
}

.user-name {
  margin-left: 6px;
}

.layout-main {
  background-color: #f5f7fa;
  padding: 24px;
  overflow-y: auto;
}
</style>
