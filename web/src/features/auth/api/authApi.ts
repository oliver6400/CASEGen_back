import { httpClient } from '../../../api/httpClient'
import type {
  AuthTokenResponse,
  BootstrapStatusResponse,
  CurrentUserResponse,
} from '../../../api/apiTypes'
import { useAuthStore } from '../authStore'

export interface LoginInput {
  email: string
  password: string
}

export interface RegisterInput extends LoginInput {
  displayName: string
}

async function acceptTokenPair(request: Promise<{ data: AuthTokenResponse }>) {
  const { data } = await request
  useAuthStore.getState().setTokenPair(data)
  return data
}

export const authApi = {
  bootstrapStatus: async () =>
    (await httpClient.get<BootstrapStatusResponse>('/api/auth/bootstrap-status'))
      .data,

  register: (input: RegisterInput) =>
    acceptTokenPair(
      httpClient.post<AuthTokenResponse>('/api/auth/register', input),
    ),

  login: (input: LoginInput) =>
    acceptTokenPair(httpClient.post<AuthTokenResponse>('/api/auth/login', input)),

  me: async () =>
    (await httpClient.get<CurrentUserResponse>('/api/auth/me')).data,

  logout: async () => {
    const { refreshToken, clearSession } = useAuthStore.getState()
    try {
      if (refreshToken) {
        await httpClient.post('/api/auth/logout', { refreshToken })
      }
    } finally {
      clearSession()
    }
  },
}
