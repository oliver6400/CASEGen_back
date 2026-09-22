import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from 'lucide-react'
import { useEffect, useState } from 'react'

type AlertVariant = 'SUCCESS' | 'INFO' | 'WARNING' | 'ERROR'
const styles: Record<AlertVariant, string> = {
  SUCCESS: 'border-emerald-300 bg-emerald-50 text-emerald-800',
  INFO: 'border-blue-300 bg-blue-50 text-blue-800',
  WARNING: 'border-amber-300 bg-amber-50 text-amber-900',
  ERROR: 'border-rose-300 bg-rose-50 text-rose-800',
}
const icons = { SUCCESS: CircleCheck, INFO: Info, WARNING: TriangleAlert, ERROR: CircleAlert }

export function DismissibleAlert({ variant = 'INFO', children, autoCloseMs }: { variant?: AlertVariant; children: React.ReactNode; autoCloseMs?: number }) {
  const [visible, setVisible] = useState(true)
  useEffect(() => {
    if (!autoCloseMs || !visible) return
    const timeout = window.setTimeout(() => setVisible(false), autoCloseMs)
    return () => window.clearTimeout(timeout)
  }, [autoCloseMs, visible])
  if (!visible) return null
  const Icon = icons[variant]
  return <div className={`flex items-start gap-2 rounded-xl border px-3 py-2.5 text-sm shadow-lg ${styles[variant]}`} role="alert"><Icon className="mt-0.5 shrink-0" size={16} /><div className="min-w-0 flex-1">{children}</div><button className="grid size-6 shrink-0 place-items-center rounded-md hover:bg-black/5" onClick={() => setVisible(false)} title="Cerrar aviso" type="button"><X size={14} /></button></div>
}
