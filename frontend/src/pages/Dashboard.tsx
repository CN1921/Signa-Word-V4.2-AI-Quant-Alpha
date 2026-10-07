import { useEffect, useState } from 'react'
import {
  Alert,
  Card,
  Col,
  Progress,
  Row,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import {
  ApiOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  LineChartOutlined,
  NodeIndexOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { getQuantHealth, getRealtimeStatus } from '../api'
import './Dashboard.css'

const { Text } = Typography

type SystemStatus = {
  status?: string
  realtime_count?: number
  realtime_loaded?: boolean
  realtime_source?: string
  target_universe?: string
  concept_count?: number
  concepts_loaded?: boolean
  concepts_building?: boolean
  concept_provider?: string
  concept_progress?: {
    done?: number
    total?: number
  }
}

function StatusTag({
  status,
  children,
}: {
  status: 'success' | 'warning' | 'default'
  children: React.ReactNode
}) {
  const color =
    status === 'success' ? 'green' : status === 'warning' ? 'gold' : 'default'

  return (
    <Tag color={color}>
      {status === 'success' && <CheckCircleOutlined />}
      {status === 'warning' && <ThunderboltOutlined />}
      {status === 'default' && <DatabaseOutlined />}
      {'  '}
      {children}
    </Tag>
  )
}

export default function Dashboard() {
  const [quant, setQuant] = useState<SystemStatus>({})
  const [market, setMarket] = useState<SystemStatus>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadStatus = async () => {
    setLoading(true)
    setError('')

    try {
      const [quantResponse, marketResponse] = await Promise.allSettled([
        getQuantHealth(),
        getRealtimeStatus(),
      ])

      if (quantResponse.status === 'fulfilled') {
        setQuant(quantResponse.value.data || {})
      }

      if (marketResponse.status === 'fulfilled') {
        setMarket(marketResponse.value.data || {})
      }

      if (
        quantResponse.status === 'rejected' &&
        marketResponse.status === 'rejected'
      ) {
        setError('无法连接后端服务，请确认 Java 与 Python Bridge 正在运行。')
      }
    } catch {
      setError('系统状态获取失败。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadStatus()

    const timer = window.setInterval(loadStatus, 30000)

    return () => window.clearInterval(timer)
  }, [])

  const realtimeCount = market.realtime_count ?? 0
  const conceptCount = market.concept_count ?? 0
  const realtimeLoaded = market.realtime_loaded === true
  const conceptsLoaded = market.concepts_loaded === true
  const conceptBuilding = market.concepts_building === true

  const progressDone = market.concept_progress?.done ?? 0
  const progressTotal = market.concept_progress?.total ?? 0
  const progress =
    progressTotal > 0
      ? Math.round((progressDone / progressTotal) * 100)
      : 0

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div>
          <div className="brand-row">
            <div className="brand-mark">SW</div>
            <div>
              <h1>Signa-Word AI Quant Alpha</h1>
              <span>AI 量化阿尔法研究终端</span>
            </div>
          </div>
        </div>

        <Space size="small">
          <StatusTag status={realtimeLoaded ? 'success' : 'default'}>
            {realtimeLoaded ? '行情已连接' : '行情未连接'}
          </StatusTag>
          <Text type="secondary">V4.2 Alpha</Text>
        </Space>
      </header>

      {error && (
        <Alert
          className="system-alert"
          type="warning"
          showIcon
          message={error}
        />
      )}

      <section className="overview-grid">
        <Card className="metric-card" loading={loading}>
          <Statistic
            title={
              <span>
                <LineChartOutlined /> 实时行情
              </span>
            }
            value={realtimeCount}
            suffix="只"
          />
          <div className="metric-footer">
            <StatusTag status={realtimeLoaded ? 'success' : 'default'}>
              {realtimeLoaded ? '数据已加载' : '等待数据'}
            </StatusTag>
            <Text type="secondary">
              {market.realtime_source || '未连接'}
            </Text>
          </div>
        </Card>

        <Card className="metric-card" loading={loading}>
          <Statistic
            title={
              <span>
                <ThunderboltOutlined /> Alpha Engine
              </span>
            }
            value="V4.2"
          />
          <div className="metric-footer">
            <StatusTag status="success">核心已部署</StatusTag>
            <Text type="secondary">Factor Store</Text>
          </div>
        </Card>

        <Card className="metric-card" loading={loading}>
          <Statistic
            title={
              <span>
                <NodeIndexOutlined /> 概念数据
              </span>
            }
            value={conceptCount}
            suffix="个"
          />
          <div className="metric-footer">
            <StatusTag
              status={conceptsLoaded ? 'success' : 'warning'}
            >
              {conceptsLoaded ? '已加载' : '开发中'}
            </StatusTag>
            <Text type="secondary">
              {market.concept_provider || '待接入'}
            </Text>
          </div>
        </Card>

        <Card className="metric-card" loading={loading}>
          <Statistic
            title={
              <span>
                <ExperimentOutlined /> 模型系统
              </span>
            }
            value="LightGBM"
          />
          <div className="metric-footer">
            <StatusTag status="warning">训练模块开发中</StatusTag>
            <Text type="secondary">Walk-forward</Text>
          </div>
        </Card>
      </section>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Card
            title={
              <span>
                <LineChartOutlined /> 市场概览
              </span>
            }
            extra={<Text type="secondary">实时数据</Text>}
            className="panel-card"
          >
            <div className="market-overview">
              <div className="market-universe">
                <div className="section-label">当前股票池</div>
                <div className="universe-value">
                  {realtimeCount.toLocaleString()}
                </div>
                <div className="universe-caption">
                  {market.target_universe || 'MAIN + CHINEXT + STAR'}
                </div>
              </div>

              <div className="system-state">
                <div className="section-label">数据连接</div>
                <div className="state-line">
                  <span
                    className={`state-dot ${
                      realtimeLoaded ? 'online' : ''
                    }`}
                  />
                  <strong>
                    {realtimeLoaded ? 'Realtime Online' : 'Offline'}
                  </strong>
                </div>
                <Text type="secondary">
                  Source: {market.realtime_source || 'N/A'}
                </Text>
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            title={
              <span>
                <ApiOutlined /> 系统状态
              </span>
            }
            className="panel-card"
          >
            <div className="status-list">
              <div className="status-item">
                <span>Python Bridge</span>
                <StatusTag status={realtimeLoaded ? 'success' : 'default'}>
                  {realtimeLoaded ? '正常' : '检查中'}
                </StatusTag>
              </div>

              <div className="status-item">
                <span>Realtime Market</span>
                <StatusTag status={realtimeLoaded ? 'success' : 'default'}>
                  {realtimeLoaded ? '正常' : '离线'}
                </StatusTag>
              </div>

              <div className="status-item">
                <span>Concept Engine</span>
                <StatusTag
                  status={
                    conceptsLoaded
                      ? 'success'
                      : conceptBuilding
                        ? 'warning'
                        : 'default'
                  }
                >
                  {conceptsLoaded
                    ? '正常'
                    : conceptBuilding
                      ? '构建中'
                      : '开发中'}
                </StatusTag>
              </div>

              <div className="status-item">
                <span>Quant Engine</span>
                <StatusTag status="success">V4.2</StatusTag>
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} className="feature-row">
        <Col xs={24} md={8}>
          <Card className="feature-card">
            <div className="feature-icon">
              <ThunderboltOutlined />
            </div>
            <div>
              <h3>Alpha Ranking</h3>
              <p>横截面 Alpha 因子与股票排序</p>
              <Tag color="gold">开发中</Tag>
            </div>
          </Card>
        </Col>

        <Col xs={24} md={8}>
          <Card className="feature-card">
            <div className="feature-icon">
              <DatabaseOutlined />
            </div>
            <div>
              <h3>Event Engine</h3>
              <p>新闻事件、名称共振与情绪特征</p>
              <Tag color="gold">开发中</Tag>
            </div>
          </Card>
        </Col>

        <Col xs={24} md={8}>
          <Card className="feature-card">
            <div className="feature-icon">
              <ExperimentOutlined />
            </div>
            <div>
              <h3>Backtest</h3>
              <p>T+1、100 股整手与交易成本模型</p>
              <Tag color="gold">开发中</Tag>
            </div>
          </Card>
        </Col>
      </Row>

      {conceptBuilding && (
        <Card className="progress-card">
          <div className="progress-header">
            <span>概念数据构建</span>
            <span>
              {progressDone} / {progressTotal}
            </span>
          </div>
          <Progress percent={progress} />
        </Card>
      )}

      <footer className="dashboard-footer">
        <span>Signa-Word AI Quant Alpha</span>
        <span>Real data only · No simulated market data</span>
      </footer>
    </div>
  )
}
