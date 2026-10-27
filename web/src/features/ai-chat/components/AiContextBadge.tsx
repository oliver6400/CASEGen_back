export function AiContextBadge({ modelName, diagramName }: { modelName: string; diagramName?: string }) {
  return <div className="rounded-lg border border-indigo-100 bg-indigo-50 px-3 py-2 text-xs text-indigo-900">
    <div className="font-semibold">Contexto actual</div>
    <div className="mt-1 truncate">Modelo: {modelName}</div>
    <div className="truncate">Diagrama: {diagramName ?? 'Sin diagrama seleccionado'}</div>
  </div>
}
