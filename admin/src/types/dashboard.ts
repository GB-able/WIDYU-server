export interface DailyCount {
  date: string
  count: number
}

export interface DashboardStats {
  // 회원
  totalMembers: number
  seniorCount: number
  guardianCount: number
  todayNewMembers: number
  yesterdayNewMembers: number

  // 가족 & 앨범
  totalFamilyConnections: number
  todayAlbums: number
  weekAlbums: number
  monthAlbums: number
  processingAlbums: number

  // 결제
  todayPaymentTotal: number
  monthPaymentTotal: number
  pendingPayments: number

  // 심박 응급
  heartEmergencyCount: number
  todayHeartEmergencies: number

  // 주간 추이
  weeklyMemberTrend: DailyCount[]
  weeklyAlbumTrend: DailyCount[]
}
