import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  DashboardOutlined,
  LineChartOutlined,
  FundOutlined,
  RobotOutlined,
  ExperimentOutlined,
  ThunderboltOutlined,
  AppstoreOutlined,
  StarOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import './AppShell.css'

const menuItems = [
  { path: '/', label: 'Dashboard', icon: <DashboardOutlined /> },
  { path: '/market', label: 'Market', icon: <LineChartOutlined /> },
  { path: '/stock-analysis', label: 'Stock Analysis', icon: <FundOutlined /> },
  { path: '/strategy', label: 'Strategy / AI Alpha', icon: <RobotOutlined /> },
  { path: '/backtest', label: 'Backtest', icon: <ExperimentOutlined /> },
  { path: '/event-engine', label: 'Event Engine', icon: <ThunderboltOutlined /> },
  { path: '/concept-heat', label: 'Concept Heat', icon: <AppstoreOutlined /> },
  { path: '/watchlist', label: 'Watchlist', icon: <StarOutlined /> },
]

export default function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()

  const isSettings = location.pathname === '/settings'

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="brand">
          <div className="brand-mark">SW</div>
          <div>
            <div className="brand-name">Signa-Word</div>
            <div className="brand-subtitle">AI Quant Alpha</div>
          </div>
        </div>

        <nav className="main-nav">
          {menuItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === '/'}
              className={({ isActive }) =>
                `nav-item ${isActive ? 'active' : ''}`
              }
            >
              <span className="nav-icon">{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <button
            className={`settings-nav ${isSettings ? 'active' : ''}`}
            onClick={() => navigate('/settings')}
          >
            <SettingOutlined />
            <span>Settings</span>
          </button>

          <div className="engine-status">
            <div className="engine-status-dot" />
            <div>
              <div className="engine-version">V4.2 Alpha</div>
              <div className="engine-state">Realtime Online</div>
            </div>
          </div>
        </div>
      </aside>

      <div className="app-main">
        <header className="app-header">
          <div>
            <div className="header-title">Signa-Word AI Quant Alpha Terminal</div>
            <div className="header-subtitle">
              Market Intelligence · Alpha Research · Quant Engine
            </div>
          </div>

          <div className="header-actions">
            <div className="connection-status">
              <span className="connection-dot" />
              行情已连接
            </div>

            <button
              className="header-settings"
              onClick={() => navigate('/settings')}
            >
              <SettingOutlined />
              Settings
            </button>
          </div>
        </header>

        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
