import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import api from '../api/client'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    authRequired: false,
    loggedIn: false,
    username: '',
    loginVisible: false,
    // 首次 /auth/status 检查完成前不渲染功能区，避免未登录状态下界面可交互
    statusLoaded: false
  }),
  actions: {
    async refresh() {
      try {
        const { data } = await api.get('/auth/status')
        this.authRequired = !!(data.data && data.data.authRequired)
        this.loggedIn = !!(data.data && data.data.loggedIn)
        this.username = (data.data && data.data.username) || ''
      } catch {
        this.authRequired = false
        this.loggedIn = false
      } finally {
        this.statusLoaded = true
      }
    },
    // 供右上角「登录」按钮使用：仅在需要登录且未登录时弹窗
    openLogin() {
      if (this.authRequired && !this.loggedIn) {
        this.loginVisible = true
      }
    },
    // 401 是权威证据：处于登录保护模式且当前会话无效
    handleUnauthorized() {
      this.authRequired = true
      this.loggedIn = false
      this.loginVisible = true
    },
    async logout() {
      try {
        await api.post('/auth/logout')
      } finally {
        this.loggedIn = false
        ElMessage.success('已退出登录')
      }
    }
  }
})
