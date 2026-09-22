export interface ApiError {
  code: string
  message: string
  timestamp: string
}

export interface IdentifiedResource {
  id: string
}

export type SystemRole = 'USER' | 'SUPERUSER'

export interface AuthTokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

export interface CurrentUserResponse extends IdentifiedResource {
  email: string
  displayName: string
  systemRole: SystemRole
}

export interface BootstrapStatusResponse {
  superuserCreated: boolean
}

export interface ApiProblem {
  code?: string
  detail?: string
  message?: string
  title?: string
}
