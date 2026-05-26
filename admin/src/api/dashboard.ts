import client from './client'
import type { DashboardStats } from '../types/dashboard'

export async function fetchDashboard(): Promise<DashboardStats> {
  const { data } = await client.get('/admin/dashboard')
  return data.data
}
