import { X } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'

export function WorkspaceDrawer({ open, title, onClose, children }: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
}) {
  useEffect(() => {
    if (!open) return
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [onClose, open])

  if (!open) return null
  return <aside className="fixed bottom-0 left-0 top-[6.75rem] z-[55] flex w-[min(420px,92vw)] flex-col border-r border-[var(--border)] bg-[var(--surface)] text-[var(--text-primary)] shadow-2xl" aria-label={title}>
    <header className="flex items-center justify-between border-b border-[var(--border)] px-4 py-3">
      <h2 className="font-bold">{title}</h2>
      <button className="icon-button" onClick={onClose} title={`Cerrar ${title}`} type="button"><X size={17} /></button>
    </header>
    <div className="min-h-0 flex-1 overflow-y-auto p-4">{children}</div>
  </aside>
}
