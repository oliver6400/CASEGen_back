import { create } from 'zustand'
import type { AuthTokenResponse } from '../../api/apiTypes'

const STORAGE_KEY = 'casegen.auth.tokens'

interface StoredTokens {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  accessTokenExpiresAt: number
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  tokenType: 'Bearer'
  accessTokenExpiresAt: number | null
  setTokenPair: (tokens: AuthTokenResponse) => void
  clearSession: () => void
}

function loadStoredTokens(): StoredTokens | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value ? (JSON.parse(value) as StoredTokens) : null
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

const storedTokens = loadStoredTokens()

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: storedTokens?.accessToken ?? null,
  refreshToken: storedTokens?.refreshToken ?? null,
  tokenType: 'Bearer',
  accessTokenExpiresAt: storedTokens?.accessTokenExpiresAt ?? null,
  setTokenPair: (tokens) => {
    const nextTokens: StoredTokens = {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      tokenType: 'Bearer',
      accessTokenExpiresAt: Date.now() + tokens.expiresIn * 1000,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(nextTokens))
    set(nextTokens)
  },
  clearSession: () => {
    localStorage.removeItem(STORAGE_KEY)
    set({
      accessToken: null,
      refreshToken: null,
      tokenType: 'Bearer',
      accessTokenExpiresAt: null,
    })
  },
}))
