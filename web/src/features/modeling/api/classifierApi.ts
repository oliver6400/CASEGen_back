import { httpClient } from '../../../api/httpClient'
import type {
  UmlClassifier,
  UmlClassifierKind,
  UmlVisibility,
} from '../types/uml'

export interface CreateClassifierInput {
  name: string
  documentation?: string | null
  packageId?: string | null
  kind: UmlClassifierKind
  visibility?: UmlVisibility
  isAbstract?: boolean
  isActive?: boolean
}

export const classifierApi = {
  list: async (modelId: string) =>
    (
      await httpClient.get<UmlClassifier[]>(
        `/api/uml-models/${modelId}/classifiers`,
      )
    ).data,
  create: async (modelId: string, input: CreateClassifierInput) =>
    (
      await httpClient.post<UmlClassifier>(
        `/api/uml-models/${modelId}/classifiers`,
        input,
      )
    ).data,
  update: async (modelId: string, classifierId: string, input: CreateClassifierInput) =>
    (await httpClient.put<UmlClassifier>(`/api/uml-models/${modelId}/classifiers/${classifierId}`, input)).data,
  remove: async (modelId: string, classifierId: string) =>
    void (await httpClient.delete(`/api/uml-models/${modelId}/classifiers/${classifierId}`)),
}
