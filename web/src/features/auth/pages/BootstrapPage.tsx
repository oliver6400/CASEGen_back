import { useQuery } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { PageState } from '../../../components/ui/PageState'
import { authApi } from '../api/authApi'
import { AuthFrame } from './LoginPage'
import { RegistrationForm } from './RegisterPage'

export function BootstrapPage() {
  const status = useQuery({
    queryKey: ['auth', 'bootstrap'],
    queryFn: authApi.bootstrapStatus,
    retry: false,
  })

  if (status.isPending) return <PageState title="Comprobando instalación…" />
  if (status.isError) return <PageState title="No se pudo conectar con CASEGen" detail="Comprueba que el backend esté disponible." />
  if (status.data.superuserCreated) return <Navigate to="/login" replace />

  return (
    <AuthFrame title="Inicializa CASEGen" subtitle="Esta primera cuenta será el único SUPERUSER inicial de la instalación.">
      <RegistrationForm bootstrap />
    </AuthFrame>
  )
}
