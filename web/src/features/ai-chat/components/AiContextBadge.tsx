export function AiContextBadge({ modelName, diagramName }: { modelName: string; diagramName?: string }) {
  return <div className="flex gap-2 overflow-hidden rounded-lg border border-indigo-100 bg-indigo-50 px-2.5 py-1.5 text-[11px] text-indigo-900">
    <span className="shrink-0 font-semibold">Contexto:</span>
    <span className="truncate">{modelName}{diagramName ? ` / ${diagramName}` : ''}</span>
  </div>
}
