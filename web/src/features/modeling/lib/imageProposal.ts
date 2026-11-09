import type { DetectedClassDiagram } from '../api/modelApi'

export function applyVisibilityDefaults(proposal: DetectedClassDiagram): DetectedClassDiagram {
  const visibility = (value: string | null | undefined, fallback: string) => {
    const normalized = value?.trim().toUpperCase()
    return !normalized || normalized === 'UNKNOWN' || normalized === 'NOT_APPLICABLE'
      ? fallback
      : normalized
  }
  const associationTypes = new Set(['ASSOCIATION', 'AGGREGATION', 'COMPOSITION'])
  return {
    ...proposal,
    detectedClassifiers: proposal.detectedClassifiers.map((value) => ({
      ...value,
      visibility: visibility(value.visibility, 'PUBLIC'),
    })),
    detectedAttributes: proposal.detectedAttributes.map((value) => ({
      ...value,
      visibility: visibility(value.visibility, 'PRIVATE'),
    })),
    detectedOperations: proposal.detectedOperations.map((value) => ({
      ...value,
      visibility: visibility(value.visibility, 'PUBLIC'),
    })),
    detectedRelationships: proposal.detectedRelationships.map((value) => associationTypes.has(value.type)
      ? {
          ...value,
          source: { ...value.source, visibility: visibility(value.source.visibility, 'PUBLIC') },
          target: { ...value.target, visibility: visibility(value.target.visibility, 'PUBLIC') },
        }
      : value),
  }
}
