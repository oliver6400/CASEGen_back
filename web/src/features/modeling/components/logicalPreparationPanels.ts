export type PreparationPanel = 'PROFILE' | 'MAPPINGS' | 'RELATIONSHIPS' | 'SUMMARY'
export const OPEN_PREPARATION_PANEL_EVENT = 'casegen:open-logical-preparation-panel'

export function openLogicalPreparationPanel(panel: PreparationPanel) {
  window.dispatchEvent(new CustomEvent<PreparationPanel>(OPEN_PREPARATION_PANEL_EVENT, { detail: panel }))
}
