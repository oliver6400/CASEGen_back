import { useEffect, useState } from 'react'
import { BookOpen, MapPin, X } from 'lucide-react'
import { tourSteps } from '../helpContent'
import { useHelpStore } from '../helpStore'

const STORAGE_KEY = 'casegen.help-tour.v1'

export function HelpTour() {
  const open = useHelpStore((state) => state.tourOpen)
  const openTour = useHelpStore((state) => state.openTour)
  const closeTour = useHelpStore((state) => state.closeTour)
  const [index, setIndex] = useState(0)

  useEffect(() => {
    if (!localStorage.getItem(STORAGE_KEY)) openTour()
  }, [openTour])

  if (!open) return null
  const step = tourSteps[index]
  const finish = (status: 'completed' | 'skipped') => {
    localStorage.setItem(STORAGE_KEY, status)
    setIndex(0)
    closeTour()
  }

  return <div className="fixed inset-0 z-[100] grid place-items-center bg-slate-950/45 p-4" role="dialog" aria-modal="true" aria-labelledby="help-tour-title">
    <section className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl">
      <div className="flex items-start justify-between gap-4">
        <div className="flex gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-indigo-100 text-indigo-700"><BookOpen size={20} /></span>
          <div><p className="text-xs font-bold uppercase tracking-wider text-indigo-600">Recorrido CASEGen</p><h2 id="help-tour-title" className="mt-1 text-xl font-bold">{step.title}</h2></div>
        </div>
        <button className="icon-button" onClick={() => finish('skipped')} title="Omitir recorrido" type="button"><X size={17} /></button>
      </div>
      <p className="mt-5 text-slate-600">{step.text}</p>
      <p className="mt-4 flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600"><MapPin size={15} className="text-indigo-600" /> Pantalla: {step.screen}</p>
      <div className="mt-5 flex items-center justify-between gap-3">
        <span className="text-xs font-semibold text-slate-400">{index + 1} de {tourSteps.length}</span>
        <div className="flex flex-wrap justify-end gap-2">
          <button className="toolbar-button" onClick={() => finish('skipped')} type="button">Omitir</button>
          <button className="toolbar-button" disabled={index === 0} onClick={() => setIndex((value) => value - 1)} type="button">Anterior</button>
          {index < tourSteps.length - 1
            ? <button className="primary-button" onClick={() => setIndex((value) => value + 1)} type="button">Siguiente</button>
            : <button className="primary-button" onClick={() => finish('completed')} type="button">Finalizar</button>}
        </div>
      </div>
    </section>
  </div>
}
