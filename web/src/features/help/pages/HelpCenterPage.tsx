import { BookOpen, Lightbulb, MapPin, PlayCircle, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { AppShell } from '../../../components/layout/AppShell'
import { helpSections } from '../helpContent'
import { useHelpStore } from '../helpStore'

export function HelpCenterPage() {
  const [query, setQuery] = useState('')
  const openTour = useHelpStore((state) => state.openTour)
  const filtered = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('es')
    if (!normalized) return helpSections
    return helpSections.filter((section) => [section.title, section.summary, section.screen,
      ...section.steps, ...section.recommendations, ...section.keywords]
      .join(' ').toLocaleLowerCase('es').includes(normalized))
  }, [query])

  return <AppShell>
    <main className="mx-auto max-w-6xl px-4 py-8 sm:px-5 sm:py-10">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div><p className="eyebrow">Centro de ayuda</p><h1 className="page-title">Aprenda a usar CASEGen</h1>
          <p className="mt-2 max-w-2xl text-slate-500">Guías breves para completar el flujo desde el proyecto hasta un backend verificable.</p></div>
        <button className="primary-button" onClick={openTour} type="button"><PlayCircle size={17} /> Iniciar recorrido</button>
      </header>

      <label className="mt-7 flex items-center gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
        <Search size={19} className="shrink-0 text-slate-400" />
        <span className="sr-only">Buscar en la ayuda</span>
        <input className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar relación, backend, imagen, colaborador…" />
      </label>

      <div className="mt-7 grid gap-4 md:grid-cols-2">
        {filtered.map((section) => <article id={section.id} key={section.id} className="scroll-mt-24 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-start gap-3"><BookOpen size={19} className="mt-0.5 shrink-0 text-indigo-600" /><div><h2 className="font-bold text-slate-900">{section.title}</h2><p className="mt-1 text-sm text-slate-600">{section.summary}</p></div></div>
          <ol className="mt-4 list-decimal space-y-1 pl-5 text-sm text-slate-700">{section.steps.map((step) => <li key={step}>{step}</li>)}</ol>
          <div className="mt-4 rounded-xl bg-indigo-50/70 p-3 text-sm text-indigo-950"><p className="flex items-center gap-2 font-semibold"><Lightbulb size={15} /> Recomendaciones</p><ul className="mt-1 list-disc space-y-1 pl-5">{section.recommendations.map((item) => <li key={item}>{item}</li>)}</ul></div>
          <p className="mt-3 flex items-center gap-2 text-xs font-semibold text-slate-500"><MapPin size={14} /> {section.screen}</p>
        </article>)}
      </div>
      {filtered.length === 0 && <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center text-slate-500">No se encontraron temas para “{query}”.</div>}
    </main>
  </AppShell>
}
