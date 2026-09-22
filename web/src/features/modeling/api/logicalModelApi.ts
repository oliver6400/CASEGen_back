import { httpClient } from '../../../api/httpClient'

export interface LogicalColumn { name: string; sourcePropertyId: string | null; sqlType: string; nullable: boolean; ordinalPosition: number }
export interface LogicalKey { name: string; columns: string[]; sourceElementId: string | null }
export interface LogicalForeignKey { name: string; referencedTable: string; columns: { column: string; referencedColumn: string }[]; onDelete: string; sourceElementId: string | null }
export interface LogicalTable {
  name: string; sourceClassifierId: string | null; sourceAssociationId: string | null; columns: LogicalColumn[]
  primaryKey: LogicalKey | null; foreignKeys: LogicalForeignKey[]; uniqueConstraints: LogicalKey[]
  indexes: (LogicalKey & { unique: boolean })[]; checks: { name: string; expression: string; sourceElementId: string | null }[]
}
export interface LogicalModel {
  id: string; modelVersionId: string; generationProfileId: string; status: 'VALID' | 'INVALID'; checksum: string
  tables: LogicalTable[]; issues: { severity: string; code: string; message: string; sourceElementId: string | null }[]
}
export interface GenerationRun {
  id: string; logicalModelId: string; modelVersionId: string; modelVersionNumber: number; generationProfileId: string; generationProfileName: string
  generatorVersion: string; status: 'QUEUED' | 'GENERATING' | 'COMPILING' | 'TESTING' | 'VERIFYING_DATABASE' | 'PACKAGING' | 'SUCCESS' | 'FAILED'
  currentStage: string | null; errorMessage: string | null
  startedAt: string | null; finishedAt: string | null; createdAt: string
  tests: { id: string; testType: string; status: string; outputLog: string | null; startedAt: string; finishedAt: string | null }[]
  artifacts: { id: string; artifactType: string; fileName: string; checksum: string; sizeBytes: number; createdAt: string }[]
}
export interface GenerationStart { generationId: string; status: 'QUEUED' }
export interface LogicalSummary { id: string; modelVersionId: string; versionNumber: number; generationProfileId: string; profileName: string; status: 'VALID' | 'INVALID'; checksum: string; createdAt: string }
export interface LogicalPreview { tables: LogicalTable[]; issues: LogicalModel['issues'] }
export interface VersionSnapshot {
  id: string; versionNumber: number
  semanticSnapshot: {
    model: { revision: number }
    elements: { id: string; name: string | null }[]
    classifiers: { element_id: string; kind: 'CLASS' | 'INTERFACE' }[]
    properties: { element_id: string; owner_classifier_id: string | null; type_classifier_id?: string | null; primitive_type: string | null; lower_bound: number; upper_bound: number | null; is_static: boolean; is_derived: boolean }[]
    associations?: { element_id: string }[]
    associationEnds?: { property_id: string; association_id: string; member_order: number; aggregation: 'NONE' | 'SHARED' | 'COMPOSITE'; ownership: 'ASSOCIATION' | 'CLASSIFIER' }[]
    generalizations?: { specific_classifier_id: string; general_classifier_id: string }[]
  }
}
export interface GenerationProfile {
  id: string; modelId: string; name: string; target: 'SPRING_BOOT'; groupId: string; artifactId: string; basePackage: string
  javaVersion: number; springBootVersion: string; database: 'POSTGRESQL'; configJson: Record<string, unknown>
}
export interface ElementMapping { id: string; umlElementId: string; targetKind: string; configJson: Record<string, unknown> }
const base = (modelId: string) => `/api/uml-models/${modelId}/generation-profiles`
export const logicalModelApi = {
  versions: async (modelId: string) => (await httpClient.get<VersionSnapshot[]>(`/api/uml-models/${modelId}/versions`)).data,
  profiles: async (modelId: string) => (await httpClient.get<GenerationProfile[]>(base(modelId))).data,
  mappings: async (modelId: string, profileId: string) => (await httpClient.get<ElementMapping[]>(`${base(modelId)}/${profileId}/mappings`)).data,
  saveProfile: async (modelId: string, profile: Omit<GenerationProfile, 'id' | 'modelId'>, id?: string) =>
    (id ? await httpClient.put<GenerationProfile>(`${base(modelId)}/${id}`, profile) : await httpClient.post<GenerationProfile>(base(modelId), profile)).data,
  saveMapping: async (modelId: string, profileId: string, mapping: Omit<ElementMapping, 'id'>, id?: string) =>
    (id ? await httpClient.put<ElementMapping>(`${base(modelId)}/${profileId}/mappings/${id}`, mapping) : await httpClient.post<ElementMapping>(`${base(modelId)}/${profileId}/mappings`, mapping)).data,
  build: async (versionId: string, generationProfileId: string) => (await httpClient.post<LogicalModel>(`/api/model-versions/${versionId}/logical-models`, { generationProfileId })).data,
  get: async (id: string) => (await httpClient.get<LogicalModel>(`/api/logical-models/${id}`)).data,
  list: async (modelId: string) => (await httpClient.get<LogicalSummary[]>(`/api/uml-models/${modelId}/logical-models`)).data,
  preview: async (versionId: string, generationProfileId: string) => (await httpClient.post<LogicalPreview>(`/api/model-versions/${versionId}/logical-models/preview`, { generationProfileId })).data,
  generate: async (logicalModelId: string) => (await httpClient.post<GenerationStart>('/api/logical-models/' + logicalModelId + '/generations')).data,
  generation: async (generationId: string) => (await httpClient.get<GenerationRun>('/api/generations/' + generationId)).data,
  generations: async (logicalModelId: string) => (await httpClient.get<GenerationRun[]>('/api/logical-models/' + logicalModelId + '/generations')).data,
  downloadArtifact: async (generationId: string, artifactId: string) => (await httpClient.get<Blob>(`/api/generations/${generationId}/artifacts/${artifactId}/download`, { responseType: 'blob' })).data,
}
