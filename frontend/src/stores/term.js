import { defineStore } from 'pinia'
import api from '../api/client'

/** 学期上下文：浏览学期（view）/ 工作学期（active，唯一未归档）。 */
export const useTermStore = defineStore('term', {
  state: () => ({
    loaded: false,
    terms: [],
    viewTermId: null,
    activeTermId: null
  }),
  getters: {
    viewTerm(state) {
      return state.terms.find((t) => t.id === state.viewTermId) || null
    },
    /** 是否在浏览历史学期（只读模式）。 */
    browsingHistory(state) {
      return state.loaded && state.viewTermId !== null && state.viewTermId !== state.activeTermId
    },
    viewName() {
      return this.viewTerm ? this.viewTerm.name : ''
    }
  },
  actions: {
    async refresh() {
      try {
        const { data } = await api.get('/terms')
        this.terms = data.data.terms || []
        this.viewTermId = data.data.viewTermId
        this.activeTermId = data.data.activeTermId
        this.loaded = true
      } catch (e) {
        /* 拦截器已提示；未登录场景静默 */
      }
    },
    async switchTo(id) {
      await api.post(`/terms/${id}/switch`)
      // hash 路由 + keep-alive，整页刷新是让所有页面回到新学期数据最可靠的方式
      window.location.reload()
    },
    async createAndSwitch(payload) {
      await api.post('/terms', payload)
      window.location.reload()
    }
  }
})
