import { defineStore } from 'pinia'

/**
 * 主题偏好（三态）：auto 跟随系统 / light 浅色 / dark 深色。
 * 深色实现 = html 挂 dark class（EP 官方 dark css-vars + styles.css 的 html.dark 覆盖块联动）。
 * 偏好存 localStorage（mb_theme），默认 auto 跟随系统。
 */
const KEY = 'mb_theme'

function readPref() {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' ? v : 'auto'
  } catch {
    return 'auto'
  }
}

function systemDark() {
  return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
}

function applyClass(pref) {
  const dark = pref === 'dark' || (pref === 'auto' && systemDark())
  document.documentElement.classList.toggle('dark', dark)
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    pref: readPref(),
    systemDark: systemDark()
  }),
  getters: {
    isDark(state) {
      if (state.pref === 'dark') return true
      if (state.pref === 'light') return false
      return state.systemDark
    },
    /** 当前偏好文案（开关显示用）。 */
    label(state) {
      return state.pref === 'dark' ? '深色' : state.pref === 'light' ? '浅色' : '跟随系统'
    }
  },
  actions: {
    init() {
      applyClass(this.pref)
      // auto 模式下监听系统切换
      try {
        if (typeof matchMedia === 'function') {
          matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
            this.systemDark = e.matches
            if (this.pref === 'auto') applyClass(this.pref)
          })
        }
      } catch {
        /* ignore */
      }
    },
    setPref(pref) {
      this.pref = pref
      try {
        localStorage.setItem(KEY, pref)
      } catch {
        /* ignore quota */
      }
      applyClass(pref)
    }
  }
})
