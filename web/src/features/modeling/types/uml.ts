export type UmlClassifierKind = 'CLASS' | 'INTERFACE'
export type UmlVisibility = 'PUBLIC' | 'PRIVATE' | 'PROTECTED' | 'PACKAGE'
export type UmlPrimitiveType =
  | 'STRING'
  | 'INTEGER'
  | 'BOOLEAN'
  | 'REAL'
  | 'UNLIMITED_NATURAL'
export type UmlParameterDirection = 'IN' | 'OUT' | 'INOUT' | 'RETURN'
export type UmlTypeKind = 'PRIMITIVE' | 'CLASSIFIER'
export type UmlModelStatus = 'EDITABLE' | 'ARCHIVED'
export type UmlAggregationKind = 'NONE' | 'SHARED' | 'COMPOSITE'
export type UmlAssociationEndOwnership = 'ASSOCIATION' | 'CLASSIFIER'

export interface UmlModel {
  id: string
  projectId: string
  name: string
  documentation: string | null
  umlVersion: string
  revision: number
  lockVersion: number
  status: UmlModelStatus
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

export interface UmlPackage {
  id: string
  name: string
  parentPackageId: string | null
}

export interface UmlClassifier {
  id: string
  modelId: string
  elementType: 'CLASSIFIER'
  name: string
  documentation: string | null
  packageId: string | null
  kind: UmlClassifierKind
  visibility: UmlVisibility
  isAbstract: boolean
  isActive: boolean
  modelRevision: number
  createdAt: string
  updatedAt: string
}

export interface UmlProperty {
  id: string
  modelId: string
  elementType: 'PROPERTY'
  name: string
  documentation: string | null
  ownerClassifierId: string
  visibility: UmlVisibility
  typeKind: UmlTypeKind
  primitiveType: UmlPrimitiveType | null
  typeClassifierId: string | null
  lowerBound: number
  upperBound: number | null
  isStatic: boolean
  isReadOnly: boolean
  isDerived: boolean
  modelRevision: number
  createdAt: string
  updatedAt: string
}

export interface UmlOperation {
  id: string
  modelId: string
  elementType: 'OPERATION'
  name: string
  documentation: string | null
  ownerClassifierId: string
  visibility: UmlVisibility
  isAbstract: boolean
  isStatic: boolean
  isQuery: boolean
  modelRevision: number
  createdAt: string
  updatedAt: string
}

export interface UmlParameter {
  id: string
  modelId: string
  elementType: 'PARAMETER'
  name: string | null
  documentation: string | null
  operationId: string
  direction: UmlParameterDirection
  typeKind: UmlTypeKind
  primitiveType: UmlPrimitiveType | null
  typeClassifierId: string | null
  lowerBound: number
  upperBound: number | null
  position: number
  modelRevision: number
  createdAt: string
  updatedAt: string
}

export interface UmlOperationView extends UmlOperation {
  parameters: UmlParameter[]
}

export interface UmlClassifierView extends UmlClassifier {
  properties: UmlProperty[]
  operations: UmlOperationView[]
}

export interface UmlAssociationEnd {
  propertyId: string
  roleName: string | null
  classifierId: string
  ownerClassifierId: string | null
  visibility: UmlVisibility
  lowerBound: number
  upperBound: number | null
  navigable: boolean
  aggregation: UmlAggregationKind
  ownership: UmlAssociationEndOwnership
  memberOrder: 1 | 2
}

export interface UmlAssociation {
  id: string
  modelId: string
  name: string | null
  documentation: string | null
  packageId: string | null
  ends: [UmlAssociationEnd, UmlAssociationEnd]
  modelRevision: number
  createdAt: string
  updatedAt: string
}

export interface UmlGeneralization {
  id: string
  modelId: string
  specificClassifierId: string
  generalClassifierId: string
  isSubstitutable: boolean
  documentation: string | null
  modelRevision: number
  createdAt: string
  updatedAt: string
}

export interface UmlRealization {
  id: string
  modelId: string
  clientClassifierId: string
  supplierClassifierId: string
  documentation: string | null
  modelRevision: number
  createdAt: string
  updatedAt: string
}

export interface UmlRelationships {
  associations: UmlAssociation[]
  generalizations: UmlGeneralization[]
  realizations: UmlRealization[]
}

export type DiagramType = 'CLASS'
export type DiagramSide = 'TOP' | 'RIGHT' | 'BOTTOM' | 'LEFT'

export interface DiagramWaypoint {
  x: number
  y: number
}

export interface DiagramNode {
  id: string
  umlElementId: string
  x: number
  y: number
  width: number
  height: number
  zIndex: number
  createdAt: string
  updatedAt: string
}

export interface DiagramEdge {
  id: string
  umlElementId: string
  sourceNodeId: string
  targetNodeId: string
  sourceSide: DiagramSide
  targetSide: DiagramSide
  sourceAnchorIndex: number
  targetAnchorIndex: number
  waypoints: DiagramWaypoint[]
  createdAt: string
  updatedAt: string
}

export interface ClassDiagram {
  id: string
  modelId: string
  ownerPackageId: string | null
  name: string
  type: DiagramType
  revision: number
  lockVersion: number
  createdBy: string | null
  createdAt: string
  updatedAt: string
  nodes: DiagramNode[]
  edges: DiagramEdge[]
}
