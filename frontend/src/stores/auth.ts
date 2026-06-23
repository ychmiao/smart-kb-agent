import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import {
  loginApi,
  registerApi,
  refreshTokenApi,
  type LoginRequest,
  type RegisterRequest,
} from '@/api/auth'

const STORAGE_KEY = 'kb-auth'

function loadFromStorage(): { accessToken: string; refreshToken: string } {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      return JSON.parse(saved)
    }
  } catch {
    // ignore
  }
  return { accessToken: '', refreshToken: '' }
}

function saveToStorage(data: { accessToken: string; refreshToken: string }) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
}

function clearStorage() {
  localStorage.removeItem(STORAGE_KEY)
}

export const useAuthStore = defineStore('auth', () => {
  const saved = loadFromStorage()
  const accessToken = ref(saved.accessToken)
  const refreshToken = ref(saved.refreshToken)

  const isLoggedIn = computed(() => !!accessToken.value)

  // Auto-persist to localStorage
  watch(
    [accessToken, refreshToken],
    ([newAccess, newRefresh]) => {
      if (newAccess && newRefresh) {
        saveToStorage({ accessToken: newAccess, refreshToken: newRefresh })
      }
    },
    { deep: true },
  )

  async function login(data: LoginRequest) {
    const token = await loginApi(data)
    accessToken.value = token.accessToken
    refreshToken.value = token.refreshToken
  }

  async function register(data: RegisterRequest) {
    await registerApi(data)
  }

  async function refreshAccessToken(): Promise<string> {
    const token = await refreshTokenApi(refreshToken.value)
    accessToken.value = token.accessToken
    refreshToken.value = token.refreshToken
    return accessToken.value
  }

  function clear() {
    accessToken.value = ''
    refreshToken.value = ''
    clearStorage()
  }

  function restore() {
    const saved = loadFromStorage()
    if (saved.accessToken) {
      accessToken.value = saved.accessToken
      refreshToken.value = saved.refreshToken
    }
  }

  return {
    accessToken,
    refreshToken,
    isLoggedIn,
    login,
    register,
    refreshAccessToken,
    clear,
    restore,
  }
})
