import axios from 'axios'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../features/auth/authStore'
import { createSingleFlight, jwtExpirationMs } from '../features/auth/jwtToken'
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

httpClient.interceptors.request.use(async (config) => {
  if (!isPublicAuthPath(config.url ?? '')) {
    const state = useAuthStore.getState()
    if (state.accessToken) {
      const accessToken = await getValidAccessToken()
      config.headers.Authorization = `${state.tokenType} ${accessToken}`
    }
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

const getRefreshedAccessToken = createSingleFlight(refreshToken)

export async function getValidAccessToken(minimumValidityMs = 60_000) {
  const state = useAuthStore.getState()
  const expiresAt = state.accessToken
    ? jwtExpirationMs(state.accessToken) ?? state.accessTokenExpiresAt
    : null
  if (state.accessToken && expiresAt && expiresAt - Date.now() > minimumValidityMs) {
    return state.accessToken
  }
  if (!state.refreshToken) {
    throw new Error('No refresh token is available')
  }
  try {
    return await getRefreshedAccessToken()
  } catch (error) {
    useAuthStore.getState().clearSession()
    throw error
  }
}

async function refreshToken() {
  const refreshTokenValue = useAuthStore.getState().refreshToken
  if (!refreshTokenValue) {
    throw new Error('No refresh token is available')
  }
  if (import.meta.env.DEV) console.info('[auth] refresh started')
  try {
    const response = await refreshClient.post<AuthTokenResponse>(
      '/api/auth/refresh',
      { refreshToken: refreshTokenValue },
    )
    useAuthStore.getState().setTokenPair(response.data)
    if (import.meta.env.DEV) console.info('[auth] refresh succeeded')
    return response.data.accessToken
  } catch (error) {
    if (import.meta.env.DEV) console.info('[auth] refresh failed')
    throw error
  }
}
