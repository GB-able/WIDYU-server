import { useEffect, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import client from '../../api/client'

interface LogEntry {
  time: string
  level: 'ERROR' | 'WARN' | 'INFO' | 'DEBUG'
  logger: string
  message: string
  exception: string | null
}

const LEVEL_STYLE: Record<string, string> = {
  ERROR: 'text-red-400',
  WARN:  'text-yellow-400',
  INFO:  'text-green-400',
  DEBUG: 'text-gray-400',
}


const LEVELS = ['ALL', 'ERROR', 'WARN', 'INFO'] as const
type LevelFilter = typeof LEVELS[number]

export default function LogViewerTool() {
  const [levelFilter, setLevelFilter] = useState<LevelFilter>('ALL')
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const queryClient = useQueryClient()
  const bottomRef = useRef<HTMLDivElement>(null)

  const { data: logs = [], dataUpdatedAt } = useQuery<LogEntry[]>({
    queryKey: ['dev-logs', levelFilter],
    queryFn: async () => {
      const params: Record<string, string | number> = { limit: 200 }
      if (levelFilter !== 'ALL') params.level = levelFilter
      const { data } = await client.get('/admin/dev/logs', { params })
      return data.data
    },
    refetchInterval: autoRefresh ? 3000 : false,
  })

  const { mutate: clearLogs } = useMutation({
    mutationFn: () => client.delete('/admin/dev/logs'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dev-logs'] }),
  })

  useEffect(() => {
    if (autoRefresh) bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [dataUpdatedAt, autoRefresh])

  const updatedAt = new Date(dataUpdatedAt).toLocaleTimeString('ko-KR')

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-lg font-semibold text-gray-800">서버 로그</h2>
          <p className="text-xs text-gray-400 mt-0.5">최근 200건 · 마지막 업데이트 {updatedAt}</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => queryClient.invalidateQueries({ queryKey: ['dev-logs'] })}
            className="px-3 py-1.5 text-xs border border-gray-200 rounded-lg hover:bg-gray-50 text-gray-600"
          >
            새로고침
          </button>
          <button
            onClick={() => setAutoRefresh((v) => !v)}
            className={`px-3 py-1.5 text-xs rounded-lg border transition-colors ${
              autoRefresh
                ? 'bg-green-600 text-white border-green-600'
                : 'border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}
          >
            {autoRefresh ? '● 자동갱신 ON' : '자동갱신 OFF'}
          </button>
          <button
            onClick={() => { if (window.confirm('로그를 초기화할까요?')) clearLogs() }}
            className="px-3 py-1.5 text-xs border border-red-200 text-red-500 rounded-lg hover:bg-red-50"
          >
            초기화
          </button>
        </div>
      </div>

      {/* 레벨 필터 */}
      <div className="flex gap-1.5 mb-3">
        {LEVELS.map((l) => (
          <button
            key={l}
            onClick={() => setLevelFilter(l)}
            className={`px-2.5 py-1 rounded text-xs font-medium border transition-colors ${
              levelFilter === l
                ? 'bg-gray-800 text-white border-gray-700'
                : 'bg-white text-gray-500 border-gray-200 hover:border-gray-300'
            }`}
          >
            {l}
          </button>
        ))}
        <span className="ml-auto text-xs text-gray-400 self-center">{logs.length}건</span>
      </div>

      {/* 로그 뷰어 */}
      <div className="flex-1 bg-gray-950 rounded-xl overflow-y-auto font-mono text-xs min-h-0 max-h-[600px]">
        {logs.length === 0 ? (
          <div className="flex items-center justify-center h-40 text-gray-600">
            로그가 없습니다
          </div>
        ) : (
          <div className="p-3 space-y-0.5">
            {logs.map((log, i) => (
              <div key={i}>
                <div
                  className="flex items-start gap-2 py-0.5 px-2 rounded hover:bg-white/5 cursor-pointer"
                  onClick={() => setExpandedId(expandedId === i ? null : i)}
                >
                  <span className="text-gray-600 shrink-0 w-16 text-right">
                    {new Date(log.time).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                  </span>
                  <span className={`shrink-0 w-12 text-center font-bold ${LEVEL_STYLE[log.level]}`}>
                    {log.level}
                  </span>
                  <span className="text-gray-500 shrink-0 w-36 truncate">{log.logger}</span>
                  <span className={`flex-1 ${log.level === 'ERROR' ? 'text-red-300' : log.level === 'WARN' ? 'text-yellow-200' : 'text-gray-300'}`}>
                    {log.message}
                  </span>
                  {log.exception && (
                    <span className="shrink-0 text-red-500">{expandedId === i ? '▲' : '▼'}</span>
                  )}
                </div>
                {expandedId === i && log.exception && (
                  <div className="mx-2 mb-1 px-3 py-2 bg-red-950/50 rounded text-red-300 whitespace-pre-wrap break-all">
                    {log.exception}
                  </div>
                )}
              </div>
            ))}
            <div ref={bottomRef} />
          </div>
        )}
      </div>
    </div>
  )
}
