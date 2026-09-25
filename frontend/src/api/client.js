import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000
})

api.interceptors.request.use((config) => {
  const password = localStorage.getItem('accessPassword') || ''
  if (password) {
    config.headers['X-Access-Password'] = password
  }
  return config
})

api.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload === 'object' && 'code' in payload && payload.code !== 0) {
      const msg = payload.message || '请求失败'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return response
  },
  (error) => {
    const status = error.response && error.response.status
    const msg =
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '网络错误'
    if (status === 401) {
      ElMessage.error(msg || '访问口令错误或未提供，请在右上角填写口令')
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default api
