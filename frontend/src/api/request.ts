import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: string
}

const instance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor — attach JWT
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore()
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Response interceptor — handle token refresh
let isRefreshing = false
let pendingQueue: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

function processQueue(error: unknown, token: string | null = null) {
  pendingQueue.forEach((p) => {
    if (token) {
      p.resolve(token)
    } else {
      p.reject(error)
    }
  })
  pendingQueue = []
}

instance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean
    }

    // Token expired — auto refresh
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/refresh') &&
      !originalRequest.url?.includes('/auth/login')
    ) {
      const authStore = useAuthStore()
      const refreshToken = authStore.refreshToken

      if (!refreshToken) {
        authStore.clear()
        window.location.href = '/#/login'
        return Promise.reject(error)
      }

      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          pendingQueue.push({ resolve, reject })
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return instance(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const newToken = await authStore.refreshAccessToken()
        processQueue(null, newToken)
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return instance(originalRequest)
      } catch (refreshError) {
        processQueue(refreshError, null)
        authStore.clear()
        window.location.href = '/#/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    // Network error
    if (!error.response) {
      ElMessage.error('网络连接失败，请检查网络')
    }

    return Promise.reject(error)
  },
)

// ——— Typed helpers that unwrap ApiResult ———

async function handleResponse<T>(promise: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const response = await promise
  const result = response.data
  if (result.code !== 0) {
    ElMessage.error(result.message || '请求失败')
    throw new Error(result.message)
  }
  return result.data
}

export const api = {
  get<T>(url: string, config?: Record<string, unknown>): Promise<T> {
    return handleResponse(instance.get<ApiResult<T>>(url, config))
  },
  post<T>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<T> {
    return handleResponse(instance.post<ApiResult<T>>(url, data, config))
  },
  delete<T = void>(url: string, config?: Record<string, unknown>): Promise<T> {
    return handleResponse(instance.delete<ApiResult<T>>(url, config))
  },
}

export default instance
