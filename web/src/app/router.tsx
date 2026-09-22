import { Navigate, createBrowserRouter } from 'react-router-dom'
import { AdminDashboardPage } from '../features/admin/pages/AdminDashboardPage'
import { ProtectedRoute } from '../features/auth/components/ProtectedRoute'
import { DashboardRedirect, SuperuserRoute } from '../features/auth/components/RoleRoutes'
import { BootstrapPage } from '../features/auth/pages/BootstrapPage'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { RegisterPage } from '../features/auth/pages/RegisterPage'
import { HelpCenterPage } from '../features/help/pages/HelpCenterPage'
import { ModelEditorPage } from '../features/modeling/pages/ModelEditorPage'
import { ProjectPage } from '../features/projects/pages/ProjectPage'
import { ProjectsPage } from '../features/projects/pages/ProjectsPage'

export const router = createBrowserRouter([
  { path: '/bootstrap', element: <BootstrapPage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <ProtectedRoute />,
    children: [
      { path: '/', element: <DashboardRedirect /> },
      { path: '/admin', element: <SuperuserRoute><AdminDashboardPage /></SuperuserRoute> },
      { path: '/projects', element: <ProjectsPage /> },
      { path: '/help', element: <HelpCenterPage /> },
      { path: '/projects/:projectId', element: <ProjectPage /> },
      { path: '/projects/:projectId/models/:modelId', element: <ModelEditorPage /> },
      { path: '/projects/:projectId/models/:modelId/diagrams/:diagramId', element: <ModelEditorPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])
