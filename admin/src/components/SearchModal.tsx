import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'

interface MemberHit {
  id: number
  name: string
  phoneNumber: string
  type: 'SENIOR' | 'GUARDIAN'
  status: 'ACTIVE' | 'INACTIVE' | 'DELETED'
}

interface PaymentHit {
  id: number
  orderId: string
  memberName: string
  amount: number
  status: string
}

interface SearchResult {
  members: MemberHit[]
  payments: PaymentHit[]
}

const TYPE_LABEL: Record<string, string> = { SENIOR: '시니어', GUARDIAN: '보호자' }
const STATUS_STYLE: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  INACTIVE: 'bg-gray-100 text-gray-500',
  DELETED: 'bg-red-100 text-red-500',
  DONE: 'bg-green-100 text-green-700',
  READY: 'bg-yellow-100 text-yellow-700',
  CANCELED: 'bg-red-100 text-red-500',
}

interface Props {
  onClose: () => void
  onSelectMember?: (memberId: number) => void
}

export default function SearchModal({ onClose, onSelectMember }: Props) {
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<SearchResult | null>(null)
  const [loading, setLoading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKey)
    return () => window.removeEventListener('keydown', handleKey)
  }, [onClose])

  const doSearch = async (q: string) => {
    if (q.trim().length < 2) {
      setResult(null)
      return
    }
    setLoading(true)
    try {
      const { data } = await client.get('/admin/search', { params: { q: q.trim() } })
      setResult(data.data)
    } catch {
      setResult(null)
    } finally {
      setLoading(false)
    }
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value
    setQuery(val)
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => doSearch(val), 300)
  }

  const handleMemberClick = (member: MemberHit) => {
    onClose()
    if (onSelectMember) {
      onSelectMember(member.id)
    } else {
      navigate(`/members?memberId=${member.id}`)
    }
  }

  const hasResults = result && (result.members.length > 0 || result.payments.length > 0)
  const isEmpty = result && result.members.length === 0 && result.payments.length === 0

  return (
    <>
      <div
        className="fixed inset-0 bg-black/40 z-[60] backdrop-blur-sm"
        onClick={onClose}
      />
      <div className="fixed top-20 left-1/2 -translate-x-1/2 w-full max-w-lg z-[70] px-4">
        <div className="bg-white rounded-xl shadow-2xl overflow-hidden">
          {/* 검색 입력 */}
          <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-100">
            <svg className="w-4 h-4 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              ref={inputRef}
              value={query}
              onChange={handleChange}
              placeholder="이름, 전화번호, ID, 가족코드(6자), 초대코드(7자), 주문ID"
              className="flex-1 text-sm outline-none placeholder-gray-400"
            />
            {loading && (
              <span className="text-xs text-gray-400 shrink-0">검색 중...</span>
            )}
            <kbd className="hidden sm:inline text-xs text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded shrink-0">ESC</kbd>
          </div>

          {/* 결과 */}
          {hasResults && (
            <div className="max-h-96 overflow-y-auto">
              {result.members.length > 0 && (
                <div>
                  <p className="px-4 pt-3 pb-1 text-xs font-semibold text-gray-400 uppercase tracking-wider">회원</p>
                  {result.members.map((m) => (
                    <button
                      key={m.id}
                      onClick={() => handleMemberClick(m)}
                      className="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-gray-50 text-left transition-colors"
                    >
                      <div className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center text-xs font-bold text-indigo-600 shrink-0">
                        {m.name.charAt(0)}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium text-gray-800">{m.name}</span>
                          <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${m.type === 'SENIOR' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'}`}>
                            {TYPE_LABEL[m.type]}
                          </span>
                          <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${STATUS_STYLE[m.status]}`}>
                            {m.status}
                          </span>
                        </div>
                        <p className="text-xs text-gray-400 truncate">{m.phoneNumber ?? '-'} · ID {m.id}</p>
                      </div>
                    </button>
                  ))}
                </div>
              )}

              {result.payments.length > 0 && (
                <div>
                  <p className="px-4 pt-3 pb-1 text-xs font-semibold text-gray-400 uppercase tracking-wider">결제</p>
                  {result.payments.map((p) => (
                    <button
                      key={p.id}
                      onClick={() => { onClose(); navigate('/payments') }}
                      className="w-full flex items-center justify-between px-4 py-2.5 hover:bg-gray-50 text-left transition-colors"
                    >
                      <div>
                        <p className="text-sm font-medium text-gray-800">{p.orderId}</p>
                        <p className="text-xs text-gray-400">{p.memberName} · {p.amount.toLocaleString()}원</p>
                      </div>
                      <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${STATUS_STYLE[p.status]}`}>
                        {p.status}
                      </span>
                    </button>
                  ))}
                </div>
              )}

              <div className="h-2" />
            </div>
          )}

          {isEmpty && !loading && query.trim().length >= 2 && (
            <div className="px-4 py-8 text-center text-sm text-gray-400">
              검색 결과가 없습니다
            </div>
          )}

          {!result && !loading && (
            <div className="px-4 py-4">
              <p className="text-xs text-gray-400">검색 팁:</p>
              <ul className="mt-1 text-xs text-gray-400 space-y-0.5">
                <li>· 이름 2자 이상, 전화번호 3자 이상</li>
                <li>· 숫자만 입력 시 회원 ID 직접 조회</li>
                <li>· 6자리: 가족코드 · 7자리: 초대코드</li>
                <li>· 주문 ID (orderId) 입력 시 결제 조회</li>
              </ul>
            </div>
          )}
        </div>
      </div>
    </>
  )
}
