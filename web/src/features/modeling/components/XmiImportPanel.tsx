import { useMutation, useQueryClient } from '@tanstack/react-query'
import { FileUp } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getApiErrorMessage } from '../../../api/apiError'
import { modelApi } from '../api/modelApi'

export function XmiImportPanel({ projectId }: { projectId: string }) {
  const input = useRef<HTMLInputElement>(null)
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const mutation = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('Selecciona un archivo XMI.')
      return modelApi.importXmi(projectId, file)
    },
    onSuccess: async () => {
      setFile(null)
      if (input.current) input.current.value = ''
      await queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'models'] })
    },
  })

  return (
    <div>
      <div className="mb-4 flex items-center gap-2 font-semibold"><FileUp size={18} className="text-indigo-600" /> Archivo UML/XMI</div>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input ref={input} className="block flex-1 text-sm text-slate-600" type="file" accept=".xmi,.xml,application/xml,text/xml"
          onChange={(event) => { setFile(event.target.files?.[0] ?? null); mutation.reset() }} />
        <button className="primary-button" type="button" disabled={!file || mutation.isPending}
          onClick={() => mutation.mutate()}>{mutation.isPending ? 'Importando…' : 'Importar XMI'}</button>
      </div>
      {mutation.isError && <p className="form-error mt-3">{getApiErrorMessage(mutation.error)}</p>}
      {mutation.data && (
        <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-slate-700">
          <h3 className="font-semibold text-emerald-800">Importación completada</h3>
          <p className="mt-2"><strong>Modelo:</strong> {mutation.data.modelName}</p>
          <p className="mt-1"><strong>Dialect:</strong>{' '}
            {mutation.data.dialect === 'EA_XMI_1_1_UML_1_3'
              ? 'Enterprise Architect XMI 1.1'
              : 'CASEGen UML/XMI 2.5.1'}
          </p>
          {mutation.data.dialect === 'EA_XMI_1_1_UML_1_3' && (
            <p className="mt-1 text-emerald-800">Modelo importado desde Enterprise Architect.</p>
          )}
          <p className="mt-2 font-medium">Elementos importados</p>
          <p className="mt-1">
            {mutation.data.classCount} clases · {mutation.data.interfaceCount} interfaces · {mutation.data.propertyCount} atributos ·{' '}
            {mutation.data.operationCount} operaciones · {mutation.data.associationCount} asociaciones ·{' '}
            {mutation.data.generalizationCount} generalizaciones · {mutation.data.realizationCount} realizaciones
          </p>
          <p className="mt-1">Validación UML: <strong>{mutation.data.validation.status}</strong>
            {mutation.data.validation.issueCount > 0 && ` · ${mutation.data.validation.issueCount} issues`}</p>
          <p className="mt-1">{mutation.data.warnings.length} advertencias</p>
          {mutation.data.warnings.length > 0 && <ul className="mt-2 list-disc pl-5">
            {mutation.data.warnings.map((warning, index) => <li key={`${warning.externalId ?? 'warning'}-${index}`}>
              {warning.code}: {warning.message}
            </li>)}
          </ul>}
          {mutation.data.diagram ? (
            <div className="mt-4 border-t border-emerald-200 pt-3">
              <p><strong>Diagrama creado:</strong> {mutation.data.diagram.name}</p>
              <p className="mt-1 text-xs text-slate-600">
                {mutation.data.diagram.nodeCount} elementos · {mutation.data.diagram.edgeCount} relaciones
              </p>
              <Link className="primary-button mt-3 inline-flex"
                to={`/projects/${projectId}/models/${mutation.data.modelId}/diagrams/${mutation.data.diagram.diagramId}`}>
                Abrir diagrama
              </Link>
            </div>
          ) : (
            <p className="mt-4 border-t border-emerald-200 pt-3">
              No se creó un diagrama porque no existen classifiers visualizables.
            </p>
          )}
        </div>
      )}
    </div>
  )
}
