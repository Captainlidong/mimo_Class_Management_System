<template>
  <div class="page-card">
    <div class="toolbar">
      <h2 class="page-title" style="margin: 0">📣 群发素材</h2>
      <el-input v-model="q" placeholder="搜标题/来源/内容" style="width: 200px" clearable @change="load" />
      <el-select v-model="category" clearable placeholder="全部类型" style="width: 140px" @change="load">
        <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 130px" @change="load">
        <el-option label="草稿" value="draft" />
        <el-option label="已发送" value="sent" />
      </el-select>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">新建素材</el-button>
    </div>
    <p class="muted">
      导员要求转发的通知、文件、文案话段都存这里：左边存导员原件，右边写你改好的版本，改前改后有据可查。
    </p>

    <el-table :data="items" border stripe>
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">
          <span v-if="row.pinned" title="已置顶" style="margin-right: 4px">📌</span>
          <span style="font-weight: 600">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="categoryTag(row.category)" size="small" effect="plain">{{ categoryLabel(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="来源" width="120">
        <template #default="{ row }">{{ row.source || '—' }}</template>
      </el-table-column>
      <el-table-column label="附件" width="70" align="center">
        <template #default="{ row }">{{ row.attachmentCount ? `📎${row.attachmentCount}` : '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="200">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'sent'" type="success" size="small">已发送 {{ formatTime(row.sentAt) }}</el-tag>
          <el-tag v-else type="info" size="small" effect="plain">草稿</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="352">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="copyEdited(row)">复制文案</el-button>
          <el-button link @click="openVersions(row)">版本({{ row.versionCount }})</el-button>
          <el-button link @click="togglePin(row)">{{ row.pinned ? '取消置顶' : '置顶' }}</el-button>
          <el-button v-if="row.status !== 'sent'" link type="success" @click="markSent(row)">已发送</el-button>
          <el-button v-else link @click="unmarkSent(row)">撤回</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑素材' : '新建素材'" width="900px" top="4vh" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="10">
            <el-form-item label="标题">
              <el-input v-model="form.title" placeholder="如：关于提交资助材料的通知" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="类型">
              <el-select v-model="form.category" style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="来源">
              <el-input v-model="form.source" placeholder="如：导员张老师 / 学院 / 自己" maxlength="64" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="导员原件">
              <el-input v-model="form.originalText" type="textarea" :rows="9" placeholder="导员/学院发来的原始内容，保持原样存档" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <span>修改稿</span>
              </template>
              <el-input v-model="form.editedText" type="textarea" :rows="9" placeholder="你实际发到班级群的版本" />
              <div style="margin-top: 6px; text-align: right">
                <el-button size="small" type="primary" plain @click="copyText(form.editedText)">📋 一键复制修改稿</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="记下为什么改、有什么要注意的（如：导员要求去掉称呼）" maxlength="512" />
        </el-form-item>
      </el-form>

      <template v-if="form.id">
        <el-divider content-position="left">📎 附件（可多个，随文案一起转发）</el-divider>
        <div style="margin-bottom: 8px">
          <el-tag
            v-for="f in form.files"
            :key="f.id"
            closable
            style="margin: 0 8px 8px 0"
            @close="removeFile(f)"
          >
            <span style="cursor: pointer" @click="downloadFile(f)">📄 {{ f.fileName }}</span>
            <span style="margin-left: 6px; color: #999; font-size: 12px">{{ formatSize(f.fileSize) }}</span>
          </el-tag>
          <span v-if="!form.files.length" class="muted">暂无附件</span>
        </div>
        <input ref="attachInput" type="file" multiple style="display: none" @change="onAttachPicked" />
        <div
          class="attach-drop"
          :class="{ 'is-dragover': attachDragOver }"
          @dragenter.prevent="onAttachDragEnter"
          @dragover.prevent="attachDragOver = true"
          @dragleave.prevent="onAttachDragLeave"
          @drop.prevent="onAttachDrop"
          @click="attachInput && attachInput.click()"
        >
          <span>{{ attachDragOver ? '🎉 松开即可上传' : '📥 把附件拖到这里，或点击选择' }}</span>
          <div class="muted" style="font-weight: 400; margin-top: 4px">
            可一次拖入多个 · docx / pdf / xlsx / pptx / 图片 / 压缩包，单个最大 50MB
          </div>
        </div>
      </template>
      <p v-else class="muted" style="margin: 8px 0 0">保存后即可添加附件。</p>

      <template #footer>
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="versionVisible" :title="`版本历史 · ${current ? current.title : ''}`" size="45%" append-to-body>
      <p class="muted" style="margin-top: 0">每次保存修改稿时，旧稿会自动留档在这里。</p>
      <div v-for="v in versions" :key="v.id" class="version-item">
        <div class="version-head">
          <span>🕒 {{ formatTime(v.savedAt) }}</span>
          <el-button size="small" link type="primary" @click="restoreVersion(v)">恢复此版本</el-button>
        </div>
        <pre class="version-content">{{ v.content }}</pre>
      </div>
      <el-empty v-if="!versions.length" description="还没有历史版本（第一次修改保存后出现）" />
    </el-drawer>
  </div>
</template>

<script setup>
import { onDeactivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'

const categoryOptions = [
  { value: 'notice', label: '通知转发' },
  { value: 'file', label: '文件转发' },
  { value: 'copy', label: '文案话段' },
  { value: 'other', label: '其他' }
]

const items = ref([])
const q = ref('')
const category = ref(null)
const status = ref(null)
const editVisible = ref(false)
const versionVisible = ref(false)
const current = ref(null)
const versions = ref([])
const pendingFiles = ref([])

const form = reactive({
  id: null,
  title: '',
  category: 'notice',
  source: '',
  originalText: '',
  editedText: '',
  remark: '',
  files: []
})

const categoryMap = Object.fromEntries(categoryOptions.map((c) => [c.value, c.label]))

const categoryLabel = (v) => categoryMap[v] || '其他'
const categoryTag = (v) => ({ notice: 'primary', file: 'success', copy: 'warning', other: 'info' }[v] || 'info')

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

function formatSize(bytes) {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function load() {
  const params = {}
  if (q.value) params.q = q.value
  if (category.value) params.category = category.value
  if (status.value) params.status = status.value
  const { data } = await api.get('/announcements', { params })
  items.value = data.data || []
}

function openCreate() {
  Object.assign(form, {
    id: null, title: '', category: 'notice', source: '',
    originalText: '', editedText: '', remark: '', files: []
  })
  pendingFiles.value = []
  editVisible.value = true
}

async function openEdit(row) {
  const { data } = await api.get(`/announcements/${row.id}`)
  const d = data.data
  Object.assign(form, {
    id: d.id, title: d.title, category: d.category, source: d.source || '',
    originalText: d.originalText || '', editedText: d.editedText || '',
    remark: d.remark || '', files: d.files || []
  })
  pendingFiles.value = []
  editVisible.value = true
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  const body = {
    title: form.title.trim(),
    category: form.category,
    source: form.source,
    originalText: form.originalText,
    editedText: form.editedText,
    remark: form.remark
  }
  if (form.id) {
    await api.put(`/announcements/${form.id}`, body)
    ElMessage.success('已保存（旧修改稿已自动留档）')
  } else {
    const { data } = await api.post('/announcements', body)
    form.id = data.data.id
    ElMessage.success('已保存，现在可以添加附件了')
  }
  await load()
}

const attachInput = ref(null)
const attachDragOver = ref(false)
let attachDragDepth = 0

function onAttachDragEnter() {
  attachDragDepth += 1
  attachDragOver.value = true
}

function onAttachDragLeave() {
  attachDragDepth = Math.max(0, attachDragDepth - 1)
  if (attachDragDepth === 0) attachDragOver.value = false
}

function onAttachDrop(e) {
  attachDragDepth = 0
  attachDragOver.value = false
  const files = Array.from((e.dataTransfer && e.dataTransfer.files) || [])
  if (files.length) uploadAttachFiles(files)
}

function onAttachPicked(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  uploadAttachFiles(files)
}

async function uploadAttachFiles(fileList) {
  if (!fileList || !fileList.length) return
  if (!form.id) {
    ElMessage.warning('请先保存素材，再添加附件')
    return
  }
  const fd = new FormData()
  for (const f of fileList) {
    fd.append('files', f)
  }
  const password = localStorage.getItem('accessPassword') || ''
  try {
    const { data } = await api.post(`/announcements/${form.id}/files`, fd, {
      headers: { 'Content-Type': 'multipart/form-data', ...(password ? { 'X-Access-Password': password } : {}) }
    })
    form.files = data.data.files || []
    ElMessage.success(fileList.length > 1 ? `已上传 ${fileList.length} 个附件 📎` : '附件已上传 📎')
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

async function removeFile(f) {
  await ElMessageBox.confirm(`删除附件「${f.fileName}」？`, '删除附件', { type: 'warning' })
  const { data } = await api.delete(`/announcements/${form.id}/files/${f.id}`)
  form.files = data.data.files || []
  ElMessage.success('附件已删除')
  await load()
}

function downloadFile(f) {
  const password = localStorage.getItem('accessPassword') || ''
  api
    .get(`/announcements/${form.id}/files/${f.id}`, {
      responseType: 'blob',
      headers: password ? { 'X-Access-Password': password } : {}
    })
    .then((res) => {
      const blob = new Blob([res.data])
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = f.fileName
      a.click()
      URL.revokeObjectURL(a.href)
    })
}

async function copyText(text) {
  if (!text || !text.trim()) {
    ElMessage.warning('修改稿还是空的，先写点什么吧')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制，去群里粘贴吧 🎀')
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

async function copyEdited(row) {
  if (!row.hasEdited) {
    ElMessage.warning('这条素材还没有修改稿，点「编辑」补上')
    return
  }
  const { data } = await api.get(`/announcements/${row.id}`)
  await copyText(data.data.editedText)
}

async function openVersions(row) {
  const { data } = await api.get(`/announcements/${row.id}`)
  current.value = data.data
  versions.value = data.data.versions || []
  versionVisible.value = true
}

async function restoreVersion(v) {
  await ElMessageBox.confirm('用这个历史版本覆盖当前修改稿？（当前修改稿会先自动留档）', '恢复版本', { type: 'warning' })
  const { data } = await api.post(`/announcements/${current.value.id}/versions/${v.id}/restore`)
  versions.value = data.data.versions || []
  current.value = data.data
  ElMessage.success('已恢复')
  await load()
}

async function togglePin(row) {
  await api.patch(`/announcements/${row.id}/pin`, { pinned: !row.pinned })
  ElMessage.success(row.pinned ? '已取消置顶' : '已置顶')
  await load()
}

async function markSent(row) {
  await api.post(`/announcements/${row.id}/mark-sent`)
  ElMessage.success('已标记为发送完成 🌸')
  await load()
}

async function unmarkSent(row) {
  await api.post(`/announcements/${row.id}/unmark-sent`)
  ElMessage.success('已撤回为草稿')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(
    `删除素材「${row.title}」？附件和版本历史会一起删除，不可恢复。`,
    '删除素材',
    { type: 'warning' }
  )
  await api.delete(`/announcements/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)

// keep-alive 页面被切走时，挂在 body 上的弹层不会自动隐藏，这里兜底关闭
onDeactivated(() => {
  editVisible.value = false
  versionVisible.value = false
})
</script>

<style scoped>
.attach-drop {
  min-height: 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-weight: 600;
  color: var(--c-ink);
  background: #fff;
  border: 1.5px dashed #ffb6cc;
  border-radius: 14px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.attach-drop:hover {
  box-shadow: 0 8px 24px rgba(255, 107, 157, 0.12);
}

.attach-drop.is-dragover {
  border-color: var(--c-primary);
  background: linear-gradient(145deg, #ffe8f2, #e8fffb);
  box-shadow: 0 12px 32px rgba(255, 107, 157, 0.22);
  transform: scale(1.01);
}

.version-item {
  border: 1px solid var(--c-border, #f3d9e5);
  border-radius: 12px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #fff;
}

.version-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 13px;
  color: #8a6d7c;
}

.version-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  max-height: 260px;
  overflow-y: auto;
}
</style>
