import { useEffect, useState } from 'react'
import {
  DatabaseOutlined,
  ApiOutlined,
  CloudServerOutlined,
  CheckCircleOutlined,
  ReloadOutlined,
  StockOutlined,
} from '@ant-design/icons'
import { getRealtimeStatus } from '../api'
import './Market.css'

interface RealtimeStatus {
  status?: string
  realtime_loaded?: boolean
  realtime_count?: number
  realtime_source?: string
  target_universe?: string
  server_time?: string
  akshare_available?: boolean
  concept_count?: number
  concept_provider?: string
  concept_loaded?: boolean
  concepts_loaded?: boolean
  concepts_building?: boolean
  concept_progress?: {
    done?: number
    total?: number
  }
}

export default function Market() {
  const [data, setData] = useState<RealtimeStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadStatus = async () => {
    setLoading(true)
    setError('')

    try {
      const response = await getRealtimeStatus()
      setData(response.data)
    } catch (err) {
      console.error(err)
      setError('无法连接 Python Bridge')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadStatus()

    const timer = window.setInterval(loadStatus, 30000)

    return () => window.clearInterval(timer)
  }, [])

  const realtimeOnline =
    data?.status === 'ok' && data?.realtime_loaded === true

  const conceptBuilding =
    data?.concepts_building === true ||
    data?.concept_loaded === false ||
    data?.concepts_loaded === false

  return (
    <div className="market-page">
      <div className="market-page-header">
        <div>
          <div className="market-eyebrow">MARKET DATA</div>
          <h1>Market</h1>
          <p>
            Real-time A-share market data and data-source diagnostics.
          </p>
        </div>

        <button
          className="market-refresh"
          onClick={loadStatus}
          disabled={loading}
        >
          <ReloadOutlined spin={loading} />
          Refresh
        </button>
      </div>

      {error && (
        <div className="market-error">
          <ApiOutlined />
          <span>{error}</span>
        </div>
      )}

      <section className="market-grid market-grid-main">
        <div className="market-card primary">
          <div className="market-card-label">
            <StockOutlined />
            Realtime Market
          </div>

          <div className="market-card-value">
            {data?.realtime_count?.toLocaleString() ?? '--'}
          </div>

          <div className="market-card-description">
            instruments loaded
          </div>

          <div className="market-status-row">
            <span
              className={`status-dot ${
                realtimeOnline ? 'online' : 'offline'
              }`}
            />
            <span>
              {realtimeOnline ? 'Realtime Online' : 'Realtime Offline'}
            </span>
          </div>
        </div>

        <div className="market-card">
          <div className="market-card-label">
            <DatabaseOutlined />
            Data Source
          </div>

          <div className="market-card-value text-value">
            {data?.realtime_source || '--'}
          </div>

          <div className="market-card-description">
            realtime provider
          </div>

          <div className="market-card-meta">
            <span>AKShare</span>
            <span
              className={
                data?.akshare_available ? 'meta-ok' : 'meta-warning'
              }
            >
              {data?.akshare_available ? 'Available' : 'Unavailable'}
            </span>
          </div>
        </div>

        <div className="market-card">
          <div className="market-card-label">
            <CloudServerOutlined />
            Target Universe
          </div>

          <div className="market-card-value text-value">
            {data?.target_universe || '--'}
          </div>

          <div className="market-card-description">
            configured market universe
          </div>

          <div className="market-card-meta">
            <span>Board coverage</span>
            <span>3 segments</span>
          </div>
        </div>
      </section>

      <section className="market-section">
        <div className="market-section-title">
          <div>
            <h2>Market Status</h2>
            <p>Current runtime state reported by the Python bridge.</p>
          </div>
        </div>

        <div className="status-grid">
          <StatusItem
            title="Python Bridge"
            value={data?.status === 'ok' ? 'Normal' : 'Unavailable'}
            ok={data?.status === 'ok'}
          />

          <StatusItem
            title="Realtime Market"
            value={realtimeOnline ? 'Loaded' : 'Not Loaded'}
            ok={realtimeOnline}
          />

          <StatusItem
            title="AKShare"
            value={data?.akshare_available ? 'Available' : 'Unavailable'}
            ok={data?.akshare_available}
          />

          <StatusItem
            title="Concept Engine"
            value={
              conceptBuilding
                ? 'Developing'
                : `${data?.concept_count ?? 0} concepts`
            }
            ok={!conceptBuilding}
            developing={conceptBuilding}
          />
        </div>
      </section>

      <section className="market-section">
        <div className="market-section-title">
          <div>
            <h2>Universe</h2>
            <p>
              Current target universe configured by the quant data pipeline.
            </p>
          </div>
        </div>

        <div className="universe-grid">
          <UniverseItem
            name="MAIN"
            description="Shanghai / Shenzhen Main Board"
          />

          <UniverseItem
            name="CHINEXT"
            description="ChiNext"
          />

          <UniverseItem
            name="STAR"
            description="STAR Market"
          />
        </div>
      </section>

      <section className="market-section">
        <div className="market-section-title">
          <div>
            <h2>Data Diagnostics</h2>
            <p>
              Transparent runtime information. No simulated market data.
            </p>
          </div>
        </div>

        <div className="diagnostic-table">
          <DiagnosticRow
            label="Realtime source"
            value={data?.realtime_source || '--'}
          />

          <DiagnosticRow
            label="Realtime instruments"
            value={
              data?.realtime_count?.toLocaleString() || '--'
            }
          />

          <DiagnosticRow
            label="Target universe"
            value={data?.target_universe || '--'}
          />

          <DiagnosticRow
            label="Server time"
            value={data?.server_time || '--'}
          />

          <DiagnosticRow
            label="Concept provider"
            value={data?.concept_provider || 'Not configured'}
          />

          <DiagnosticRow
            label="Concept data"
            value={
              conceptBuilding
                ? 'Developing'
                : `${data?.concept_count ?? 0}`
            }
          />
        </div>
      </section>

      <div className="market-footer-note">
        <CheckCircleOutlined />
        <span>Real data only · No simulated market data</span>
      </div>
    </div>
  )
}

function StatusItem({
  title,
  value,
  ok,
  developing = false,
}: {
  title: string
  value: string
  ok?: boolean
  developing?: boolean
}) {
  return (
    <div className="status-item">
      <div className="status-item-title">{title}</div>

      <div className="status-item-value">
        <span
          className={`status-dot ${
            developing
              ? 'developing'
              : ok
                ? 'online'
                : 'offline'
          }`}
        />

        {value}
      </div>
    </div>
  )
}

function UniverseItem({
  name,
  description,
}: {
  name: string
  description: string
}) {
  return (
    <div className="universe-item">
      <div className="universe-name">{name}</div>
      <div className="universe-description">{description}</div>
    </div>
  )
}

function DiagnosticRow({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div className="diagnostic-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}
