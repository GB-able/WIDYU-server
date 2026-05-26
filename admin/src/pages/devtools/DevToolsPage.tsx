import { useState } from 'react'
import FcmTestTool from './FcmTestTool'
import LogViewerTool from './LogViewerTool'
import PointGrantTool from './PointGrantTool'

const TOOLS = [
  { id: 'fcm', label: 'FCM 테스트 발송' },
  { id: 'points', label: '포인트 지급' },
  { id: 'logs', label: '서버 로그' },
]

export default function DevToolsPage() {
  const [activeTool, setActiveTool] = useState('fcm')

  return (
    <div className="flex gap-6 h-full">
      {/* 툴 목록 */}
      <aside className="w-48 shrink-0">
        <h2 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">개발자 툴</h2>
        <nav className="space-y-1">
          {TOOLS.map((tool) => (
            <button
              key={tool.id}
              onClick={() => setActiveTool(tool.id)}
              className={`w-full text-left px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                activeTool === tool.id
                  ? 'bg-indigo-50 text-indigo-700'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              {tool.label}
            </button>
          ))}
        </nav>
      </aside>

      {/* 툴 컨텐츠 */}
      <div className="flex-1 min-w-0">
        {activeTool === 'fcm' && <FcmTestTool />}
        {activeTool === 'points' && <PointGrantTool />}
        {activeTool === 'logs' && <LogViewerTool />}
      </div>
    </div>
  )
}
