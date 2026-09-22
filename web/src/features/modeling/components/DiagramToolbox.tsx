import { Box, Diamond, GitBranch, Waypoints, X } from 'lucide-react'
import type { UmlClassifierKind } from '../types/uml'
import type { RelationshipTool } from './ModelingToolbar'

export function DiagramToolbox({ disabled, onClose, onCreateClassifier, onCreateRelationship }: {
  disabled: boolean
  onClose: () => void
  onCreateClassifier: (kind: UmlClassifierKind) => void
  onCreateRelationship: (tool: RelationshipTool) => void
}) {
  return <aside className="diagram-toolbox workspace-panel min-h-0 w-60 overflow-auto border-l border-[var(--border)] bg-[var(--surface)]" aria-label="Toolbox UML">
    <div className="panel-heading justify-between"><span>Toolbox</span><button className="grid size-7 place-items-center rounded-md hover:bg-[var(--surface-secondary)]" onClick={onClose} title="Cerrar Toolbox" type="button"><X size={15} /></button></div>
    <ToolGroup title="Elementos">
      <ToolButton disabled={disabled} icon={<Box size={15} />} label="Clase" onClick={() => onCreateClassifier('CLASS')} />
      <ToolButton disabled={disabled} icon={<Box className="text-teal-600" size={15} />} label="Interface" onClick={() => onCreateClassifier('INTERFACE')} />
    </ToolGroup>
    <ToolGroup title="Relaciones">
      <ToolButton disabled={disabled} icon={<Waypoints size={15} />} label="Asociación" title="Vínculo estructural entre classifiers." onClick={() => onCreateRelationship('ASSOCIATION')} />
      <ToolButton disabled={disabled} icon={<Diamond size={15} />} label="Agregación" title="Relación todo-parte débil." onClick={() => onCreateRelationship('AGGREGATION')} />
      <ToolButton disabled={disabled} icon={<Diamond fill="currentColor" size={15} />} label="Composición" title="Relación todo-parte fuerte." onClick={() => onCreateRelationship('COMPOSITION')} />
      <ToolButton disabled={disabled} icon={<GitBranch size={15} />} label="Generalización" title="Herencia hacia un classifier general." onClick={() => onCreateRelationship('GENERALIZATION')} />
      <ToolButton disabled={disabled} icon={<GitBranch size={15} />} label="Realización" title="Implementación de una interfaz." onClick={() => onCreateRelationship('REALIZATION')} />
    </ToolGroup>
  </aside>
}

function ToolGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className="border-b border-[var(--border)] p-3"><h3 className="mb-2 text-[11px] font-bold uppercase tracking-wider text-[var(--text-secondary)]">{title}</h3><div className="grid gap-1">{children}</div></section>
}
function ToolButton({ icon, label, title, disabled, onClick }: { icon: React.ReactNode; label: string; title?: string; disabled: boolean; onClick: () => void }) {
  return <button className="flex items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-[var(--text-primary)] hover:bg-[var(--surface-secondary)] hover:text-[var(--primary)]" disabled={disabled} onClick={onClick} title={title} type="button">{icon}<span>{label}</span></button>
}
