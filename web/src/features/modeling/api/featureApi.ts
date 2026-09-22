import { httpClient } from '../../../api/httpClient'
import type {
  UmlOperation,
  UmlParameter,
  UmlParameterDirection,
  UmlPrimitiveType,
  UmlProperty,
  UmlTypeKind,
  UmlVisibility,
} from '../types/uml'

interface TypedFeatureInput {
  typeKind: UmlTypeKind
  primitiveType?: UmlPrimitiveType | null
  typeClassifierId?: string | null
  lowerBound?: number
  upperBound?: number | null
}

export interface CreatePropertyInput extends TypedFeatureInput {
  name: string
  documentation?: string | null
  visibility?: UmlVisibility
  isStatic?: boolean
  isReadOnly?: boolean
  isDerived?: boolean
}

export interface CreateOperationInput {
  name: string
  documentation?: string | null
  visibility?: UmlVisibility
  isAbstract?: boolean
  isStatic?: boolean
  isQuery?: boolean
}

export interface CreateParameterInput extends TypedFeatureInput {
  name?: string | null
  documentation?: string | null
  direction: UmlParameterDirection
  position: number
}

export const featureApi = {
  listProperties: async (modelId: string, classifierId: string) =>
    (
      await httpClient.get<UmlProperty[]>(
        `/api/uml-models/${modelId}/classifiers/${classifierId}/properties`,
      )
    ).data,
  createProperty: async (
    modelId: string,
    classifierId: string,
    input: CreatePropertyInput,
  ) =>
    (
      await httpClient.post<UmlProperty>(
        `/api/uml-models/${modelId}/classifiers/${classifierId}/properties`,
        input,
      )
    ).data,
  updateProperty: async (modelId: string, classifierId: string, propertyId: string, input: CreatePropertyInput) =>
    (await httpClient.put<UmlProperty>(`/api/uml-models/${modelId}/classifiers/${classifierId}/properties/${propertyId}`, input)).data,
  deleteProperty: async (modelId: string, classifierId: string, propertyId: string) =>
    void (await httpClient.delete(`/api/uml-models/${modelId}/classifiers/${classifierId}/properties/${propertyId}`)),
  listOperations: async (modelId: string, classifierId: string) =>
    (
      await httpClient.get<UmlOperation[]>(
        `/api/uml-models/${modelId}/classifiers/${classifierId}/operations`,
      )
    ).data,
  createOperation: async (
    modelId: string,
    classifierId: string,
    input: CreateOperationInput,
  ) =>
    (
      await httpClient.post<UmlOperation>(
        `/api/uml-models/${modelId}/classifiers/${classifierId}/operations`,
        input,
      )
    ).data,
  updateOperation: async (modelId: string, classifierId: string, operationId: string, input: CreateOperationInput) =>
    (await httpClient.put<UmlOperation>(`/api/uml-models/${modelId}/classifiers/${classifierId}/operations/${operationId}`, input)).data,
  deleteOperation: async (modelId: string, classifierId: string, operationId: string) =>
    void (await httpClient.delete(`/api/uml-models/${modelId}/classifiers/${classifierId}/operations/${operationId}`)),
  listParameters: async (modelId: string, operationId: string) =>
    (
      await httpClient.get<UmlParameter[]>(
        `/api/uml-models/${modelId}/operations/${operationId}/parameters`,
      )
    ).data,
  createParameter: async (
    modelId: string,
    operationId: string,
    input: CreateParameterInput,
  ) =>
    (
      await httpClient.post<UmlParameter>(
        `/api/uml-models/${modelId}/operations/${operationId}/parameters`,
        input,
      )
    ).data,
  updateParameter: async (modelId: string, operationId: string, parameterId: string, input: CreateParameterInput) =>
    (await httpClient.put<UmlParameter>(`/api/uml-models/${modelId}/operations/${operationId}/parameters/${parameterId}`, input)).data,
  deleteParameter: async (modelId: string, operationId: string, parameterId: string) =>
    void (await httpClient.delete(`/api/uml-models/${modelId}/operations/${operationId}/parameters/${parameterId}`)),
}
