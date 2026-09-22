import { Check, Palette, RotateCcw, X } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'
import { useAppearanceStore, type AppFont, type AppTheme, type PrimaryColor, type TextSize } from '../appearanceStore'

const themeLabels: Record<AppTheme, string> = { LIGHT: 'Claro', DARK: 'Oscuro', SOFT: 'Suave / neutral' }
const colorLabels: Record<PrimaryColor, string> = { VIOLET: 'Violeta', BLUE: 'Azul', GREEN: 'Verde', GRAY: 'Gris' }
const fontLabels: Record<AppFont, string> = { INTER: 'Inter', ROBOTO: 'Roboto', SYSTEM: 'Arial / system' }
const sizeLabels: Record<TextSize, string> = { SMALL: 'Pequeño', NORMAL: 'Normal', LARGE: 'Grande' }

export function AppearanceManager({ children }: { children: ReactNode }) {
  const state = useAppearanceStore()
  useEffect(() => {
    const root = document.documentElement
    root.dataset.appTheme = state.theme.toLowerCase()
    root.dataset.primaryColor = state.primaryColor.toLowerCase()
    root.dataset.textSize = state.textSize.toLowerCase()
    root.style.setProperty('--font-family', fontValue(state.font))
  }, [state.font, state.primaryColor, state.textSize, state.theme])

  return <>{children}{state.panelOpen && <aside className="fixed inset-y-0 right-0 z-[90] w-full max-w-sm overflow-auto border-l border-[var(--border)] bg-[var(--surface)] p-5 text-[var(--text-primary)] shadow-2xl" aria-label="Apariencia">
    <div className="flex items-center justify-between"><h2 className="flex items-center gap-2 text-lg font-bold"><Palette size={19} /> Apariencia</h2><button className="icon-button" onClick={state.closePanel} title="Cerrar apariencia" type="button"><X size={17} /></button></div>
    <p className="mt-2 text-sm text-[var(--text-secondary)]">Personalice solamente la presentación en este navegador.</p>
    <AppearanceChoice title="Tema" values={themeLabels} selected={state.theme} onSelect={(theme) => state.update({ theme })} />
    <AppearanceChoice title="Color principal" values={colorLabels} selected={state.primaryColor} onSelect={(primaryColor) => state.update({ primaryColor })} />
    <AppearanceChoice title="Fuente" values={fontLabels} selected={state.font} onSelect={(font) => state.update({ font })} />
    <AppearanceChoice title="Tamaño de texto" values={sizeLabels} selected={state.textSize} onSelect={(textSize) => state.update({ textSize })} />
    <button className="toolbar-button mt-6 w-full justify-center" onClick={state.reset} type="button"><RotateCcw size={15} /> Restablecer apariencia</button>
  </aside>}</>
}

function AppearanceChoice<T extends string>({ title, values, selected, onSelect }: { title: string; values: Record<T, string>; selected: T; onSelect: (value: T) => void }) {
  return <fieldset className="mt-6"><legend className="mb-2 text-xs font-bold uppercase tracking-wider text-[var(--text-secondary)]">{title}</legend><div className="grid gap-2 sm:grid-cols-2">{(Object.entries(values) as [T, string][]).map(([value, label]) => <button className={`flex items-center justify-between rounded-xl border px-3 py-2 text-left text-sm transition ${selected === value ? 'border-[var(--primary)] bg-[var(--surface-secondary)] text-[var(--primary)]' : 'border-[var(--border)] hover:border-[var(--primary)]'}`} key={value} onClick={() => onSelect(value)} type="button">{label}{selected === value && <Check size={15} />}</button>)}</div></fieldset>
}

function fontValue(font: AppFont) {
  if (font === 'ROBOTO') return 'Roboto, Arial, sans-serif'
  if (font === 'SYSTEM') return 'Arial, ui-sans-serif, system-ui, sans-serif'
  return 'Inter, ui-sans-serif, system-ui, sans-serif'
}
