import { create } from 'zustand'

interface AuthState {
  token: string | null
  setToken: (token: string) => void
  clearToken: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('admin_token'),
  setToken: (token) => {
    localStorage.setItem('admin_token', token)
    set({ token })
  },
  clearToken: () => {
    localStorage.removeItem('admin_token')
    set({ token: null })
  },
}))
