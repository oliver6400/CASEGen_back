import { httpClient } from '../../../api/httpClient'

export type NormalForm = '1NF' | '2NF' | '3NF'
export type NormalizationStatus = 'PASS' | 'WARNING' | 'FAIL' | 'UNKNOWN'

export interface NormalizationIssue {
  id: string
  logicalTableId: string | null
  tableName: string | null
  logicalColumnId: string | null
  columnName: string | null
  normalForm: NormalForm
  severity: 'WARNING' | 'ERROR'
  code: string
  message: string
  recommendation: string
  metadata: { columns?: string[]; dependency?: string; sourceElementId?: string; [key: string]: unknown }
  sourceElementId: string | null
}

export interface NormalizationRun {
  id: string
  logicalModelId: string
  status: NormalizationStatus
  highestNormalForm: NormalForm | null
  createdBy: string | null
  createdAt: string
  forms: { normalForm: NormalForm; status: NormalizationStatus; statement: string }[]
  issues: NormalizationIssue[]
}

export const normalizationApi = {
  analyze: async (logicalModelId: string) =>
    (await httpClient.post<NormalizationRun>(`/api/logical-models/${logicalModelId}/normalization-runs`)).data,
  list: async (logicalModelId: string) =>
    (await httpClient.get<NormalizationRun[]>(`/api/logical-models/${logicalModelId}/normalization-runs`)).data,
  get: async (runId: string) =>
    (await httpClient.get<NormalizationRun>(`/api/normalization-runs/${runId}`)).data,
}
