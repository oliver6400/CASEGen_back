import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { getApiErrorMessage } from '../../../api/apiError'
import { FormField } from '../../../components/ui/FormField'
import { authApi } from '../api/authApi'
import { useAuthStore } from '../authStore'
import { AuthFrame } from './LoginPage'

const registerSchema = z.object({
  displayName: z.string().trim().min(1, 'El nombre es obligatorio.').max(120),
  email: z.email('Ingresa un email válido.').max(254),
  password: z.string().min(8, 'Usa al menos 8 caracteres.').max(72),
})
type RegisterValues = z.infer<typeof registerSchema>

export function RegistrationForm({ bootstrap = false }: { bootstrap?: boolean }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const form = useForm<RegisterValues>({ resolver: zodResolver(registerSchema) })
  const mutation = useMutation({
    mutationFn: authApi.register,
    onSuccess: async () => {
      const user = await authApi.me()
      queryClient.setQueryData(['auth', 'me'], user)
      navigate(user.systemRole === 'SUPERUSER' ? '/admin' : '/projects', { replace: true })
    },
  })

  return (
    <form className="grid gap-4" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
      <FormField id="displayName" label="Nombre" autoComplete="name" error={form.formState.errors.displayName?.message} {...form.register('displayName')} />
      <FormField id="email" label="Email" type="email" autoComplete="email" error={form.formState.errors.email?.message} {...form.register('email')} />
      <FormField id="password" label="Contraseña" type="password" autoComplete="new-password" error={form.formState.errors.password?.message} {...form.register('password')} />
      {mutation.isError && <p className="form-error">{getApiErrorMessage(mutation.error)}</p>}
      <button className="primary-button mt-1" disabled={mutation.isPending} type="submit">
        {mutation.isPending ? 'Creando cuenta…' : bootstrap ? 'Crear SUPERUSER' : 'Crear cuenta'}
      </button>
    </form>
  )
}

export function RegisterPage() {
  const hasSession = useAuthStore((state) => Boolean(state.accessToken))
  const { data } = useQuery({ queryKey: ['auth', 'bootstrap'], queryFn: authApi.bootstrapStatus })
  if (hasSession) return <Navigate to="/" replace />
  if (data && !data.superuserCreated) return <Navigate to="/bootstrap" replace />

  return (
    <AuthFrame title="Crea tu cuenta" subtitle="Tu cuenta utilizará los mismos permisos de CASEGen en web y móvil.">
      <RegistrationForm />
      <p className="mt-6 text-center text-sm text-slate-500">
        ¿Ya tienes una cuenta? <Link className="font-semibold text-indigo-600" to="/login">Inicia sesión</Link>
      </p>
    </AuthFrame>
  )
}
