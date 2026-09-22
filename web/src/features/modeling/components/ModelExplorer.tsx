import { Box, Braces, ChevronRight, CircleDot, FolderTree, PanelsTopLeft, Plus } from 'lucide-react'
import { useEditorStore } from '../store/editorStore'
import type { ClassDiagram, UmlClassifierView, UmlPackage } from '../types/uml'

export function ModelExplorer({ classifiers, packages, diagrams, activeDiagramId, canEdit, isCreating, onSelectDiagram, onCreateDiagram, validationPanel }: {
  classifiers: UmlClassifierView[]
  packages: UmlPackage[]
  diagrams: ClassDiagram[]
  activeDiagramId: string
  canEdit: boolean
  isCreating: boolean
  onSelectDiagram: (id: string) => void
  onCreateDiagram: () => void
  validationPanel?: React.ReactNode
}) {
  const selectedElementId = useEditorStore((state) => state.selectedElementId)
  const selectElement = useEditorStore((state) => state.selectElement)

  return (
    <aside className="workspace-panel h-full min-h-0 overflow-auto border-r border-[var(--border)] bg-[var(--surface)]" aria-label="Explorador del modelo">
      <div className="panel-heading"><PanelsTopLeft size={16} /> Diagramas</div>
      <div className="border-b border-slate-200 p-2">
        {diagrams.map((diagram) => <button
          aria-current={diagram.id === activeDiagramId ? 'page' : undefined}
          className={`explorer-row ${diagram.id === activeDiagramId ? 'explorer-row-active' : ''}`}
          key={diagram.id} onClick={() => onSelectDiagram(diagram.id)} type="button"
        ><PanelsTopLeft size={15} /><span className="truncate">{diagram.name}</span>{diagram.id === activeDiagramId && <span className="ml-auto text-[10px] font-semibold">Activo</span>}</button>)}
        {canEdit && <button className="toolbar-button mt-2 w-full" disabled={isCreating} onClick={onCreateDiagram} title="Crear un diagrama hermano en este modelo UML" type="button"><Plus size={15} /> Diagrama</button>}
      </div>
      {validationPanel}
      <div className="panel-heading"><FolderTree size={16} /> Elementos UML</div>
      <div className="p-2">
        {packages.map((item) => <button className={`explorer-row ${selectedElementId === item.id ? 'explorer-row-active' : 'text-slate-500'}`} key={item.id} onClick={() => selectElement(item.id)} type="button"><FolderTree size={15} /><span className="truncate">{item.name}</span></button>)}
        {classifiers.map((classifier) => (
          <div key={classifier.id}>
            <button className={`explorer-row ${selectedElementId === classifier.id ? 'explorer-row-active' : ''}`} onClick={() => selectElement(classifier.id)} type="button">
              <ChevronRight size={14} /><Box size={15} className={classifier.kind === 'INTERFACE' ? 'text-teal-600' : 'text-indigo-600'} /><span className="truncate">{classifier.name}</span>
            </button>
            <div className="ml-8 border-l border-slate-100 pl-2 text-xs text-slate-500">
              {classifier.properties.map((property) => <button className="explorer-child" key={property.id} onClick={() => selectElement(property.id)} type="button"><CircleDot size={11} />{property.name}</button>)}
              {classifier.operations.map((operation) => <div key={operation.id}><button className="explorer-child" onClick={() => selectElement(operation.id)} type="button"><Braces size={11} />{operation.name}()</button>{[...operation.parameters].sort((a, b) => a.position - b.position).map((parameter) => <button className="explorer-child !ml-5 !w-[calc(100%-1.25rem)] !text-[11px]" key={parameter.id} onClick={() => selectElement(parameter.id)} type="button">↳ {parameter.direction} {parameter.name || 'retorno'}</button>)}</div>)}
            </div>
          </div>
        ))}
        {classifiers.length === 0 && <p className="p-3 text-xs leading-5 text-slate-400">Aquí aparecerán clases, interfaces y sus features.</p>}
      </div>
    </aside>
  )
}
