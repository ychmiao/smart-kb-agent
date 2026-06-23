import { api } from './request'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
}

export interface TokenResponse {
  tokenType: string
  accessToken: string
  refreshToken: string
  accessTokenExpiresIn: number
}

/** Login */
export function loginApi(data: LoginRequest) {
  return api.post<TokenResponse>('/auth/login', data)
}

/** Register */
export function registerApi(data: RegisterRequest) {
  return api.post<number>('/auth/register', data)
}

/** Refresh token */
export function refreshTokenApi(refreshToken: string) {
  return api.post<TokenResponse>('/auth/refresh', { refreshToken })
}
