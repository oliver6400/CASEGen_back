import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createSingleFlight, jwtExpirationMs } from '../src/features/auth/jwtToken.ts'

function unsignedJwt(exp) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'none' })}.${encode({ exp })}.signature`
}

test('JWT exp is decoded only as a public expiration hint', () => {
  assert.equal(jwtExpirationMs(unsignedJwt(2_000_000_000)), 2_000_000_000_000)
  assert.equal(jwtExpirationMs('not-a-jwt'), null)
})

test('single-flight shares exactly one active refresh and resets afterwards', async () => {
  let calls = 0
  let release
  const operation = () => {
    calls += 1
    return new Promise((resolve) => { release = resolve })
  }
  const refresh = createSingleFlight(operation)
  const first = refresh()
  const second = refresh()
  const third = refresh()
  assert.equal(calls, 1)
  assert.strictEqual(first, second)
  assert.strictEqual(second, third)
  release('new-access')
  assert.deepEqual(await Promise.all([first, second, third]), ['new-access', 'new-access', 'new-access'])
  const next = refresh()
  assert.equal(calls, 2)
  release('newer-access')
  assert.equal(await next, 'newer-access')
})

test('HTTP client proactively refreshes near expiry and retries a 401 only once', () => {
  const source = readFileSync(new URL('../src/api/httpClient.ts', import.meta.url), 'utf8')
  assert.match(source, /getValidAccessToken\(minimumValidityMs = 60_000\)/)
  assert.match(source, /jwtExpirationMs\(state\.accessToken\)/)
  assert.match(source, /request\._casegenRetried = true/)
  assert.match(source, /request\._casegenRetried/)
  assert.match(source, /isPublicAuthPath\(request\.url/)
  assert.match(source, /createSingleFlight\(refreshToken\)/)
})

test('failed refresh clears the local session without retrying refresh itself', () => {
  const source = readFileSync(new URL('../src/api/httpClient.ts', import.meta.url), 'utf8')
  assert.match(source, /useAuthStore\.getState\(\)\.clearSession\(\)/)
  assert.match(source, /'\/api\/auth\/refresh'/)
  assert.match(source, /return !isPublicAuthPath/)
  assert.doesNotMatch(source, /refreshClient\.interceptors/)
})

test('every STOMP connect and reconnect resolves a current access token', () => {
  for (const relative of [
    '../src/features/ai-chat/hooks/useProjectAiRealtime.ts',
    '../src/features/modeling/hooks/useRealtimeDiagram.ts',
  ]) {
    const source = readFileSync(new URL(relative, import.meta.url), 'utf8')
    assert.match(source, /client\.beforeConnect = async/)
    assert.match(source, /await getValidAccessToken\(\)/)
    assert.match(source, /client\.connectHeaders = \{ Authorization: `Bearer \$\{token\}` \}/)
  }
})

test('AI APIs continue using the shared authenticated HTTP client', () => {
  for (const relative of [
    '../src/features/ai-chat/api/aiChatApi.ts',
    '../src/features/ai-proposals/api/aiProposalApi.ts',
  ]) {
    const source = readFileSync(new URL(relative, import.meta.url), 'utf8')
    assert.match(source, /import \{ httpClient \}/)
    assert.doesNotMatch(source, /axios\.create/)
  }
})
