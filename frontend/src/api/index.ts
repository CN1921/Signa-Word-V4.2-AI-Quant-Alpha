import axios from 'axios'

const api = axios.create({
  baseURL: '/',
  timeout: 10000,
})

export const getQuantHealth = () =>
  api.get('/api/quant/health')

export const getRealtimeStatus = () =>
  api.get('/bridge/health')

export const getFeatures = (code: string) =>
  api.get('/api/quant/features', {
    params: { code },
  })
