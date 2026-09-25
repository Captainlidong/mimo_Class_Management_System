<template>
  <div class="page-card">
    <div class="toolbar">
      <h2 class="page-title" style="margin: 0">🌸 基准名单</h2>
      <el-tag :type="locked ? 'danger' : 'success'">{{ locked ? '已锁定' : '未锁定' }}</el-tag>
      <span class="muted">共 {{ students.length }} 人</span>
      <div style="flex: 1"></div>
      <el-button v-if="locked" type="warning" @click="unlock">解锁修改</el-button>
      <el-button v-else type="primary" @click="lock">锁定名单</el-button>
      <el-button type="success" :disabled="locked" @click="saveAll">保存名单</el-button>
    </div>

    <el-alert
      v-if="locked"
      type="info"
      show-icon
      title="名单已锁定，筛查基准不会被误改。需要调整时请先解锁。"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <!-- 拖拽 / 点击导入 -->
    <div
      class="page-card import-panel drop-zone"
      :class="{ 'is-dragover': dragOver, 'is-disabled': locked }"
      style="margin-bottom: 12px"
      @dragenter.prevent="onDragEnter"
      @dragover.prevent="onDragOver"
      @dragleave.prevent="onDragLeave"
      @drop.prevent="onDrop"
      @click="!locked && fileInput && fileInput.click()"
    >
      <div class="toolbar" style="margin-bottom: 8px">
        <strong>📂 文件导入 / 📷 图片导入</strong>
        <span class="muted">文件：.txt / .csv / .xlsx；图片：截图 OCR（jpg/png）</span>
        <div style="flex: 1"></div>
        <el-button link type="primary" @click.stop="downloadTemplate">下载导入模板</el-button>
      </div>
      <div class="drop-inner">
        <input
          ref="fileInput"
          type="file"
          accept=".txt,.csv,.xlsx,.xls,.png,.jpg,.jpeg,.bmp,.webp,text/plain,text/csv,image/*"
          :disabled="locked"
          style="display: none"
          @change="onFileChange"
        />
        <div class="drop-icon">{{ dragOver ? '🎉' : '📥' }}</div>
        <div class="drop-text">
          <template v-if="dragOver">松开即可导入</template>
          <template v-else-if="locked">名单已锁定，解锁后可导入</template>
          <template v-else>把文件或图片拖到这里，或点击选择</template>
        </div>
        <div class="drop-hint muted">
          支持 txt / csv / xlsx / jpg / png · 识别后请在预览中确认，再追加/覆盖
          <span v-if="parseInfo"> · {{ parseInfo }}</span>
        </div>
      </div>
    </div>

    <div class="toolbar">
      <el-input
        v-model="pasteText"
        type="textarea"
        :rows="4"
        placeholder="也可以直接批量粘贴名单：一行一个，或用逗号/顿号/学号,姓名"
        :disabled="locked"
      />
    </div>
    <div class="toolbar">
      <el-button :disabled="locked" @click="appendPaste">解析并追加</el-button>
      <el-button :disabled="locked" @click="replaceFromPaste">解析并覆盖</el-button>
      <el-button :disabled="locked" type="warning" plain @click="clearAll">清空</el-button>
      <span v-if="dupWarn" class="muted" style="color:#c2410c">{{ dupWarn }}</span>
    </div>

    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索姓名 / 学号" clearable style="width: 220px" />
      <span class="muted">显示 {{ filteredStudents.length }} / {{ students.length }}</span>
    </div>

    <el-table :data="filteredStudents" border stripe>
      <el-table-column type="index" label="#" width="60" />
      <el-table-column label="姓名" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.name" :disabled="locked" />
        </template>
      </el-table-column>
      <el-table-column label="学号（可选）" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.studentNo" :disabled="locked" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="danger" :disabled="locked" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 导入预览 -->
    <el-dialog v-model="previewVisible" title="导入预览" width="640px" append-to-body>
      <p class="muted">
        解析到 <b>{{ previewRows.length }}</b> 人
        <span v-if="previewSkipped > 0">，跳过 {{ previewSkipped }} 条（空行/表头/重复/无效）</span>
        。请确认后选择「追加」或「覆盖」。
      </p>
      <el-table :data="previewRows.slice(0, 50)" border max-height="320" size="small">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="studentNo" label="学号" width="140">
          <template #default="{ row }">{{ row.studentNo || '—' }}</template>
        </el-table-column>
      </el-table>
      <p v-if="previewRows.length > 50" class="muted">仅预览前 50 条，导入时会包含全部。</p>
      <template #footer>
        <el-button @click="previewVisible = false">取消</el-button>
        <el-button :disabled="locked" @click="applyPreview('append')">追加到列表</el-button>
        <el-button type="primary" :disabled="locked" @click="applyPreview('replace')">覆盖列表</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'

const locked = ref(false)
const students = ref([])
const pasteText = ref('')
const keyword = ref('')
const fileInput = ref(null)
const parseInfo = ref('')
const previewVisible = ref(false)
const previewRows = ref([])
const previewSkipped = ref(0)
const dupWarn = ref('')
const dragOver = ref(false)
let dragDepth = 0

function onDragEnter(e) {
  if (locked.value) return
  dragDepth += 1
  dragOver.value = true
}

function onDragOver(e) {
  if (locked.value) return
  dragOver.value = true
}

function onDragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragOver.value = false
}

async function onDrop(e) {
  dragDepth = 0
  dragOver.value = false
  if (locked.value) return
  const files = e.dataTransfer && e.dataTransfer.files
  if (!files || !files.length) return
  await handleImportFile(files[0])
}

const filteredStudents = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return students.value
  return students.value.filter(
    (s) =>
      (s.name || '').toLowerCase().includes(k) ||
      (s.studentNo || '').toLowerCase().includes(k)
  )
})

async function load() {
  const { data } = await api.get('/students')
  locked.value = data.data.locked
  students.value = data.data.students.map((s) => ({
    id: s.id,
    name: s.name,
    studentNo: s.studentNo || '',
    sortOrder: s.sortOrder
  }))
  refreshDupWarn()
}

function parseNames(text) {
  return text
    .split(/[\s,，、;；\n\r\t|]+/)
    .map((x) => x.replace(/^\d{1,3}[.．、)）:：\-—\s]+/, '').trim())
    .filter(Boolean)
}

function parseRowsFromText(text) {
  const rows = []
  const lines = text.split(/\R/)
  for (const line of lines) {
    if (!line.trim()) continue
    const parts = line.split(/[\t,，、;；|]+/).map((p) => p.trim()).filter(Boolean)
    if (!parts.length) continue
    // skip header
    if (/^(姓名|名字|name|学号|序号|班级)$/i.test(parts.join(''))) continue
    if (parts.length >= 2) {
      const a = parts[0]
      const b = parts[parts.length - 1]
      const noRe = /^[A-Za-z0-9]{4,32}$/
      if (noRe.test(a) && !noRe.test(b)) {
        rows.push({ name: b.replace(/^\d{1,3}[.．、)）:：\-—\s]+/, ''), studentNo: a })
        continue
      }
      if (noRe.test(b)) {
        const name = parts.slice(0, parts.length - 1).join(' ').replace(/^\d{1,3}[.．、)）:：\-—\s]+/, '')
        rows.push({ name, studentNo: b })
        continue
      }
    }
    const name = parts[0].replace(/^\d{1,3}[.．、)）:：\-—\s]+/, '')
    if (name) rows.push({ name, studentNo: '' })
  }
  return rows
}

function rowsToStudents(rows) {
  return rows.map((r, idx) => ({
    id: null,
    name: r.name,
    studentNo: r.studentNo || '',
    sortOrder: idx + 1
  }))
}

function mergeRows(target, incoming) {
  const map = new Map()
  for (const s of target) {
    map.set(s.name, { ...s })
  }
  for (const r of incoming) {
    if (!map.has(r.name)) {
      map.set(r.name, { id: null, name: r.name, studentNo: r.studentNo || '', sortOrder: 0 })
    } else if (r.studentNo && !map.get(r.name).studentNo) {
      map.get(r.name).studentNo = r.studentNo
    }
  }
  return Array.from(map.values()).map((s, idx) => ({ ...s, sortOrder: idx + 1 }))
}

function refreshDupWarn() {
  const count = new Map()
  for (const s of students.value) {
    const n = (s.name || '').trim()
    if (!n) continue
    count.set(n, (count.get(n) || 0) + 1)
  }
  const dups = [...count.entries()].filter(([, c]) => c > 1).map(([n]) => n)
  dupWarn.value = dups.length ? `存在重名：${dups.join('、')}（仅靠姓名无法区分，建议填学号）` : ''
}

function appendPaste() {
  const rows = parseRowsFromText(pasteText.value)
  if (!rows.length) {
    ElMessage.warning('没有解析到姓名')
    return
  }
  students.value = mergeRows(students.value, rows)
  pasteText.value = ''
  refreshDupWarn()
  ElMessage.success(`已追加解析 ${rows.length} 条`)
}

function replaceFromPaste() {
  const rows = parseRowsFromText(pasteText.value)
  if (!rows.length) {
    ElMessage.warning('没有解析到姓名')
    return
  }
  ElMessageBox.confirm(`将用解析到的 ${rows.length} 人覆盖当前列表，确认？`, '覆盖名单', { type: 'warning' })
    .then(() => {
      students.value = rowsToStudents(rows)
      pasteText.value = ''
      refreshDupWarn()
    })
    .catch(() => {})
}

function clearAll() {
  students.value = []
  refreshDupWarn()
}

function removeRow(row) {
  const idx = students.value.indexOf(row)
  if (idx >= 0) students.value.splice(idx, 1)
  refreshDupWarn()
}

async function onFileChange(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  await handleImportFile(file)
}

async function handleImportFile(file) {
  if (!file) return
  const lower = (file.name || '').toLowerCase()
  const isImage = /\.(png|jpe?g|bmp|webp|gif)$/.test(lower) || (file.type || '').startsWith('image/')
  parseInfo.value = isImage
    ? `图片 OCR 识别中：${file.name} …`
    : `正在解析 ${file.name} …`
  try {
    const formData = new FormData()
    formData.append('file', file)
    const url = isImage ? '/students/parse-image' : '/students/parse-import'
    const { data } = await api.post(url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
    previewRows.value = data.data.rows || []
    previewSkipped.value = data.data.skipped || 0
    parseInfo.value =
      (isImage ? '📷 OCR ' : '') +
      `${file.name}：解析 ${previewRows.value.length} 人` +
      (previewSkipped.value ? `，跳过 ${previewSkipped.value} 条` : '')
    previewVisible.value = true
  } catch (err) {
    parseInfo.value = ''
  } finally {
    if (fileInput.value) fileInput.value.value = ''
  }
}

function applyPreview(mode) {
  if (mode === 'replace') {
    students.value = rowsToStudents(previewRows.value)
  } else {
    students.value = mergeRows(students.value, previewRows.value)
  }
  previewVisible.value = false
  refreshDupWarn()
  ElMessage.success(mode === 'replace' ? '已覆盖列表，请核对后点「保存名单」' : '已追加到列表，请核对后点「保存名单」')
}

function downloadTemplate() {
  const password = localStorage.getItem('accessPassword') || ''
  api
    .get('/students/template', {
      responseType: 'blob',
      headers: password ? { 'X-Access-Password': password } : {}
    })
    .then((res) => {
      const blob = new Blob([res.data], { type: 'text/plain;charset=utf-8' })
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = '班级名单导入模板.txt'
      a.click()
      URL.revokeObjectURL(a.href)
    })
}

async function saveAll() {
  refreshDupWarn()
  const empty = students.value.filter((s) => !(s.name || '').trim())
  if (empty.length) {
    ElMessage.warning('存在空姓名，请先补全或删除')
    return
  }
  if (!students.value.length) {
    ElMessage.warning('名单为空')
    return
  }
  const payload = {
    locked: false,
    students: students.value.map((s, idx) => ({
      name: (s.name || '').trim(),
      studentNo: s.studentNo ? String(s.studentNo).trim() : null,
      sortOrder: idx + 1
    }))
  }
  const { data } = await api.put('/students', payload)
  locked.value = data.data.locked
  students.value = data.data.students.map((s) => ({
    id: s.id,
    name: s.name,
    studentNo: s.studentNo || '',
    sortOrder: s.sortOrder
  }))
  refreshDupWarn()
  ElMessage.success(`名单已保存（${students.value.length} 人）`)
}

async function lock() {
  const { data } = await api.post('/students/lock')
  locked.value = data.data.locked
  ElMessage.success('已锁定基准名单')
}

async function unlock() {
  await ElMessageBox.confirm('解锁后可修改基准名单，历史核对记录不受影响。确认解锁？', '解锁名单', {
    type: 'warning'
  })
  const { data } = await api.post('/students/unlock')
  locked.value = data.data.locked
  ElMessage.success('已解锁，可修改名单')
}

onMounted(load)
</script>
