import { NavLink } from 'react-router-dom'

const mainNav = [{ path: '/', label: '대시보드' }]

const serviceNav = [
  { path: '/members', label: '회원' },
  { path: '/families', label: '가족' },
  { path: '/albums', label: '콘텐츠' },
  { path: '/payments', label: '결제' },
  { path: '/notifications', label: 'FCM 상태' },
  { path: '/logs', label: '운영 로그' },
]

const devNav = [{ path: '/devtools', label: '개발자 툴' }]

function NavItem({ path, label }: { path: string; label: string }) {
  return (
    <NavLink
      to={path}
      end={path === '/'}
      className={({ isActive }) =>
        `block px-3 py-2 rounded-md text-sm font-medium transition-colors ${
          isActive
            ? 'bg-indigo-600 text-white'
            : 'text-gray-400 hover:bg-gray-700 hover:text-white'
        }`
      }
    >
      {label}
    </NavLink>
  )
}

function NavSection({ label, items }: { label?: string; items: { path: string; label: string }[] }) {
  return (
    <div className="px-3 py-2">
      {label && (
        <p className="px-2 mb-1 text-xs font-semibold text-gray-500 uppercase tracking-wider">
          {label}
        </p>
      )}
      {items.map((item) => (
        <NavItem key={item.path} {...item} />
      ))}
    </div>
  )
}

const IS_DEV = import.meta.env.MODE === 'development'

export default function Sidebar() {
  return (
    <aside className="w-56 fixed top-12 left-0 bottom-0 bg-gray-900 flex flex-col overflow-y-auto">
      {IS_DEV ? (
        // 개발 서버: 개발자 툴만 표시
        <nav className="flex-1 py-3">
          <NavSection label="개발" items={devNav} />
        </nav>
      ) : (
        // 운영 서버: 전체 메뉴
        <>
          <nav className="flex-1 py-3 space-y-1">
            <NavSection items={mainNav} />
            <div className="mx-4 border-t border-gray-700" />
            <NavSection label="서비스 관리" items={serviceNav} />
          </nav>
        </>
      )}
    </aside>
  )
}
