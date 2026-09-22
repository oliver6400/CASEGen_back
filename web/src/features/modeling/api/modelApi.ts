import { httpClient } from '../../../api/httpClient'
import type { UmlModel, UmlPackage } from '../types/uml'

export interface XmiImportWarning {
  code: string
  message: string
  externalId: string | null
  metadata: Record<string, unknown>
}

export interface XmiImportResponse {
  modelId: string
  modelName: string
  dialect: 'CASEGEN_UML_2_5_1' | 'EA_XMI_1_1_UML_1_3'
  packageCount: number
  classCount: number
  interfaceCount: number
  propertyCount: number
  operationCount: number
  parameterCount: number
  associationCount: number
  generalizationCount: number
  realizationCount: number
  diagram: {
    diagramId: string
    name: string
    nodeCount: number
    edgeCount: number
  } | null
  warnings: XmiImportWarning[]
  validation: { validationId: string; status: 'VALID' | 'INVALID'; issueCount: number }
}

export interface CreateModelInput {
  name: string
  documentation?: string | null
}

export type DetectedType = { kind: 'PRIMITIVE' | 'CLASSIFIER' | 'VOID' | 'UNKNOWN'; value: string }
export type DetectedEnd = { classifierId: string; roleName: string; visibility: string; lowerBound: number | null; upperBound: number | null; upperUnlimited: boolean; multiplicityKnown: boolean; navigable: boolean }
export interface DetectedClassDiagram {
  suggestedModelName: string
  detectedClassifiers: Array<{ id: string; name: string; kind: 'CLASS' | 'INTERFACE' | 'UNKNOWN'; visibility: string; confidence: number; region?: { x: number; y: number; width: number; height: number } | null }>
  detectedAttributes: Array<{ id: string; ownerClassifierId: string; name: string; visibility: string; type: DetectedType; lowerBound: number | null; upperBound: number | null; upperUnlimited: boolean; multiplicityKnown: boolean; confidence: number }>
  detectedOperations: Array<{ id: string; ownerClassifierId: string; name: string; visibility: string; parameters: Array<{ id: string; name: string; direction: string; type: DetectedType; position: number; lowerBound: number | null; upperBound: number | null; upperUnlimited: boolean; multiplicityKnown: boolean; confidence: number }>; returnType: DetectedType; confidence: number }>
  detectedRelationships: Array<{ id: string; type: 'ASSOCIATION' | 'AGGREGATION' | 'COMPOSITION' | 'GENERALIZATION' | 'REALIZATION' | 'UNKNOWN'; name: string; source: DetectedEnd; target: DetectedEnd; confidence: number }>
  warnings: Array<{ code: string; message: string; elementId: string | null }>
  confidence: number
}

export interface ImageImportResponse {
  modelId: string
  modelName: string
  classCount: number
  interfaceCount: number
  attributeCount: number
  operationCount: number
  relationshipCount: number
  diagram: { diagramId: string; name: string; nodeCount: number; edgeCount: number } | null
  validation: { validationId: string; status: 'VALID' | 'INVALID'; issueCount: number }
  warnings: DetectedClassDiagram['warnings']
}

export interface ImageImportConfiguration {
  configured: boolean
  provider: string
  model: string
  localOcrAvailable: boolean | null
  fallbackLocalAvailable: boolean
}

export const modelApi = {
  imageImportStatus: async () =>
    (await httpClient.get<ImageImportConfiguration>('/api/image-import/status')).data,
  listPackages: async (modelId: string) =>
    (await httpClient.get<UmlPackage[]>(`/api/uml-models/${modelId}/packages`)).data,
  list: async (projectId: string) =>
    (await httpClient.get<UmlModel[]>(`/api/projects/${projectId}/uml-models`))
      .data,
  get: async (modelId: string) =>
    (await httpClient.get<UmlModel>(`/api/uml-models/${modelId}`)).data,
  create: async (projectId: string, input: CreateModelInput) =>
    (
      await httpClient.post<UmlModel>(
        `/api/projects/${projectId}/uml-models`,
        input,
      )
    ).data,
  importXmi: async (projectId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return (await httpClient.post<XmiImportResponse>(
      `/api/projects/${projectId}/xmi/import`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )).data
  },
  analyzeImage: async (projectId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return (await httpClient.post<DetectedClassDiagram>(
      `/api/projects/${projectId}/image-import/analyze`, form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )).data
  },
  confirmImageImport: async (projectId: string, proposal: DetectedClassDiagram) =>
    (await httpClient.post<ImageImportResponse>(
      `/api/projects/${projectId}/image-import/confirm`, proposal,
    )).data,
  exportXmi: async (modelId: string, fallbackName: string) => {
    const response = await httpClient.get<Blob>(
      `/api/uml-models/${modelId}/export/xmi`,
      { responseType: 'blob' },
    )
    const disposition = response.headers['content-disposition'] as string | undefined
    const encodedName = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    const simpleName = disposition?.match(/filename="?([^";]+)"?/i)?.[1]
    const fileName = encodedName
      ? decodeURIComponent(encodedName)
      : simpleName ?? `${safeFileName(fallbackName)}.xmi`
    return { content: response.data, fileName }
  },
}

function safeFileName(value: string) {
  const normalized = value.trim().toLowerCase().replace(/[^a-z0-9._-]+/g, '-')
  return normalized.replace(/^-+|-+$/g, '') || 'uml-model'
}
