import { Boxes, CircleHelp, FolderKanban, LogOut, Palette, ShieldCheck, UserRound } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../../features/auth/api/authApi'
import { useCurrentUser } from '../../features/auth/hooks/useCurrentUser'
import { HelpTour } from '../../features/help/components/HelpTour'
import { useAppearanceStore } from '../../features/appearance/appearanceStore'

export function AppShell({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const { data: user } = useCurrentUser()
  const openAppearance = useAppearanceStore((state) => state.openPanel)

  async function logout() {
    await authApi.logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-themed min-h-screen bg-slate-50 text-slate-950">
      <header className="app-header sticky top-0 z-30 border-b border-slate-200/80 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-[1600px] items-center justify-between px-5">
          <Link className="flex items-center gap-2 font-bold tracking-tight" to="/projects">
            <span className="grid size-9 place-items-center rounded-xl bg-indigo-600 text-white">
              <Boxes size={19} />
            </span>
            CASEGen
          </Link>
          <div className="flex items-center gap-2 text-sm">
            <Link className="nav-link" to="/projects"><FolderKanban size={16} /> Proyectos</Link>
            {user?.systemRole === 'SUPERUSER' && <Link className="nav-link" to="/admin"><ShieldCheck size={16} /> Administración</Link>}
            <Link className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-2 font-medium text-slate-600 transition hover:bg-slate-100 hover:text-indigo-700" to="/help" title="Centro de ayuda"><CircleHelp size={17} /><span className="hidden sm:inline">Ayuda</span></Link>
            <button className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-2 font-medium text-slate-600 transition hover:bg-slate-100 hover:text-indigo-700" onClick={openAppearance} title="Personalizar apariencia" type="button"><Palette size={17} /><span className="hidden lg:inline">Apariencia</span></button>
            <div className="hidden items-center gap-2 text-slate-600 sm:flex">
              <UserRound size={16} />
              <span>{user?.displayName ?? 'Usuario'}</span>
              {user?.systemRole === 'SUPERUSER' && (
                <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-semibold text-indigo-700">SUPERUSER</span>
              )}
            </div>
            <button className="icon-button" onClick={logout} title="Cerrar sesión" type="button">
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </header>
      {children}
      <HelpTour />
    </div>
  )
}
