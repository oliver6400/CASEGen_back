import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { PageState } from '../../../components/ui/PageState'
import { useCurrentUser } from '../hooks/useCurrentUser'

export function DashboardRedirect() {
  const user = useCurrentUser()
  if (user.isPending) return <PageState title="Abriendo CASEGen…" />
  if (user.data?.systemRole === 'SUPERUSER') return <Navigate to="/admin" replace />
  return <Navigate to="/projects" replace />
}

export function SuperuserRoute({ children }: { children: ReactNode }) {
  const user = useCurrentUser()
  if (user.isPending) return <PageState title="Verificando permisos…" />
  if (user.data?.systemRole !== 'SUPERUSER') return <Navigate to="/projects" replace />
  return children
}
