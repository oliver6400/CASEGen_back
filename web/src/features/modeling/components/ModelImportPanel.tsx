import { FileCode2, ImageIcon, Import } from 'lucide-react'
import { useState } from 'react'
import { ImageImportPanel } from './ImageImportPanel'
import { XmiImportPanel } from './XmiImportPanel'

export function ModelImportPanel({ projectId }: { projectId: string }) {
  const [mode, setMode] = useState<'xmi' | 'image'>('xmi')
  return <section className="mb-8 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <div className="mb-4 flex items-center gap-2 font-semibold"><Import size={18} className="text-indigo-600" /> Importar</div>
    <div className="mb-5 flex gap-2 border-b border-slate-200 pb-3">
      <button type="button" className={mode === 'xmi' ? 'primary-button' : 'secondary-button'} onClick={() => setMode('xmi')}><FileCode2 size={16} /> XMI</button>
      <button type="button" className={mode === 'image' ? 'primary-button' : 'secondary-button'} onClick={() => setMode('image')}><ImageIcon size={16} /> Imagen</button>
    </div>
    {mode === 'xmi' ? <XmiImportPanel projectId={projectId} /> : <ImageImportPanel projectId={projectId} />}
  </section>
}
