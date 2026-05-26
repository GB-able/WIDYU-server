import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import client from '../../api/client'

interface Member {
  id: number
  name: string
  phoneNumber: string
  type: 'SENIOR' | 'GUARDIAN'
}

const PRESETS = [100, 500, 1000, 5000]

export default function PointGrantTool() {
  const [search, setSearch] = useState('')
  const [selectedMember, setSelectedMember] = useState<Member | null>(null)
  const [amount, setAmount] = useState<string>('100')
  const [result, setResult] = useState<{ newBalance: number; granted: number } | null>(null)

  const { data: members = [] } = useQuery<Member[]>({
    queryKey: ['admin-members', search],
    queryFn: async () => {
      const { data } = await client.get('/admin/members', {
        params: search ? { name: search } : {},
      })
      return data.data
    },
  })

  const seniors = members.filter((m) => m.type === 'SENIOR')

  const { mutate: grantPoints, isPending } = useMutation({
    mutationFn: async () => {
      const { data } = await client.post('/admin/dev/points', {
        memberId: selectedMember!.id,
        amount: Number(amount),
      })
      return data.data as number
    },
    onSuccess: (newBalance) => {
      setResult({ newBalance, granted: Number(amount) })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedMember || !amount || Number(amount) <= 0) return
    setResult(null)
    grantPoints()
  }

  return (
    <div className="max-w-xl">
      <h2 className="text-lg font-semibold text-gray-800 mb-1">테스트 포인트 지급</h2>
      <p className="text-sm text-gray-500 mb-5">시니어 회원에게 포인트를 즉시 지급합니다. PointHistory에 기록됩니다.</p>

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* 회원 검색 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">수신 회원 (시니어만)</label>
          <input
            type="text"
            placeholder="이름으로 검색..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setSelectedMember(null); setResult(null) }}
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          {seniors.length > 0 && !selectedMember && (
            <ul className="mt-1 border border-gray-200 rounded-md divide-y max-h-48 overflow-y-auto bg-white shadow-sm">
              {seniors.map((m) => (
                <li
                  key={m.id}
                  onClick={() => { setSelectedMember(m); setSearch(m.name) }}
                  className="px-3 py-2 text-sm cursor-pointer hover:bg-indigo-50 flex justify-between items-center"
                >
                  <span className="font-medium">{m.name}</span>
                  <span className="text-gray-400 text-xs">{m.phoneNumber} · ID {m.id}</span>
                </li>
              ))}
            </ul>
          )}
          {search && seniors.length === 0 && members.length > 0 && (
            <p className="mt-1 text-xs text-yellow-600">시니어 회원이 없습니다 (보호자는 포인트 지급 불가)</p>
          )}
          {selectedMember && (
            <p className="mt-1 text-xs text-indigo-600">선택됨: {selectedMember.name} (ID {selectedMember.id})</p>
          )}
        </div>

        {/* 금액 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">포인트</label>
          <div className="flex gap-2 mb-2">
            {PRESETS.map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setAmount(String(p))}
                className={`px-3 py-1 rounded text-xs border transition-colors ${
                  amount === String(p)
                    ? 'bg-indigo-600 text-white border-indigo-600'
                    : 'bg-white text-gray-600 border-gray-200 hover:border-gray-400'
                }`}
              >
                {p.toLocaleString()}P
              </button>
            ))}
          </div>
          <input
            type="number"
            min={1}
            max={100000}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <button
          type="submit"
          disabled={!selectedMember || !amount || Number(amount) <= 0 || isPending}
          className="w-full bg-indigo-600 text-white py-2 rounded-md text-sm font-medium hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          {isPending ? '지급 중...' : '포인트 지급'}
        </button>
      </form>

      {result && (
        <div className="mt-4 px-4 py-3 rounded-md bg-green-50 border border-green-200">
          <p className="text-sm font-medium text-green-800">
            +{result.granted.toLocaleString()}P 지급 완료
          </p>
          <p className="text-xs text-green-600 mt-0.5">
            현재 잔액: {result.newBalance.toLocaleString()}P
          </p>
        </div>
      )}
    </div>
  )
}
