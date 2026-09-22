import { test } from 'node:test'
import assert from 'node:assert/strict'
import { getApiErrorMessage } from '../src/api/apiError.ts'

test('409 preserves real workflow code and message', () => {
  const error = { isAxiosError: true, response: { status: 409, data: { code: 'MODEL_REVISION_ALREADY_VERSIONED', message: 'La revisión r12 ya está versionada como v2.' } } }
  assert.equal(getApiErrorMessage(error), 'MODEL_REVISION_ALREADY_VERSIONED: La revisión r12 ya está versionada como v2.')
})
test('409 shows ProblemDetail reason and HTTP fallback rather than generic failure', () => {
  assert.equal(getApiErrorMessage({ isAxiosError: true, response: { status: 409, data: { detail: 'Model is not editable' } } }), 'HTTP_409: Model is not editable')
})
