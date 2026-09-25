<template>
  <div class="page-card">
    <h2 class="page-title">✨ 名单筛查</h2>
    <p class="muted" style="margin-top: 0">
      将第三方导出的报名 / 抽签 / 签到 / 作业名单粘贴进来，系统以基准名单自动比对，未参与一目了然。
    </p>

    <el-form label-position="top">
      <el-form-item label="粘贴名单">
        <el-input
          v-model="text"
          type="textarea"
          :rows="8"
          placeholder="例如：&#10;1. 王伟&#10;2、李娜&#10;张三 张三&#10;外校同学"
        />
      </el-form-item>
      <div class="toolbar">
        <input
          ref="checkFile"
          type="file"
          accept=".txt,.csv,.xlsx,.xls,.png,.jpg,.jpeg,.bmp,.webp"
          style="display: none"
          @change="onCheckFile"
        />
        <div
          class="check-drop"
          :class="{ 'is-dragover': dragOver }"
          style="width: 100%"
          @dragenter.prevent="onDragEnter"
          @dragover.prevent="onDragOver"
          @dragleave.prevent="onDragLeave"
          @drop.prevent="onDrop"
          @click="checkFile && checkFile.click()"
        >
          <span>{{ dragOver ? '🎉 松开即可导入' : '📥 把名单文件或图片拖到这里，或点击选择' }}</span>
          <div class="muted" style="font-weight: 400; margin-top: 4px">
            txt/csv 直读 · xlsx 解析姓名 · 图片 OCR 填入文本框
            <span v-if="checkFileInfo"> · {{ checkFileInfo }}</span>
          </div>
        </div>
      </div>
      <div class="toolbar">
        <el-input v-model="title" placeholder="任务名称（可选）" style="width: 220px" />
        <el-select v-model="taskId" clearable placeholder="关联长期任务" style="width: 200px">
          <el-option v-for="t in tasks" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-checkbox v-model="save">保存为历史记录</el-checkbox>
        <el-button type="primary" :loading="loading" @click="runCheck">一键筛查</el-button>
        <el-button v-if="result" @click="copyAbsent">复制未参与名单</el-button>
        <el-button v-if="result && result.recordId" @click="exportResult('xlsx')">导出Excel</el-button>
        <el-button v-if="result && result.recordId" @click="exportResult('csv')">导出CSV</el-button>
        <el-button v-if="result || text" type="warning" plain @click="clearAll">一键清空</el-button>
      </div>
    </el-form>

    <template v-if="result">
      <div class="stat-row">
        <div class="stat-item">
          <div class="label">基准人数</div>
          <div class="value">{{ result.counts.baseline }}</div>
        </div>
        <div class="stat-item" style="background: #ecfdf5">
          <div class="label">已参与</div>
          <div class="value">{{ result.counts.participated }}</div>
        </div>
        <div class="stat-item" style="background: #fef2f2">
          <div class="label">未参与</div>
          <div class="value">{{ result.counts.absent }}</div>
        </div>
        <div class="stat-item" style="background: #fff7ed">
          <div class="label">无效/异常</div>
          <div class="value">{{ result.counts.invalid }}</div>
        </div>
        <div class="stat-item" style="background: #f5f3ff">
          <div class="label">重复姓名</div>
          <div class="value">{{ result.counts.duplicates }}</div>
        </div>
      </div>

      <el-row :gutter="12">
        <el-col :md="8" :sm="24" style="margin-bottom: 12px">
          <div class="page-card" style="box-shadow: none; border: 1px solid #e5e7eb">
            <h3>已参与</h3>
            <div>
              <span v-for="n in result.participated" :key="'p' + n" class="name-chip">{{ n }}</span>
              <span v-if="!result.participated.length" class="muted">（无）</span>
            </div>
          </div>
        </el-col>
        <el-col :md="8" :sm="24" style="margin-bottom: 12px">
          <div class="page-card" style="box-shadow: none; border: 1px solid #e5e7eb">
            <h3>未参与</h3>
            <div>
              <span v-for="n in result.absent" :key="'a' + n" class="name-chip absent">{{ n }}</span>
              <span v-if="!result.absent.length" class="muted">（无）</span>
            </div>
          </div>
        </el-col>
        <el-col :md="8" :sm="24" style="margin-bottom: 12px">
          <div class="page-card" style="box-shadow: none; border: 1px solid #e5e7eb">
            <h3>无效/异常</h3>
            <div>
              <span v-for="n in result.invalid" :key="'i' + n" class="name-chip invalid">{{ n }}</span>
              <span v-if="!result.invalid.length" class="muted">（无）</span>
            </div>
            <h3 style="margin-top: 12px">重复姓名</h3>
            <div>
              <span v-for="n in result.duplicates" :key="'d' + n" class="name-chip">{{ n }}</span>
              <span v-if="!result.duplicates.length" class="muted">（无）</span>
            </div>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useCheckStore } from '../stores/check'

const store = useCheckStore()
const text = computed({
  get: () => store.text,
  set: (v) => store.setText(v)
})
const title = computed({
  get: () => store.title,
  set: (v) => store.setTitle(v)
})
const taskId = computed({
  get: () => store.taskId,
  set: (v) => store.setTaskId(v)
})
const save = computed({
  get: () => store.save,
  set: (v) => store.setSave(v)
})
const result = computed({
  get: () => store.result,
  set: (v) => store.setResult(v)
})
const loading = ref(false)
const tasks = ref([])
const checkFile = ref(null)
const checkFileInfo = ref('')
const dragOver = ref(false)
let dragDepth = 0

function onDragEnter() {
  dragDepth += 1
  dragOver.value = true
}

function onDragOver() {
  dragOver.value = true
}

function onDragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragOver.value = false
}

async function onDrop(e) {
  dragDepth = 0
  dragOver.value = false
  const files = e.dataTransfer && e.dataTransfer.files
  if (!files || !files.length) return
  await handleImportFile(files[0])
}

async function loadTasks() {
  const { data } = await api.get('/tasks')
  tasks.value = data.data || []
}

async function onCheckFile(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  await handleImportFile(file)
}

async function handleImportFile(file) {
  if (!file) return
  const name = (file.name || '').toLowerCase()
  const isImage = /\.(png|jpe?g|bmp|webp|gif)$/.test(name) || (file.type || '').startsWith('image/')
  checkFileInfo.value = isImage ? `OCR 识别 ${file.name} …` : `读取 ${file.name} …`
  try {
    const formData = new FormData()
    formData.append('file', file)
    if (isImage) {
      const { data } = await api.post('/students/parse-image', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 60000
      })
      const rows = data.data.rows || []
      text.value = rows.map((r) => (r.studentNo ? `${r.studentNo} ${r.name}` : r.name)).join('\n')
      checkFileInfo.value = `📷 OCR 识别 ${rows.length} 人已填入文本框（请核对后筛查）`
    } else if (name.endsWith('.xlsx') || name.endsWith('.xls')) {
      const { data } = await api.post('/students/parse-import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      const rows = data.data.rows || []
      text.value = rows.map((r) => r.name).join('\n')
      checkFileInfo.value = `已从 Excel 解析 ${rows.length} 个姓名填入文本框`
    } else {
      const raw = await file.text()
      text.value = raw
      checkFileInfo.value = `已读入 ${file.name}（${raw.length} 字符），可直接一键筛查`
    }
  } catch (err) {
    checkFileInfo.value = ''
  } finally {
    if (checkFile.value) checkFile.value.value = ''
  }
}

async function runCheck() {
  if (!text.value.trim()) {
    ElMessage.warning('请先粘贴名单')
    return
  }
  loading.value = true
  try {
    const { data } = await api.post('/check', {
      text: text.value,
      save: save.value,
      title: title.value || null,
      taskId: taskId.value || null
    })
    result.value = data.data
    if (data.data.saveError) {
      ElMessage.warning(data.data.saveError + '（本次结果已展示，可取消保存后重试或改用不保存）')
    } else {
      ElMessage.success(
        `筛查完成：已参与 ${data.data.counts.participated}，未参与 ${data.data.counts.absent}`
      )
    }
  } finally {
    loading.value = false
  }
}

async function copyAbsent() {
  const content = (result.value.absent || []).join('\n')
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('未参与名单已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

async function clearAll() {
  if (!result.value && !text.value.trim()) return
  try {
    await ElMessageBox.confirm(
      '将清空粘贴内容与本次筛查结果（已保存的历史记录不受影响），恢复空白页面。确认清空？',
      '一键清空',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  store.clearAll()
  checkFileInfo.value = ''
  ElMessage.success('已清空，可开始新一轮筛查')
}

function exportResult(format) {
  const id = result.value.recordId
  if (!id) {
    ElMessage.warning('请勾选「保存为历史记录」后再导出')
    return
  }
  const password = localStorage.getItem('accessPassword') || ''
  api
    .get(`/check-records/${id}/export?format=${format}`, {
      responseType: 'blob',
      headers: password ? { 'X-Access-Password': password } : {}
    })
    .then((res) => {
      const blob = new Blob([res.data])
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${result.value.title || '核对结果'}.${format}`
      a.click()
      URL.revokeObjectURL(a.href)
    })
}

onMounted(loadTasks)
</script>
