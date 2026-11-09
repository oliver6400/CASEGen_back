import type {
  ImageReconciliationAction,
  ImageImportExistingConfirmRequest,
  ImageReconciliationItem,
  ImageReconciliationPreviewResponse,
  DetectedClassDiagram,
} from '../api/modelApi'

export interface ImageReconciliationDecision {
  action: ImageReconciliationAction
  candidateId: string | null
}

export type ImageReconciliationDecisions = Record<string, ImageReconciliationDecision>

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function initialImageReconciliationDecisions(preview: ImageReconciliationPreviewResponse) {
  return Object.fromEntries(preview.items.map((item) => [item.temporaryId, {
    action: item.suggestedAction,
    candidateId: item.suggestedAction === 'MAP_TO_EXISTING' && item.candidates.length === 1
      ? item.candidates[0].elementId
      : null,
  }])) as ImageReconciliationDecisions
}

export function imageReconciliationDependencyIssue(
  item: ImageReconciliationItem,
  preview: ImageReconciliationPreviewResponse,
  decisions: ImageReconciliationDecisions,
) {
  for (const dependencyId of item.dependsOnTemporaryIds) {
    const dependency = preview.items.find((candidate) => candidate.temporaryId === dependencyId)
    if (!dependency || dependency.elementKind !== 'CLASSIFIER') continue
    const decision = decisions[dependencyId]
    if (!decision || decision.action === 'CONFLICT' || decision.action === 'SKIP') {
      return `La referencia ${dependency.detectedName} todavía no tiene una resolución utilizable.`
    }
    if (decision.action === 'MAP_TO_EXISTING' && !decision.candidateId) {
      return `Seleccione el elemento existente para ${dependency.detectedName}.`
    }
  }
  return null
}

export function isImageReconciliationDecisionPending(
  item: ImageReconciliationItem,
  preview: ImageReconciliationPreviewResponse,
  decisions: ImageReconciliationDecisions,
) {
  const decision = decisions[item.temporaryId]
  return !decision
    || decision.action === 'CONFLICT'
    || (decision.action === 'MAP_TO_EXISTING' && !decision.candidateId)
    || imageReconciliationDependencyIssue(item, preview, decisions) !== null
}

export function summarizeImageReconciliation(
  preview: ImageReconciliationPreviewResponse,
  decisions: ImageReconciliationDecisions,
) {
  const summary = { create: 0, map: 0, skip: 0, pending: 0 }
  for (const item of preview.items) {
    if (isImageReconciliationDecisionPending(item, preview, decisions)) {
      summary.pending += 1
      continue
    }
    const action = decisions[item.temporaryId].action
    if (action === 'CREATE') summary.create += 1
    else if (action === 'MAP_TO_EXISTING') summary.map += 1
    else if (action === 'SKIP') summary.skip += 1
  }
  return summary
}

export function buildImageImportConfirmation(
  preview: ImageReconciliationPreviewResponse,
  proposal: DetectedClassDiagram,
  decisions: ImageReconciliationDecisions,
): ImageImportExistingConfirmRequest {
  const expectedIds = new Set(preview.items.map((item) => item.temporaryId))
  if (expectedIds.size !== preview.items.length) {
    throw new Error('La reconciliación contiene identificadores temporales duplicados.')
  }
  if (Object.keys(decisions).some((temporaryId) => !expectedIds.has(temporaryId))) {
    throw new Error('Las decisiones contienen elementos que no pertenecen a la vista previa vigente.')
  }
  const definitive = preview.items.map((item) => {
    const decision = decisions[item.temporaryId]
    if (!decision || decision.action === 'CONFLICT') {
      throw new Error(`Resuelva la decisión pendiente para ${item.detectedName}.`)
    }
    const dependencyIssue = imageReconciliationDependencyIssue(item, preview, decisions)
    if (dependencyIssue) throw new Error(dependencyIssue)
    if (decision.action === 'CREATE' && item.suggestedAction !== 'CREATE') {
      throw new Error(`La vista previa vigente no permite crear ${item.detectedName}.`)
    }
    if (decision.action === 'MAP_TO_EXISTING') {
      if (!decision.candidateId || !UUID_PATTERN.test(decision.candidateId)
          || !item.candidates.some((candidate) => candidate.elementId === decision.candidateId)) {
        throw new Error(`Seleccione un candidato válido para ${item.detectedName}.`)
      }
    } else if (decision.candidateId !== null) {
      throw new Error(`La decisión de ${item.detectedName} no debe incluir un candidato.`)
    }
    return { temporaryId: item.temporaryId, action: decision.action, candidateId: decision.candidateId }
  })
  return {
    targetModelId: preview.targetModelId,
    targetDiagramId: preview.targetDiagramId,
    targetPackageId: preview.targetPackageId,
    expectedModelRevision: preview.modelRevision,
    expectedDiagramRevision: preview.diagramRevision,
    proposal,
    decisions: definitive,
  }
}
