import { useEffect, useMemo, useState } from 'react'
import ReactECharts from 'echarts-for-react'
import { getRealtimeStocks, getStockHistory } from '../api'
import './StockAnalysis.css'

type Stock = { code: string; name: string; open?: number; close?: number; high?: number; low?: number; changePct?: number; volume?: number; amount?: number }
type Bar = { date: string; code: string; open: number; high: number; low: number; close: number; volume: number; amount: number; change_pct: number; prev_close: number }
type ChartKind = 'hollow' | 'filled' | 'us-line' | 'close-line'
type Period = 'day' | 'week' | 'month'
type ColorMode = 'cn' | 'western'

function formatPrice(value: number | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(2) : '--'
}
function movingAverage(values: number[], windowSize: number) {
  return values.map((_, index) => {
    if (index + 1 < windowSize) return '-'
    const slice = values.slice(index + 1 - windowSize, index + 1)
    return Number((slice.reduce((sum, value) => sum + value, 0) / windowSize).toFixed(3))
  })
}
function periodize(rows: Bar[], period: Period): Bar[] {
  if (period === 'day') return rows
  const grouped = new Map<string, Bar[]>()
  rows.forEach((row) => {
    const date = new Date(row.date + 'T00:00:00')
    if (Number.isNaN(date.getTime())) return
    const key = period === 'month' ? row.date.slice(0, 7) : (() => {
      const monday = new Date(date)
      monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7))
      return monday.toISOString().slice(0, 10)
    })()
    grouped.set(key, [...(grouped.get(key) ?? []), row])
  })
  return [...grouped.entries()].map(([key, bars]) => {
    const first = bars[0]
    const last = bars[bars.length - 1]
    return { ...last, date: key, open: first.open, high: Math.max(...bars.map((bar) => bar.high)), low: Math.min(...bars.map((bar) => bar.low)), close: last.close, volume: bars.reduce((sum, bar) => sum + bar.volume, 0), amount: bars.reduce((sum, bar) => sum + bar.amount, 0), change_pct: first.open ? (last.close / first.open - 1) * 100 : 0 }
  })
}

export default function StockAnalysis() {
  const [stocks, setStocks] = useState<Stock[]>([])
  const [source, setSource] = useState('')
  const [updatedAt, setUpdatedAt] = useState('')
  const [loadingStocks, setLoadingStocks] = useState(true)
  const [stockError, setStockError] = useState('')
  const [query, setQuery] = useState('600519')
  const [selected, setSelected] = useState<Stock | null>(null)
  const [chartKind, setChartKind] = useState<ChartKind>('hollow')
  const [period, setPeriod] = useState<Period>('day')
  const [colorMode, setColorMode] = useState<ColorMode>('cn')
  const [showVolume, setShowVolume] = useState(true)
  const [showMA, setShowMA] = useState(true)
  const [history, setHistory] = useState<Bar[]>([])
  const [historySource, setHistorySource] = useState('')
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')
  const [range, setRange] = useState('1y')

  useEffect(() => {
    let active = true
    getRealtimeStocks().then((response) => {
      if (!active) return
      const payload = response.data
      setStocks(Array.isArray(payload?.stocks) ? payload.stocks : [])
      setSource(payload?.source ?? '')
      setUpdatedAt(payload?.updated_at ?? '')
      if (!Array.isArray(payload?.stocks)) setStockError('行情接口没有返回股票列表')
    }).catch((error: unknown) => {
      if (active) setStockError(error instanceof Error ? error.message : '无法加载实时股票列表')
    }).finally(() => { if (active) setLoadingStocks(false) })
    return () => { active = false }
  }, [])

  const results = useMemo(() => {
    const needle = query.trim().toLowerCase().replace(/\s+/g, '')
    if (!needle) return stocks.slice(0, 30)
    return stocks.filter((stock) => {
      const code = stock.code.toLowerCase().replace(/\s+/g, '')
      const name = stock.name.toLowerCase().replace(/\s+/g, '')
      return code.includes(needle) || name.includes(needle)
    }).slice(0, 40)
  }, [query, stocks])

  const loadHistory = async (stock: Stock, selectedRange = range) => {
    setSelected(stock)
    setQuery(stock.code)
    setHistoryLoading(true)
    setHistoryError('')
    setHistory([])
    const end = new Date()
    const start = new Date(end)
    start.setDate(start.getDate() - (selectedRange === '3m' ? 92 : selectedRange === '6m' ? 184 : selectedRange === '2y' ? 730 : 365))
    const asDate = (date: Date) => {
      const y = date.getFullYear()
      const m = String(date.getMonth() + 1).padStart(2, '0')
      const d = String(date.getDate()).padStart(2, '0')
      return `${y}${m}${d}`
    }
    try {
      const response = await getStockHistory(stock.code, asDate(start), asDate(end))
      const payload = response.data
      if (payload?.status !== 'ok' || !Array.isArray(payload?.rows)) throw new Error(payload?.error || '历史行情接口返回异常')
      setHistory(payload.rows)
      setHistorySource(payload.source || 'AKShare/EastMoney')
    } catch (error) {
      setHistoryError(error instanceof Error ? error.message : '历史行情加载失败')
    } finally { setHistoryLoading(false) }
  }

  const confirmQuery = () => {
    const normalized = query.trim().toUpperCase()
    const found = stocks.find((stock) => stock.code === normalized || stock.name === normalized)
    if (found) void loadHistory(found)
    else if (/^[0-9]{6}$/.test(normalized)) setHistoryError('股票代码不在当前主板、创业板、科创板实时股票池中，或行情接口暂不可用。')
    else setHistoryError('请从真实行情搜索结果中选择股票。美股/ETF 数据源尚未接入，不显示模拟行情。')
  }
  const chartRows = useMemo(() => periodize(history, period), [history, period])

  const option = useMemo(() => {
    const dates = chartRows.map((bar) => bar.date)
    const candles = chartRows.map((bar) => [bar.open, bar.close, bar.low, bar.high])
    const closes = chartRows.map((bar) => bar.close)
    const volumes = chartRows.map((bar) => ({ value: bar.volume, itemStyle: { color: (bar.close >= bar.open) === (colorMode === 'cn') ? '#e34b4b' : '#16a36a' } }))
    const up = colorMode === 'cn' ? '#e34b4b' : '#16a36a'
    const down = colorMode === 'cn' ? '#16a36a' : '#e34b4b'
    const hollow = chartKind === 'hollow'
    const lineOnly = chartKind === 'close-line' || chartKind === 'us-line'
    const series: Record<string, unknown>[] = []
    if (lineOnly) {
      series.push({ name: chartKind === 'us-line' ? 'US-style close' : 'Close', type: 'line', data: closes, showSymbol: false, smooth: false, lineStyle: { width: 1.8, color: chartKind === 'us-line' ? '#5b8ff9' : up }, itemStyle: { color: chartKind === 'us-line' ? '#5b8ff9' : up } })
    } else {
      series.push({ name: 'OHLC', type: 'candlestick', data: candles, barMaxWidth: 14, itemStyle: { color: hollow ? 'rgba(0,0,0,0)' : up, color0: down, borderColor: up, borderColor0: down, borderWidth: 1 } })
      if (showMA) [5, 10, 20].forEach((windowSize, index) => series.push({ name: `MA${windowSize}`, type: 'line', data: movingAverage(closes, windowSize), showSymbol: false, connectNulls: false, lineStyle: { width: 1, opacity: 0.9 }, itemStyle: { color: ['#d6a84f', '#7b8ee8', '#8c63c7'][index] } }))
    }
    if (showVolume) series.push({ name: 'Volume', type: 'bar', xAxisIndex: 1, yAxisIndex: 1, data: volumes, barMaxWidth: 14 })
    return {
      animation: false, backgroundColor: 'transparent', legend: { show: false },
      tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } }, axisPointer: { link: [{ xAxisIndex: 'all' }] },
      grid: [{ left: 56, right: 20, top: 18, height: showVolume ? '62%' : '78%' }, ...(showVolume ? [{ left: 56, right: 20, top: '77%', height: '15%' }] : [])],
      xAxis: [{ type: 'category', data: dates, boundaryGap: true, axisLine: { lineStyle: { color: '#dce2ea' } }, axisLabel: { color: '#7d8999', hideOverlap: true } }, ...(showVolume ? [{ type: 'category', data: dates, gridIndex: 1, boundaryGap: true, axisLine: { lineStyle: { color: '#dce2ea' } }, axisLabel: { show: false } }] : [])],
      yAxis: [{ scale: true, splitNumber: 4, axisLabel: { color: '#7d8999' }, splitLine: { lineStyle: { color: '#edf0f4' } } }, ...(showVolume ? [{ scale: true, gridIndex: 1, axisLabel: { color: '#7d8999', formatter: (value: number) => value >= 100000000 ? `${(value / 100000000).toFixed(1)}亿` : value >= 10000 ? `${(value / 10000).toFixed(0)}万` : String(value) }, splitLine: { show: false } }] : [])],
      dataZoom: [{ type: 'inside', xAxisIndex: showVolume ? [0, 1] : [0], start: Math.max(0, 100 - 1200 / Math.max(chartRows.length, 1) * 100), end: 100 }, { type: 'slider', xAxisIndex: showVolume ? [0, 1] : [0], bottom: 2, height: 16, borderColor: 'transparent', fillerColor: 'rgba(31,111,235,.12)', handleStyle: { color: '#8ea9d6' } }],
      series,
    }
  }, [chartRows, chartKind, colorMode, showVolume, showMA])

  return <div className="stock-terminal">
    <header className="stock-terminal-heading"><div><div className="stock-eyebrow">MARKET INTELLIGENCE / CHARTING</div><h1>Stock Analysis</h1><p>基于实时股票池与历史 OHLCV 的行情分析终端；没有行情数据时不生成占位 K 线。</p></div><div className="stock-data-state"><span className={stockError ? 'state-dot bad' : 'state-dot'} />{loadingStocks ? '正在加载股票池' : stockError ? '股票池不可用' : `股票池 ${stocks.length.toLocaleString()} 只`}</div></header>
    <section className="stock-search-panel">
      <div className="stock-search-row"><label className="stock-search-input-wrap"><span>股票名称 / 代码</span><input value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') confirmQuery() }} placeholder="输入名称或代码，例如 贵州茅台 / 600519" /></label><button className="stock-primary-btn" onClick={confirmQuery} disabled={loadingStocks}>确认 / 查询</button><button className="stock-secondary-btn" onClick={() => { setQuery(''); setSelected(null); setHistory([]); setHistoryError('') }}>清空</button></div>
      {stockError && <div className="stock-inline-error">{stockError}</div>}
      <div className="stock-results"><div className="stock-results-header"><strong>匹配结果</strong><span>{loadingStocks ? '加载中…' : `显示 ${results.length} 条`}{updatedAt ? ` · 更新于 ${updatedAt}` : ''}{source ? ` · ${source}` : ''}</span></div>{results.length === 0 ? <div className="stock-empty">{loadingStocks ? '正在获取真实股票列表…' : '没有匹配项。可尝试输入代码或名称片段。'}</div> : <div className="stock-results-list">{results.map((stock) => <button key={stock.code} className={selected?.code === stock.code ? 'stock-result selected' : 'stock-result'} onClick={() => void loadHistory(stock)}><span className="stock-result-code">{stock.code}</span><span className="stock-result-name">{stock.name}</span><span className={Number(stock.changePct ?? 0) >= 0 ? 'stock-result-change up' : 'stock-result-change down'}>{typeof stock.changePct === 'number' ? `${stock.changePct >= 0 ? '+' : ''}${stock.changePct.toFixed(2)}%` : '--'}</span><span className="stock-result-price">{formatPrice(stock.close)}</span></button>)}</div>}</div>
    </section>
    <section className="stock-chart-panel">
      <div className="stock-chart-topline"><div className="stock-selected-title"><strong>{selected?.name ?? '选择一只股票'}</strong><span>{selected?.code ?? '—'}</span>{selected && <b className={Number(selected.changePct ?? 0) >= 0 ? 'up' : 'down'}>{formatPrice(selected.close)}　{Number(selected.changePct ?? 0) >= 0 ? '+' : ''}{Number(selected.changePct ?? 0).toFixed(2)}%</b>}</div><div className="stock-chart-actions"><label className="stock-select-label">周期<select value={period} onChange={(event) => setPeriod(event.target.value as Period)}><option value="day">日线</option><option value="week">周线</option><option value="month">月线</option></select></label><label className="stock-select-label">颜色<select value={colorMode} onChange={(event) => setColorMode(event.target.value as ColorMode)}><option value="cn">A股红涨绿跌</option><option value="western">绿涨红跌</option></select></label><label className="stock-select-label">范围<select value={range} onChange={(event) => { setRange(event.target.value); if (selected) void loadHistory(selected, event.target.value) }}><option value="3m">3个月</option><option value="6m">6个月</option><option value="1y">1年</option><option value="2y">2年</option></select></label></div></div>
      <div className="stock-chart-toolbar">{([['hollow', '空心 K 线'], ['filled', '实心 K 线'], ['us-line', '美式线图'], ['close-line', '收盘价线']] as [ChartKind, string][]).map(([kind, label]) => <button key={kind} className={chartKind === kind ? 'active' : ''} onClick={() => setChartKind(kind)}>{label}</button>)}<label><input type="checkbox" checked={showMA} onChange={(event) => setShowMA(event.target.checked)} /> MA 5/10/20</label><label><input type="checkbox" checked={showVolume} onChange={(event) => setShowVolume(event.target.checked)} /> 成交量</label><span className="stock-chart-source">{historySource}</span></div>
      {historyLoading ? <div className="stock-chart-message"><span className="stock-spinner" />正在请求真实历史行情…</div> : historyError ? <div className="stock-chart-message error">{historyError}</div> : chartRows.length === 0 ? <div className="stock-chart-message">从上方真实行情列表选择股票，加载历史 OHLCV 后显示图表。</div> : <ReactECharts option={option as never} notMerge lazyUpdate style={{ height: 470, width: '100%' }} />}
      {chartRows.length > 0 && <div className="stock-chart-footer">数据源：{historySource || '历史行情接口'} · {chartRows.length} 根{period === 'day' ? '日' : period === 'week' ? '周' : '月'} K 线 · 前复权 · 支持缩放拖动</div>}
    </section>
  </div>
}