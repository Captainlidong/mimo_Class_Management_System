import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true
})

// 会话过期统一处理：Layout 挂载后注册，触发时打开登录弹窗
let unauthorizedHandler = null
let lastUnauthorizedAt = 0

export function onUnauthorized(fn) {
  unauthorizedHandler = fn
}

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
    if (status === 401) {
      // 同一时间多个请求 401 时只提示一次，避免弹窗刷屏
      const now = Date.now()
      if (now - lastUnauthorizedAt > 1000) {
        lastUnauthorizedAt = now
        ElMessage.warning('未登录或登录已过期，请先登录')
      }
      if (unauthorizedHandler) {
        unauthorizedHandler()
      }
      return Promise.reject(error)
    }
    const msg =
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default api
