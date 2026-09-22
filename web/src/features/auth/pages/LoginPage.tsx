import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Boxes } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { getApiErrorMessage } from '../../../api/apiError'
import { FormField } from '../../../components/ui/FormField'
import { authApi } from '../api/authApi'
import { useAuthStore } from '../authStore'

const schema = z.object({
  email: z.email('Ingresa un email válido.'),
  password: z.string().min(1, 'La contraseña es obligatoria.'),
})
type LoginValues = z.infer<typeof schema>

export function LoginPage() {
  const hasSession = useAuthStore((state) => Boolean(state.accessToken))
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const bootstrap = useQuery({ queryKey: ['auth', 'bootstrap'], queryFn: authApi.bootstrapStatus, retry: false })
  const form = useForm<LoginValues>({ resolver: zodResolver(schema) })
  const mutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: async () => {
      const user = await authApi.me()
      queryClient.setQueryData(['auth', 'me'], user)
      const requestedPath = (location.state as { from?: string } | null)?.from
      const dashboard = user.systemRole === 'SUPERUSER' ? '/admin' : '/projects'
      navigate(requestedPath ?? dashboard, { replace: true })
    },
  })

  if (hasSession) return <Navigate to="/" replace />
  if (bootstrap.data && !bootstrap.data.superuserCreated) return <Navigate to="/bootstrap" replace />

  return (
    <AuthFrame title="Bienvenido de nuevo" subtitle="Accede a tus proyectos y modelos UML.">
      <form className="grid gap-4" onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
        <FormField id="email" label="Email" type="email" autoComplete="email" error={form.formState.errors.email?.message} {...form.register('email')} />
        <FormField id="password" label="Contraseña" type="password" autoComplete="current-password" error={form.formState.errors.password?.message} {...form.register('password')} />
        {mutation.isError && <p className="form-error">{getApiErrorMessage(mutation.error)}</p>}
        <button className="primary-button mt-1" disabled={mutation.isPending} type="submit">
          {mutation.isPending ? 'Ingresando…' : 'Iniciar sesión'}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        ¿No tienes una cuenta? <Link className="font-semibold text-indigo-600" to="/register">Regístrate</Link>
      </p>
    </AuthFrame>
  )
}

export function AuthFrame({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <main className="grid min-h-screen place-items-center bg-[radial-gradient(circle_at_top_left,_#e0e7ff,_transparent_34%),linear-gradient(#f8fafc,#eef2ff)] p-5">
      <section className="w-full max-w-md rounded-3xl border border-white bg-white/90 p-8 shadow-2xl shadow-indigo-950/10 backdrop-blur">
        <div className="mb-7">
          <span className="mb-5 grid size-11 place-items-center rounded-2xl bg-indigo-600 text-white"><Boxes size={22} /></span>
          <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">{subtitle}</p>
        </div>
        {children}
      </section>
    </main>
  )
}
