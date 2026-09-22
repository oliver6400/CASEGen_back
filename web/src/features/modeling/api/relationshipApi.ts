import { httpClient } from '../../../api/httpClient'
import type {
  UmlAggregationKind,
  UmlAssociation,
  UmlAssociationEndOwnership,
  UmlGeneralization,
  UmlRealization,
  UmlVisibility,
} from '../types/uml'

export interface AssociationEndInput {
  roleName?: string | null
  classifierId: string
  visibility?: UmlVisibility
  lowerBound?: number
  upperBound?: number | null
  navigable?: boolean
  aggregation?: UmlAggregationKind
  ownership?: UmlAssociationEndOwnership
}

export interface AssociationInput {
  name?: string | null
  documentation?: string | null
  packageId?: string | null
  firstEnd: AssociationEndInput
  secondEnd: AssociationEndInput
}

export const relationshipApi = {
  listAssociations: async (modelId: string) =>
    (await httpClient.get<UmlAssociation[]>(`/api/uml-models/${modelId}/associations`)).data,
  createAssociation: async (modelId: string, input: AssociationInput) =>
    (await httpClient.post<UmlAssociation>(`/api/uml-models/${modelId}/associations`, input)).data,
  updateAssociation: async (modelId: string, associationId: string, input: AssociationInput) =>
    (await httpClient.put<UmlAssociation>(`/api/uml-models/${modelId}/associations/${associationId}`, input)).data,
  deleteAssociation: async (modelId: string, associationId: string) =>
    void (await httpClient.delete(`/api/uml-models/${modelId}/associations/${associationId}`)),

  listGeneralizations: async (modelId: string) =>
    (await httpClient.get<UmlGeneralization[]>(`/api/uml-models/${modelId}/generalizations`)).data,
  createGeneralization: async (modelId: string, specificClassifierId: string, generalClassifierId: string) =>
    (await httpClient.post<UmlGeneralization>(`/api/uml-models/${modelId}/generalizations`, {
      specificClassifierId, generalClassifierId, isSubstitutable: true,
    })).data,
  deleteGeneralization: async (modelId: string, generalizationId: string) =>
    void (await httpClient.delete(`/api/uml-models/${modelId}/generalizations/${generalizationId}`)),

  listRealizations: async (modelId: string) =>
    (await httpClient.get<UmlRealization[]>(`/api/uml-models/${modelId}/realizations`)).data,
  createRealization: async (modelId: string, clientClassifierId: string, supplierClassifierId: string) =>
    (await httpClient.post<UmlRealization>(`/api/uml-models/${modelId}/realizations`, {
      clientClassifierId, supplierClassifierId,
    })).data,
  deleteRealization: async (modelId: string, realizationId: string) =>
    void (await httpClient.delete(`/api/uml-models/${modelId}/realizations/${realizationId}`)),
}
