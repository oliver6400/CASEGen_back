import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '../authStore'

export function ProtectedRoute() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const location = useLocation()

  if (!accessToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
