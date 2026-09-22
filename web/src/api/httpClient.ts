import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../features/auth/authStore'
import type { AuthTokenResponse } from './apiTypes'

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() ?? ''
const baseURL = configuredBaseUrl.replace(/\/+$/, '')

export const httpClient = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})

const refreshClient = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})

interface RetryableRequest extends InternalAxiosRequestConfig {
  _casegenRetried?: boolean
}

let refreshRequest: Promise<string> | null = null

httpClient.interceptors.request.use((config) => {
  const { accessToken, tokenType } = useAuthStore.getState()
  if (accessToken && !isPublicAuthPath(config.url ?? '')) {
    config.headers.Authorization = `${tokenType} ${accessToken}`
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (error.response?.status === 409 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('casegen:concurrent-modification'))
    }
    const request = error.config as RetryableRequest | undefined
    if (!request || !shouldRefresh(error, request)) {
      return Promise.reject(error)
    }

    request._casegenRetried = true
    try {
      const accessToken = await getRefreshedAccessToken()
      request.headers.Authorization = `Bearer ${accessToken}`
      return await httpClient.request(request)
    } catch (refreshError) {
      useAuthStore.getState().clearSession()
      return Promise.reject(refreshError)
    }
  },
)

function shouldRefresh(
  error: AxiosError,
  request: RetryableRequest,
) {
  if (error.response?.status !== 401 || request._casegenRetried) {
    return false
  }
  if (!useAuthStore.getState().refreshToken) {
    return false
  }
  return !isPublicAuthPath(request.url ?? '')
}

function isPublicAuthPath(url: string) {
  return [
    '/api/auth/login',
    '/api/auth/register',
    '/api/auth/refresh',
    '/api/auth/logout',
    '/api/auth/bootstrap-status',
  ].some((authPath) => url.endsWith(authPath))
}

function getRefreshedAccessToken() {
  if (!refreshRequest) {
    refreshRequest = refreshToken().finally(() => {
      refreshRequest = null
    })
  }
  return refreshRequest
}

async function refreshToken() {
  const refreshTokenValue = useAuthStore.getState().refreshToken
  if (!refreshTokenValue) {
    throw new Error('No refresh token is available')
  }
  const response = await refreshClient.post<AuthTokenResponse>(
    '/api/auth/refresh',
    { refreshToken: refreshTokenValue },
  )
  useAuthStore.getState().setTokenPair(response.data)
  return response.data.accessToken
}
