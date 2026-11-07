export function jwtExpirationMs(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const claims = JSON.parse(atob(padded)) as { exp?: unknown }
    return typeof claims.exp === 'number' && Number.isFinite(claims.exp)
      ? claims.exp * 1000
      : null
  } catch {
    return null
  }
}

export function createSingleFlight<T>(operation: () => Promise<T>) {
  let active: Promise<T> | null = null
  return () => {
    if (!active) {
      active = operation().finally(() => { active = null })
    }
    return active
  }
}
