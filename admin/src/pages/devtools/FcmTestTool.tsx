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
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null)

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
      })
      return data.data as string
    },
    onSuccess: (msg) => setResult({ ok: true, message: msg }),
    onError: () => setResult({ ok: false, message: '전송 실패. 서버 로그를 확인하세요.' }),
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedMember) return
    setResult(null)
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
          <label className="block text-sm font-medium text-gray-700 mb-1">제목</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            placeholder="알림 제목"
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">내용</label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            required
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

      {result && (
        <div className={`mt-4 px-4 py-3 rounded-md text-sm ${result.ok ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'}`}>
          {result.message}
        </div>
      )}
    </div>
  )
}
