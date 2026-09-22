interface PageStateProps {
  title: string
  detail?: string
}

export function PageState({ title, detail }: PageStateProps) {
  return (
    <div className="grid min-h-64 place-items-center p-8 text-center">
      <div>
        <p className="font-semibold text-slate-800">{title}</p>
        {detail && <p className="mt-1 text-sm text-slate-500">{detail}</p>}
      </div>
    </div>
  )
}
