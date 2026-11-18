import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import client from '../../api/client'

const FCM_CATEGORIES = [
  { value: 'ETC', label: '기타 (ETC)' },
  { value: 'ALBUM', label: '앨범 (ALBUM)' },
  { value: 'TARGET', label: '목표 (TARGET)' },
  { value: 'HEALTH_SCHEDULE', label: '건강검진 (HEALTH_SCHEDULE)' },
  { value: 'WALK', label: '걷기 (WALK)' },
  { value: 'MEDICINE_SCHEDULE', label: '복약 (MEDICINE_SCHEDULE)' },
  { value: 'HEART_MESSAGE', label: '심박수 (HEART_MESSAGE)' },
  { value: 'SAFE_ZONE', label: '안전구역 (SAFE_ZONE)' },
]

interface FcmTestAttempt {
  tokenId: number
  outcome: 'SENT' | 'FAILED' | 'NOT_ATTEMPTED'
  detail: string | null
  payload: unknown | null
}

interface FcmTestResult {
  memberId: number
  notificationType: string | null
  deliveryMode: string | null
  tokenCount: number
  sent: number
  failed: number
  notAttempted: number
  notes: string[]
  attempts: FcmTestAttempt[]
}

const OUTCOME_LABEL: Record<FcmTestAttempt['outcome'], string> = {
  SENT: 'FCM 수락',
  FAILED: '실패',
  NOT_ATTEMPTED: '미시도',
}

interface Member {
  id: number
  name: string
  phoneNumber: string
  type: 'SENIOR' | 'GUARDIAN'
}

export default function FcmTestTool() {
  const [search, setSearch] = useState('')
  const [selectedMember, setSelectedMember] = useState<Member | null>(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [category, setCategory] = useState('ETC')
  const [notificationType, setNotificationType] = useState('')
  const [result, setResult] = useState<FcmTestResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const { data: members = [] } = useQuery<Member[]>({
    queryKey: ['admin-members', search],
    queryFn: async () => {
      const { data } = await client.get('/admin/members', {
        params: search ? { name: search } : {},
      })
      return data.data
    },
  })

  const { mutate: sendFcm, isPending } = useMutation({
    mutationFn: async () => {
      const { data } = await client.post('/admin/fcm/test', {
        memberId: selectedMember!.id,
        title,
        content,
        category,
        notificationType: notificationType.trim() || null,
      })
      return data.data as FcmTestResult
    },
    onSuccess: (body) => setResult(body),
    onError: () => setError('요청 실패. 입력값이나 서버 로그를 확인하세요.'),
  })

  const typed = notificationType.trim() !== ''
  const sentPayload = result?.attempts.find((a) => a.payload !== null)?.payload

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedMember) return
    setResult(null)
    setError(null)
    sendFcm()
  }

  return (
    <div className="max-w-xl">
      <h2 className="text-lg font-semibold text-gray-800 mb-1">FCM 테스트 발송</h2>
      <p className="text-sm text-gray-500 mb-5">특정 회원에게 테스트 푸시 알림을 전송합니다.</p>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">수신 회원</label>
          <input
            type="text"
            placeholder="이름으로 검색..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setSelectedMember(null) }}
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          {members.length > 0 && !selectedMember && (
            <ul className="mt-1 border border-gray-200 rounded-md divide-y max-h-48 overflow-y-auto bg-white shadow-sm">
              {members.map((m) => (
                <li
                  key={m.id}
                  onClick={() => { setSelectedMember(m); setSearch(m.name) }}
                  className="px-3 py-2 text-sm cursor-pointer hover:bg-indigo-50 flex justify-between items-center"
                >
                  <span className="font-medium">{m.name}</span>
                  <span className="text-gray-400 text-xs">
                    {m.type === 'SENIOR' ? '시니어' : '보호자'} · {m.phoneNumber} · ID {m.id}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {selectedMember && (
            <p className="mt-1 text-xs text-indigo-600">
              선택됨: {selectedMember.name} (ID {selectedMember.id})
            </p>
          )}
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">카테고리</label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            {FCM_CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>{c.label}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">알림 종류 (선택)</label>
          <input
            type="text"
            value={notificationType}
            onChange={(e) => setNotificationType(e.target.value)}
            placeholder="예: WALK_DATA_SYNC, HEART_RATE_EMERGENCY"
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <p className="mt-1 text-xs text-gray-500">
            종류를 주면 그 종류의 실제 전송 설정으로 보냅니다. 제목과 내용을 비우면 문구표 문구를 씁니다.
          </p>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">제목</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required={!typed}
            placeholder="알림 제목"
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">내용</label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            required={!typed}
            rows={3}
            placeholder="알림 내용"
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
          />
        </div>

        <button
          type="submit"
          disabled={!selectedMember || isPending}
          className="w-full bg-indigo-600 text-white py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          {isPending ? '전송 중...' : '발송'}
        </button>
      </form>

      {error && (
        <div className="mt-4 px-4 py-3 rounded-md text-sm bg-red-50 text-red-800">{error}</div>
      )}

      {result && (
        <div className="mt-4 space-y-3 text-sm">
          <div className={`px-4 py-3 rounded-md ${result.sent > 0 ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'}`}>
            {result.tokenCount === 0
              ? '활성 토큰이 없어 보내지 않았습니다.'
              : `토큰 ${result.tokenCount}개 중 FCM 수락 ${result.sent}, 실패 ${result.failed}, 미시도 ${result.notAttempted}`}
            {result.notificationType && (
              <span className="block text-xs mt-1">
                {result.notificationType} · {result.deliveryMode}
              </span>
            )}
          </div>

          {result.attempts.length > 0 && (
            <ul className="border border-gray-200 rounded-md divide-y">
              {result.attempts.map((a) => (
                <li key={a.tokenId} className="px-3 py-2 flex justify-between">
                  <span>토큰 ID {a.tokenId}</span>
                  <span className={a.outcome === 'SENT' ? 'text-green-700' : 'text-red-700'}>
                    {OUTCOME_LABEL[a.outcome]}{a.detail ? ` (${a.detail})` : ''}
                  </span>
                </li>
              ))}
            </ul>
          )}

          <ul className="list-disc pl-5 text-xs text-gray-600 space-y-1">
            {result.notes.map((note) => <li key={note}>{note}</li>)}
          </ul>

          {sentPayload !== undefined && (
            <div>
              <p className="text-xs font-medium text-gray-700 mb-1">FCM에 보낸 본문 (기기 토큰 제외)</p>
              <pre className="bg-gray-50 border border-gray-200 rounded-md p-3 text-xs overflow-x-auto">
                {JSON.stringify(sentPayload, null, 2)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
