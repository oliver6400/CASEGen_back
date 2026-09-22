import { classifierApi } from './classifierApi'
import { featureApi } from './featureApi'
import { modelApi } from './modelApi'
import { relationshipApi } from './relationshipApi'
import type { UmlClassifierView } from '../types/uml'

export async function loadSemanticModel(modelId: string) {
  const [model, classifiers, associations, generalizations, realizations, packages] = await Promise.all([
    modelApi.get(modelId),
    classifierApi.list(modelId),
    relationshipApi.listAssociations(modelId),
    relationshipApi.listGeneralizations(modelId),
    relationshipApi.listRealizations(modelId),
    modelApi.listPackages(modelId),
  ])

  const classifierViews: UmlClassifierView[] = await Promise.all(
    classifiers.map(async (classifier) => {
      const [properties, operations] = await Promise.all([
        featureApi.listProperties(modelId, classifier.id),
        featureApi.listOperations(modelId, classifier.id),
      ])
      const operationViews = await Promise.all(
        operations.map(async (operation) => ({
          ...operation,
          parameters: await featureApi.listParameters(modelId, operation.id),
        })),
      )
      return { ...classifier, properties, operations: operationViews }
    }),
  )

  return { model, packages, classifiers: classifierViews, relationships: { associations, generalizations, realizations } }
}
