import { httpClient } from '../../../api/httpClient'
import type { ClassDiagram, DiagramEdge, DiagramNode, DiagramSide, DiagramWaypoint } from '../types/uml'

export interface DiagramGeometryInput {
  x: number
  y: number
  width: number
  height: number
  zIndex?: number
}

export interface DiagramEdgeLayoutInput {
  sourceSide: DiagramSide
  targetSide: DiagramSide
  sourceAnchorIndex: number
  targetAnchorIndex: number
  waypoints: DiagramWaypoint[]
}

export const diagramApi = {
  list: async (modelId: string) =>
    (await httpClient.get<ClassDiagram[]>(`/api/uml-models/${modelId}/diagrams`)).data,
  get: async (diagramId: string) =>
    (await httpClient.get<ClassDiagram>(`/api/diagrams/${diagramId}`)).data,
  create: async (modelId: string, name: string) =>
    (await httpClient.post<ClassDiagram>(`/api/uml-models/${modelId}/diagrams`, {
      name, type: 'CLASS', ownerPackageId: null,
    })).data,
  addNode: async (diagramId: string, umlElementId: string, geometry: DiagramGeometryInput) =>
    (await httpClient.post<DiagramNode>(`/api/diagrams/${diagramId}/nodes`, {
      umlElementId, ...geometry,
    })).data,
  updateNodeGeometry: async (diagramId: string, nodeId: string, geometry: DiagramGeometryInput) =>
    (await httpClient.patch<DiagramNode>(`/api/diagrams/${diagramId}/nodes/${nodeId}/geometry`, geometry)).data,
  removeNode: async (diagramId: string, nodeId: string) =>
    void (await httpClient.delete(`/api/diagrams/${diagramId}/nodes/${nodeId}`)),
  addEdge: async (diagramId: string, umlElementId: string, sourceNodeId: string, targetNodeId: string, layout: DiagramEdgeLayoutInput) =>
    (await httpClient.post<DiagramEdge>(`/api/diagrams/${diagramId}/edges`, {
      umlElementId, sourceNodeId, targetNodeId, ...layout,
    })).data,
  updateEdgeLayout: async (diagramId: string, edgeId: string, layout: DiagramEdgeLayoutInput) =>
    (await httpClient.patch<DiagramEdge>(`/api/diagrams/${diagramId}/edges/${edgeId}/layout`, layout)).data,
  removeEdge: async (diagramId: string, edgeId: string) =>
    void (await httpClient.delete(`/api/diagrams/${diagramId}/edges/${edgeId}`)),
}
