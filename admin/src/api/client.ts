import axios from 'axios'

const client = axios.create({
  baseURL: '/api/v1',
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let isRefreshing = false
type QueueEntry = { resolve: (token: string) => void; reject: (err: unknown) => void }
let refreshQueue: QueueEntry[] = []

function flushQueue(token: string) {
  refreshQueue.forEach(({ resolve }) => resolve(token))
  refreshQueue = []
}

function rejectQueue(err: unknown) {
  refreshQueue.forEach(({ reject }) => reject(err))
  refreshQueue = []
}

client.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config

    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error)
    }

    const refreshToken = localStorage.getItem('admin_refresh_token')
    if (!refreshToken) {
      localStorage.removeItem('admin_token')
      window.location.href = '/login'
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        refreshQueue.push({
          resolve: (newToken) => {
            original.headers.Authorization = `Bearer ${newToken}`
            resolve(client(original))
          },
          reject,
        })
      })
    }

    original._retry = true
    isRefreshing = true

    try {
      const { data } = await axios.post('/api/v1/auth/reissue', { refreshToken })
      const newAccessToken: string = data.data.accessToken
      const newRefreshToken: string = data.data.refreshToken

      localStorage.setItem('admin_token', newAccessToken)
      localStorage.setItem('admin_refresh_token', newRefreshToken)

      client.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`
      flushQueue(newAccessToken)

      original.headers.Authorization = `Bearer ${newAccessToken}`
      return client(original)
    } catch (refreshError) {
      rejectQueue(refreshError)
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_refresh_token')
      window.location.href = '/login'
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  }
)

export default client
