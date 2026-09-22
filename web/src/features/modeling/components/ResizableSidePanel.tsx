import { ChevronLeft, ChevronRight, EyeOff, PanelLeftOpen, PanelRightOpen } from 'lucide-react'
import type { PointerEvent as ReactPointerEvent, ReactNode } from 'react'
import type { PanelMode } from '../store/workspaceStore'

export function ResizableSidePanel({ side, label, mode, width, onResize, onModeChange, children }: {
  side: 'LEFT' | 'RIGHT'
  label: string
  mode: PanelMode
  width: number
  onResize: (width: number) => void
  onModeChange: (mode: PanelMode) => void
  children: ReactNode
}) {
  if (mode === 'HIDDEN') return null
  if (mode === 'COLLAPSED') return <aside className="resizable-side-panel workspace-panel relative min-h-0 border-x border-[var(--border)] bg-[var(--surface)]" data-side={side.toLowerCase()}>
    <button className="flex h-full w-11 flex-col items-center gap-3 px-2 py-3 text-xs font-semibold text-[var(--text-secondary)] hover:text-[var(--primary)]" onClick={() => onModeChange('VISIBLE')} title={`Expandir ${label}`} type="button">
      {side === 'LEFT' ? <PanelLeftOpen size={17} /> : <PanelRightOpen size={17} />}
      <span className="[writing-mode:vertical-rl]">{label}</span>
    </button>
  </aside>

  const startResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    event.preventDefault()
    const startX = event.clientX
    const startWidth = width
    const move = (pointer: PointerEvent) => onResize(side === 'LEFT' ? startWidth + pointer.clientX - startX : startWidth + startX - pointer.clientX)
    const stop = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', stop) }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop)
  }

  return <section className="resizable-side-panel workspace-panel relative min-h-0 min-w-0 overflow-hidden bg-[var(--surface)]" data-side={side.toLowerCase()} style={{ width }}>
    <div className={`absolute top-1.5 z-30 flex gap-1 ${side === 'LEFT' ? 'right-2' : 'right-2'}`}>
      <button className="grid size-7 place-items-center rounded-md bg-[var(--surface-secondary)] text-[var(--text-secondary)] hover:text-[var(--primary)]" onClick={() => onModeChange('COLLAPSED')} title={`Colapsar ${label}`} type="button">{side === 'LEFT' ? <ChevronLeft size={14} /> : <ChevronRight size={14} />}</button>
      <button className="grid size-7 place-items-center rounded-md bg-[var(--surface-secondary)] text-[var(--text-secondary)] hover:text-[var(--primary)]" onClick={() => onModeChange('HIDDEN')} title={`Ocultar ${label}`} type="button"><EyeOff size={14} /></button>
    </div>
    <div className="h-full min-w-0 overflow-hidden">{children}</div>
    <div aria-label={`Redimensionar ${label}`} className={`absolute inset-y-0 z-40 w-1 cursor-col-resize hover:bg-[var(--primary)] ${side === 'LEFT' ? 'right-0' : 'left-0'}`} onPointerDown={startResize} role="separator" />
  </section>
}
