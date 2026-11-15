import { defineStore } from 'pinia'

const KEY = 'classmgmt:check-draft'

function load() {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

function persist(state) {
  try {
    localStorage.setItem(
      KEY,
      JSON.stringify({
        text: state.text,
        title: state.title,
        taskId: state.taskId,
        save: state.save,
        remark: state.remark,
        result: state.result
      })
    )
  } catch {
    // ignore quota
  }
}

export const useCheckStore = defineStore('check', {
  state: () => {
    const draft = load()
    return {
      text: draft.text || '',
      title: draft.title || '',
      taskId: draft.taskId ?? null,
      save: draft.save ?? true,
      remark: draft.remark || '',
      result: draft.result || null
    }
  },
  actions: {
    setText(v) {
      this.text = v
      persist(this.$state)
    },
    setTitle(v) {
      this.title = v
      persist(this.$state)
    },
    setTaskId(v) {
      this.taskId = v
      persist(this.$state)
    },
    setSave(v) {
      this.save = v
      persist(this.$state)
    },
    setRemark(v) {
      this.remark = v
      persist(this.$state)
    },
    setResult(v) {
      this.result = v
      persist(this.$state)
    },
    patchResult(partial) {
      this.result = { ...(this.result || {}), ...partial }
      persist(this.$state)
    },
    clearAll() {
      this.text = ''
      this.title = ''
      this.taskId = null
      this.save = true
      this.remark = ''
      this.result = null
      persist(this.$state)
    }
  }
})
