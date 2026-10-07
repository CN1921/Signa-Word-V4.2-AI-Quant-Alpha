import { BrowserRouter, Route, Routes } from 'react-router-dom'
import AppShell from './AppShell'
import Dashboard from './pages/Dashboard'
import Market from './pages/Market'

function Placeholder({ title }: { title: string }) {
  return (
    <div style={{ padding: 24 }}>
      <h2>{title}</h2>
      <p style={{ color: '#7a8699' }}>模块页面将在下一阶段接入。</p>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<Dashboard />} />

          <Route path="/market" element={<Market />} />

          <Route
            path="/stock-analysis"
            element={<Placeholder title="Stock Analysis" />}
          />

          <Route
            path="/strategy"
            element={<Placeholder title="Strategy / AI Alpha" />}
          />

          <Route
            path="/backtest"
            element={<Placeholder title="Backtest" />}
          />

          <Route
            path="/event-engine"
            element={<Placeholder title="Event Engine" />}
          />

          <Route
            path="/concept-heat"
            element={<Placeholder title="Concept Heat" />}
          />

          <Route
            path="/watchlist"
            element={<Placeholder title="Watchlist" />}
          />

          <Route
            path="/settings"
            element={<Placeholder title="Settings" />}
          />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
