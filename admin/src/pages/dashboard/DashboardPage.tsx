import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { fetchDashboard } from '../../api/dashboard'

// ─── 경고 카드 ───────────────────────────────────────────────────────────────

interface AlertCardProps {
  label: string
  value: number
  description: string
  level: 'danger' | 'warning' | 'info'
  link?: string
}

function AlertCard({ label, value, description, level, link }: AlertCardProps) {
  const navigate = useNavigate()
  const styles = {
    danger:  'bg-red-50 border-red-200 text-red-700',
    warning: 'bg-yellow-50 border-yellow-200 text-yellow-700',
    info:    'bg-blue-50 border-blue-200 text-blue-700',
  }
  const numStyles = {
    danger:  'text-red-600',
    warning: 'text-yellow-600',
    info:    'text-blue-600',
  }
  return (
    <div
      className={`rounded-xl border p-4 ${styles[level]} ${link ? 'cursor-pointer hover:opacity-80 transition-opacity' : ''}`}
      onClick={() => link && navigate(link)}
    >
      <p className="text-xs font-semibold uppercase tracking-wide opacity-70 mb-1">{label}</p>
      <p className={`text-3xl font-bold ${numStyles[level]}`}>{value.toLocaleString()}</p>
      <p className="text-xs mt-1 opacity-60">{description}</p>
    </div>
  )
}

// ─── KPI 카드 ────────────────────────────────────────────────────────────────

interface KpiCardProps {
  label: string
  value: string | number
  delta?: number
  sub?: string
}

function KpiCard({ label, value, delta, sub }: KpiCardProps) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-sm">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <div className="flex items-end gap-2">
        <p className="text-2xl font-bold text-gray-800">{typeof value === 'number' ? value.toLocaleString() : value}</p>
        {delta !== undefined && (
          <span className={`text-xs font-medium mb-0.5 ${delta > 0 ? 'text-green-500' : delta < 0 ? 'text-red-500' : 'text-gray-400'}`}>
            {delta > 0 ? `▲ ${delta}` : delta < 0 ? `▼ ${Math.abs(delta)}` : '—'}
          </span>
        )}
      </div>
      {sub && <p className="text-xs text-gray-400 mt-1">{sub}</p>}
    </div>
  )
}

// ─── 섹션 타이틀 ─────────────────────────────────────────────────────────────

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">{children}</h2>
  )
}

function toKRW(n: number) {
  return n.toLocaleString('ko-KR') + '원'
}

// ─── 메인 ────────────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
    refetchInterval: 60_000,
  })

  if (isLoading) {
    return <div className="flex items-center justify-center h-64 text-gray-400">불러오는 중...</div>
  }
  if (isError || !data) {
    return <div className="flex items-center justify-center h-64 text-red-400">데이터를 불러오지 못했습니다.</div>
  }

  const memberDelta = data.todayNewMembers - data.yesterdayNewMembers

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-800">대시보드</h1>
        <span className="text-xs text-gray-400">1분마다 자동 갱신</span>
      </div>

      {/* 경고 행 */}
      <section>
        <SectionTitle>오늘 처리 필요</SectionTitle>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <AlertCard
            label="처리중 앨범"
            value={data.processingAlbums}
            description="업로드 미완료 · 클릭 시 앨범 이동"
            level={data.processingAlbums > 0 ? 'warning' : 'info'}
            link="/albums"
          />
          <AlertCard
            label="오늘 심박 응급"
            value={data.todayHeartEmergencies}
            description={`누적 ${data.heartEmergencyCount.toLocaleString()}건`}
            level={data.todayHeartEmergencies > 0 ? 'danger' : 'info'}
          />
          <AlertCard
            label="미승인 결제"
            value={data.pendingPayments}
            description="READY 상태 잔존 건"
            level={data.pendingPayments > 0 ? 'warning' : 'info'}
            link="/payments"
          />
          <AlertCard
            label="오늘 신규 가입"
            value={data.todayNewMembers}
            description={`전일 대비 ${memberDelta >= 0 ? '+' : ''}${memberDelta}명`}
            level="info"
            link="/members"
          />
        </div>
      </section>

      {/* KPI 행 */}
      <section>
        <SectionTitle>핵심 지표</SectionTitle>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <KpiCard label="전체 회원" value={data.totalMembers} sub={`시니어 ${data.seniorCount.toLocaleString()} · 보호자 ${data.guardianCount.toLocaleString()}`} />
          <KpiCard label="오늘 신규 가입" value={data.todayNewMembers} delta={memberDelta} sub="전일 대비" />
          <KpiCard label="이번달 앨범" value={data.monthAlbums} sub={`이번주 ${data.weekAlbums.toLocaleString()}건`} />
          <KpiCard label="이번달 결제" value={toKRW(data.monthPaymentTotal)} sub={`오늘 ${toKRW(data.todayPaymentTotal)}`} />
        </div>
      </section>

      {/* 추이 차트 */}
      <section>
        <SectionTitle>주간 추이 (최근 7일)</SectionTitle>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm">
            <p className="text-sm font-medium text-gray-600 mb-4">신규 회원</p>
            <ResponsiveContainer width="100%" height={180}>
              <LineChart data={data.weeklyMemberTrend}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} width={28} />
                <Tooltip />
                <Line type="monotone" dataKey="count" stroke="#6366f1" strokeWidth={2} dot={{ r: 3 }} name="가입수" />
              </LineChart>
            </ResponsiveContainer>
          </div>

          <div className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm">
            <p className="text-sm font-medium text-gray-600 mb-4">앨범 업로드</p>
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={data.weeklyAlbumTrend}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} width={28} />
                <Tooltip />
                <Bar dataKey="count" fill="#a5b4fc" radius={[3, 3, 0, 0]} name="업로드수" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </section>

      {/* 가족 연결 */}
      <section>
        <SectionTitle>가족 연결</SectionTitle>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <KpiCard label="가족 연결 수" value={data.totalFamilyConnections} sub="보호자-가족 연결 총계" />
        </div>
      </section>
    </div>
  )
}
