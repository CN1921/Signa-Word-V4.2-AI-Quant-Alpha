import axios from 'axios'

const api = axios.create({
  baseURL: '/',
  timeout: 30000,
})

export const getQuantHealth = () =>
  api.get('/api/quant/health')

export const getRealtimeStatus = () =>
  api.get('/bridge/health')

export const getRealtimeStocks = () =>
  api.get('/bridge/api/stocks/realtime')

export const getStockHistory = (
  code: string,
  startDate: string,
  endDate: string,
  adjust = 'qfq',
) =>
  api.get('/api/quant/history', {
    params: { code, startDate, endDate, adjust },
  })

export const getFeatures = (code: string) =>
  api.get('/api/quant/features', {
    params: { code },
  })
