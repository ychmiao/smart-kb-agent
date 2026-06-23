import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', noAuth: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', noAuth: true },
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    meta: { title: 'Smart KB Agent' },
    redirect: '/kb',
    children: [
      {
        path: 'kb',
        name: 'KnowledgeBase',
        component: () => import('@/views/KnowledgeBase.vue'),
        meta: { title: '知识库' },
      },
      {
        path: 'kb/:kbId/documents',
        name: 'Documents',
        component: () => import('@/views/DocumentList.vue'),
        meta: { title: '文档管理' },
      },
      {
        path: 'kb/:kbId/chat',
        name: 'Chat',
        component: () => import('@/views/Chat.vue'),
        meta: { title: '智能问答' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// Navigation guard — check auth
router.beforeEach((to, _from, next) => {
  // Set document title
  document.title = `${to.meta.title || 'Smart KB Agent'} - Smart KB Agent`

  if (to.meta.noAuth) {
    next()
    return
  }

  const authStore = useAuthStore()
  if (!authStore.isLoggedIn) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
