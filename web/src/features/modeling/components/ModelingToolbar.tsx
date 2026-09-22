import { ArrowLeft, CircleHelp, Download, Eye, Focus, Maximize2, PanelLeftOpen, PanelRightOpen, Palette, Plus, RefreshCw, RotateCcw, Wrench } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAppearanceStore } from '../../appearance/appearanceStore'
import type { ClassDiagram, UmlClassifierKind } from '../types/uml'

export type RelationshipTool = 'ASSOCIATION' | 'AGGREGATION' | 'COMPOSITION' | 'GENERALIZATION' | 'REALIZATION'

interface ModelingToolbarProps {
  projectId: string
  projectName: string
  modelName: string
  semanticRevision: number
  diagram: ClassDiagram
  canEdit: boolean
  isCreating: boolean
  onAddExisting: () => void
  onSyncRelationships: () => void
  onCreateClassifier: (kind: UmlClassifierKind) => void
  onCreateRelationship: (tool: RelationshipTool) => void
  onToggleToolbox: () => void
  onShowLeftPanel: () => void
  onShowRightPanel: () => void
  onFocusDiagram: () => void
  onRestorePanels: () => void
  leftPanelHidden: boolean
  rightPanelHidden: boolean
  toolboxVisible: boolean
  focusMode: boolean
  onRefresh: () => void
  onFitView: () => void
  onExportXmi: () => void
  isExportingXmi: boolean
}

export function ModelingToolbar({ projectId, projectName, modelName, semanticRevision, diagram, canEdit, isCreating, onAddExisting, onSyncRelationships, onToggleToolbox, onShowLeftPanel, onShowRightPanel, onFocusDiagram, onRestorePanels, leftPanelHidden, rightPanelHidden, toolboxVisible, focusMode, onRefresh, onFitView, onExportXmi, isExportingXmi }: ModelingToolbarProps) {
  const openAppearance = useAppearanceStore((state) => state.openPanel)
  return (
    <nav className="workspace-panel grid min-w-0 gap-2 border-b border-[var(--border)] bg-[var(--surface)] px-4 py-2" style={{ gridColumn: '1 / -1' }} aria-label="Barra de modelado">
      <div className="flex min-w-0 items-center gap-3">
        <Link className="icon-button" to={`/projects/${projectId}`} title="Volver al proyecto"><ArrowLeft size={18} /></Link>
        <div className="min-w-0">
          <div className="flex items-center gap-1 text-xs text-slate-400" aria-label="Breadcrumb">
            <Link className="hover:text-indigo-600" to={`/projects/${projectId}`}>{projectName}</Link><span>&gt;</span>
            <span>{modelName}</span><span>&gt;</span>
            <strong className="truncate text-slate-700">{diagram.name}</strong>
          </div>
          <div className="truncate text-sm font-semibold">{modelName} · {diagram.name}</div>
          <div className="text-[10px] text-slate-400">{diagram.type} · Revisión gráfica {diagram.revision} · Modelo r{semanticRevision}</div>
        </div>
      </div>
      <div className="flex items-center gap-2 overflow-x-auto pb-1 [&>button]:shrink-0">
        {!canEdit && <span className="permission-badge"><Eye size={13} /> Solo lectura</span>}
        {canEdit && <>
          <button className="toolbar-button" disabled={isCreating} onClick={onAddExisting} type="button"><Plus size={15} /> Existente</button>
          <button className="toolbar-button" disabled={isCreating} onClick={onSyncRelationships} type="button"><RefreshCw size={15} /> Relaciones</button>
          <button className="toolbar-button" aria-pressed={toolboxVisible} onClick={onToggleToolbox} type="button"><Wrench size={15} /> Toolbox</button>
        </>}
        <button className="toolbar-button" disabled={isExportingXmi} onClick={onExportXmi} title={`${modelName}.xmi · UML/XMI`} type="button"><Download size={15} /> {isExportingXmi ? 'Exportando…' : 'Exportar XMI'}</button>
        {leftPanelHidden && <button className="toolbar-button" onClick={onShowLeftPanel} type="button"><PanelLeftOpen size={15} /> Diagramas</button>}
        {rightPanelHidden && <button className="toolbar-button" onClick={onShowRightPanel} type="button"><PanelRightOpen size={15} /> Propiedades</button>}
        {!focusMode ? <button className="icon-button" onClick={onFocusDiagram} title="Enfocar diagrama" type="button"><Focus size={17} /></button> : <button className="toolbar-button" onClick={onRestorePanels} type="button"><RotateCcw size={15} /> Restaurar paneles</button>}
        <button className="icon-button" onClick={onFitView} title="Ajustar diagrama" type="button"><Maximize2 size={17} /></button>
        <button className="icon-button" onClick={onRefresh} title="Actualizar modelo" type="button"><RefreshCw size={17} /></button>
        <button className="icon-button" onClick={openAppearance} title="Personalizar apariencia" type="button"><Palette size={17} /></button>
        <Link className="toolbar-button shrink-0" to="/help" title="Abrir centro de ayuda"><CircleHelp size={17} /> Ayuda</Link>
      </div>
    </nav>
  )
}
