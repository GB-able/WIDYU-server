import { create } from 'zustand'

interface AuthState {
  token: string | null
  refreshToken: string | null
  setTokens: (accessToken: string, refreshToken: string) => void
  setToken: (token: string) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('admin_token'),
  refreshToken: localStorage.getItem('admin_refresh_token'),
  setTokens: (accessToken, refreshToken) => {
    localStorage.setItem('admin_token', accessToken)
    localStorage.setItem('admin_refresh_token', refreshToken)
    set({ token: accessToken, refreshToken })
  },
  setToken: (token) => {
    localStorage.setItem('admin_token', token)
    set({ token })
  },
  logout: () => {
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_refresh_token')
    set({ token: null, refreshToken: null })
  },
}))
