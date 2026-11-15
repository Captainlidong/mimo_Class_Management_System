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
            txt/csv 直读 · xlsx/图片解析后可逐行确认再填入
            <span v-if="checkFileInfo"> · {{ checkFileInfo }}</span>
          </div>
        </div>
      </div>
      <div class="toolbar">
        <el-input v-model="title" placeholder="任务名称（可选）" style="width: 220px" />
        <el-input v-model="remark" placeholder="结果备注（可选，如：来自群接龙）" style="width: 240px" />
        <el-select v-model="taskId" clearable placeholder="关联长期任务" style="width: 180px">
          <el-option v-for="t in tasks" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-checkbox v-model="save">保存为历史记录</el-checkbox>
        <el-button type="primary" :loading="loading" @click="runCheck">一键筛查</el-button>
        <el-button v-if="result" @click="copyAbsent">复制未参与名单</el-button>
        <el-button
          v-if="result && result.recordId && result.counts.absent > 0"
          type="success"
          plain
          @click="convertToCollection"
        >未参与转收集任务</el-button>
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
        <div class="stat-item" style="background: #e7f0e9">
          <div class="label">已参与</div>
          <div class="value">{{ result.counts.participated }}</div>
        </div>
        <div class="stat-item" style="background: #faebe7">
          <div class="label">未参与</div>
          <div class="value">{{ result.counts.absent }}</div>
        </div>
        <div class="stat-item" style="background: #fff7ed">
          <div class="label">无效/异常</div>
          <div class="value">{{ result.counts.invalid }}</div>
        </div>
        <div class="stat-item" style="background: #eef4ee">
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

    <!-- 解析确认弹窗：左原图/原文对照，右逐行勾选改名 -->
    <el-dialog v-model="parseConfirm.visible" title="解析结果确认" width="760px" append-to-body :close-on-click-modal="false">
      <div class="parse-layout">
        <div class="parse-left">
          <template v-if="parseConfirm.isImage">
            <img v-if="parseConfirm.fileUrl" :src="parseConfirm.fileUrl" class="parse-img" alt="原图" />
            <el-collapse style="margin-top: 10px">
              <el-collapse-item title="原始 OCR 文本">
                <div class="ocr-raw">{{ parseConfirm.ocrText || '（空）' }}</div>
              </el-collapse-item>
            </el-collapse>
          </template>
          <template v-else>
            <div class="ocr-raw">{{ (parseConfirm.rawLines || []).join('\n') || '（空）' }}</div>
          </template>
        </div>
        <div class="parse-right">
          <div class="muted" style="margin-bottom: 8px">
            解析 {{ parseConfirm.rows.length }} 行
            <span v-if="parseConfirm.skipped"> · 跳过 {{ parseConfirm.skipped }} 行</span>
            · 取消勾选的行不会填入
          </div>
          <div class="parse-rows">
            <div v-for="(row, idx) in parseConfirm.rows" :key="idx" class="parse-row">
              <el-checkbox v-model="row.checked" />
              <el-input v-model="row.name" size="small" style="flex: 1" placeholder="姓名" />
              <span v-if="row.studentNo" class="muted parse-no">{{ row.studentNo }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="parseConfirm.visible = false">取消</el-button>
        <el-button type="primary" @click="applyParse">采用勾选的 {{ checkedCount }} 人填入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { useCheckStore } from '../stores/check'

const store = useCheckStore()
const router = useRouter()
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
const remark = computed({
  get: () => store.remark,
  set: (v) => store.setRemark(v)
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

// 解析确认弹窗状态
const parseConfirm = reactive({
  visible: false,
  isImage: false,
  fileUrl: '',
  ocrText: '',
  rawLines: [],
  skipped: 0,
  rows: []
})

const checkedCount = computed(() => parseConfirm.rows.filter((r) => r.checked).length)

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
      openParseConfirm(data.data, true, file)
      checkFileInfo.value = `📷 OCR 解析 ${data.data.rows.length} 行，请确认后填入`
    } else if (name.endsWith('.xlsx') || name.endsWith('.xls')) {
      const { data } = await api.post('/students/parse-import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      openParseConfirm(data.data, false, file)
      checkFileInfo.value = `已从 Excel 解析 ${data.data.rows.length} 行，请确认后填入`
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

function openParseConfirm(parseData, isImage, file) {
  const rows = (parseData.rows || []).map((r) => ({
    name: r.name,
    studentNo: r.studentNo || '',
    checked: true
  }))
  if (!rows.length) {
    checkFileInfo.value = '解析结果为空，未填入任何内容'
    ElMessage.warning('解析结果为空，请检查文件内容')
    return
  }
  parseConfirm.isImage = isImage
  parseConfirm.fileUrl = isImage && file ? URL.createObjectURL(file) : ''
  parseConfirm.ocrText = parseData.ocrText || ''
  parseConfirm.rawLines = parseData.rawLines || []
  parseConfirm.skipped = parseData.skipped || 0
  parseConfirm.rows = rows
  parseConfirm.visible = true
}

/** 把勾选行填入文本框；已有内容时追加而非覆盖。 */
function applyParse() {
  const picked = parseConfirm.rows
    .filter((r) => r.checked && r.name.trim())
    .map((r) => (r.studentNo ? `${r.studentNo} ${r.name.trim()}` : r.name.trim()))
  if (!picked.length) {
    ElMessage.warning('没有勾选任何行')
    return
  }
  const merged = text.value.trim()
    ? text.value.replace(/\s+$/, '') + '\n' + picked.join('\n')
    : picked.join('\n')
  text.value = merged
  parseConfirm.visible = false
  checkFileInfo.value = `已填入 ${picked.length} 人（请核对后筛查）`
  ElMessage.success(`已填入 ${picked.length} 人`)
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
      taskId: taskId.value || null,
      remark: remark.value || null
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

/** 未参与名单一键转成收集任务（后端按基准名单匹配学生）。 */
async function convertToCollection() {
  try {
    await ElMessageBox.confirm(
      `把 ${result.value.counts.absent} 位未参与同学建成一个补交收集任务？任务会出现在「资料收集与分发」。`,
      '转收集任务',
      { type: 'success', confirmButtonText: '创建', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const { data } = await api.post(`/check-records/${result.value.recordId}/convert-to-collection`)
    const unmatched = data.data.unmatched || []
    ElMessage.success(
      `已创建收集任务「${data.data.name}」（${data.data.total} 人）` +
        (unmatched.length ? `，${unmatched.length} 人未能匹配名单` : '')
    )
    router.push('/collections')
  } catch {
    /* 拦截器已提示 */
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
  api
    .get(`/check-records/${id}/export?format=${format}`, {
      responseType: 'blob'
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

<style scoped>
.parse-layout {
  display: flex;
  gap: 14px;
  min-height: 300px;
}

.parse-left {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--c-line);
  border-radius: 12px;
  padding: 10px;
  max-height: 420px;
  overflow: auto;
}

.parse-img {
  width: 100%;
  border-radius: 8px;
}

.ocr-raw {
  white-space: pre-wrap;
  font-size: 12px;
  color: var(--c-ink-soft);
  background: #fbf8f0;
  border-radius: 8px;
  padding: 10px;
  max-height: 380px;
  overflow: auto;
}

.parse-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.parse-rows {
  flex: 1;
  overflow: auto;
  max-height: 380px;
  border: 1px solid var(--c-line);
  border-radius: 12px;
  padding: 8px;
}

.parse-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 2px;
}

.parse-no {
  font-size: 12px;
  flex-shrink: 0;
  width: 70px;
}
</style>
