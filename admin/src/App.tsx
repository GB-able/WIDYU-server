import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuthStore } from './store/authStore'
import Layout from './components/layout/Layout'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/dashboard/DashboardPage'
import MembersPage from './pages/members/MembersPage'
import FamiliesPage from './pages/families/FamiliesPage'
import AlbumsPage from './pages/albums/AlbumsPage'
import PaymentsPage from './pages/payments/PaymentsPage'
import NotificationsPage from './pages/notifications/NotificationsPage'
import LogsPage from './pages/logs/LogsPage'
import DevToolsPage from './pages/devtools/DevToolsPage'

const queryClient = new QueryClient()
const IS_DEV = import.meta.env.MODE === 'development'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token)
  return token ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <PrivateRoute>
                <Layout />
              </PrivateRoute>
            }
          >
            {IS_DEV ? (
              // 개발 서버: 개발자 툴만
              <>
                <Route index element={<Navigate to="/devtools" replace />} />
                <Route path="devtools" element={<DevToolsPage />} />
                <Route path="*" element={<Navigate to="/devtools" replace />} />
              </>
            ) : (
              // 운영 서버: 전체 대시보드
              <>
                <Route index element={<DashboardPage />} />
                <Route path="members" element={<MembersPage />} />
                <Route path="families" element={<FamiliesPage />} />
                <Route path="albums" element={<AlbumsPage />} />
                <Route path="payments" element={<PaymentsPage />} />
                <Route path="notifications" element={<NotificationsPage />} />
                <Route path="logs" element={<LogsPage />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </>
            )}
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
