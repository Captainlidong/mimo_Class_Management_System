<template>
  <div class="page-card">
    <!-- ============ 任务列表视图 ============ -->
    <template v-if="!currentTask">
      <div class="toolbar">
        <h2 class="page-title" style="margin: 0">📥 资料收集</h2>
        <div style="flex: 1"></div>
        <el-button type="primary" @click="openCreate">新建收集任务</el-button>
      </div>
      <p class="muted">
        收培养计划、问卷、各类表格：建任务自动生成名单（初始全部未交），点一下标记已交，随时复制/导出未交名单催交。
      </p>
      <el-table :data="tasks" border stripe>
        <el-table-column label="任务名称" min-width="180">
          <template #default="{ row }"><span style="font-weight: 600">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="130">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="截止日期" width="130">
          <template #default="{ row }">
            <span v-if="row.deadline" :class="{ 'deadline-passed': isPassed(row.deadline) }">{{ formatDate(row.deadline) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="提交进度" width="130">
          <template #default="{ row }">
            <span v-if="row.total" :style="{ color: row.received >= row.total ? '#059669' : '#b45309', fontWeight: 600 }">
              已交 {{ row.received }} / {{ row.total }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTask(row)">进入</el-button>
            <el-button link type="primary" @click="openEditTask(row)">编辑</el-button>
            <el-button link type="danger" @click="removeTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- ============ 任务详情视图 ============ -->
    <template v-else>
      <div class="toolbar">
        <el-button @click="goBack">← 返回</el-button>
        <h2 class="page-title" style="margin: 0">{{ currentTask.name }}</h2>
        <span v-if="currentTask.deadline" class="muted" :class="{ 'deadline-passed': isPassed(currentTask.deadline) }">
          📅 截止：{{ formatDate(currentTask.deadline) }}{{ isPassed(currentTask.deadline) ? '（已过期）' : '' }}
        </span>
        <div style="flex: 1"></div>
        <el-button type="primary" @click="openMark">📋 粘贴名单标记</el-button>
        <el-button type="success" @click="copyUndelivered">复制未交名单</el-button>
        <el-button @click="exportTask('all')">导出全部</el-button>
        <el-button type="warning" plain @click="exportTask('undelivered')">导出未交</el-button>
        <el-button @click="openEditTask(currentTask)">编辑任务</el-button>
      </div>

      <div class="stat-row">
        <div class="stat-item"><div class="label">应交人数</div><div class="value">{{ entries.length }}</div></div>
        <div class="stat-item" style="background:#f0fdf4"><div class="label">已交</div><div class="value">{{ receivedCount }}</div></div>
        <div class="stat-item" style="background:#fff7ed"><div class="label">未交</div><div class="value">{{ entries.length - receivedCount }}</div></div>
      </div>

      <div style="margin-bottom: 10px">
        <el-switch v-model="onlyUndelivered" active-text="只看未交" />
      </div>

      <el-table :data="filteredEntries" border stripe>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }"><span style="font-weight: 600">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="130">
          <template #default="{ row }">{{ row.studentNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="提交状态" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.submitted ? 'success' : 'warning'"
              size="small"
              style="cursor: pointer"
              @click="toggleRow(row)"
            >{{ row.submitted ? '已交' : '未交' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="160">
          <template #default="{ row }">{{ row.submittedAt ? formatTime(row.submittedAt) : '—' }}</template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 新建任务 -->
    <el-dialog v-model="createVisible" title="新建收集任务" width="560px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="任务名称">
          <el-input v-model="createForm.name" placeholder="如：培养计划提交" maxlength="128" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="createForm.deadline" type="datetime" placeholder="可选" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" maxlength="512" />
        </el-form-item>
        <el-form-item label="收集名单">
          <div style="width: 100%">
            <div style="margin-bottom: 6px">
              <el-button size="small" @click="selectAllStudents">全选</el-button>
              <el-button size="small" @click="createForm.studentIds = []">清空</el-button>
              <span class="muted" style="margin-left: 8px">已选 {{ createForm.studentIds.length }} / {{ students.length }} 人（默认全班）</span>
            </div>
            <el-select v-model="createForm.studentIds" multiple filterable placeholder="不选则默认全班同学" style="width: 100%">
              <el-option v-for="s in students" :key="s.id" :label="`${s.name}（${s.studentNo || '无学号'}）`" :value="s.id" />
            </el-select>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createTask">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑任务 -->
    <el-dialog v-model="editVisible" title="编辑任务" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="任务名称">
          <el-input v-model="editForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="editForm.deadline" type="datetime" placeholder="可选" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 粘贴名单批量标记 -->
    <el-dialog v-model="markVisible" title="粘贴名单批量标记已交" width="560px" append-to-body>
      <p class="muted" style="margin-top: 0">
        把<strong>已交同学</strong>的名单粘贴到下面（一行一个，或用逗号/顿号/空格分隔），系统自动匹配标记。
      </p>
      <el-input v-model="markText" type="textarea" :rows="6" placeholder="例如：&#10;绳涵戈&#10;孟庆成、徐昊&#10;温涛 2112616159" />
      <div v-if="markResult" style="margin-top: 10px">
        <el-alert type="success" :closable="false" :title="`已标记 ${markResult.marked.length} 人`" />
        <el-alert
          v-if="markResult.notFound.length"
          type="warning"
          :closable="false"
          style="margin-top: 8px"
          :title="`以下 ${markResult.notFound.length} 个名字未匹配到本任务名单：${markResult.notFound.join('、')}`"
        />
      </div>
      <template #footer>
        <el-button @click="markVisible = false">关闭</el-button>
        <el-button type="primary" @click="markBatch">解析并标记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onDeactivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'

const tasks = ref([])
const currentTask = ref(null)
const entries = ref([])
const students = ref([])
const onlyUndelivered = ref(false)

const createVisible = ref(false)
const createForm = reactive({ name: '', remark: '', deadline: null, studentIds: [] })
const editVisible = ref(false)
const editForm = reactive({ id: null, name: '', remark: '', deadline: null })
const markVisible = ref(false)
const markText = ref('')
const markResult = ref(null)

const receivedCount = computed(() => entries.value.filter((e) => e.submitted).length)
const filteredEntries = computed(() =>
  onlyUndelivered.value ? entries.value.filter((e) => !e.submitted) : entries.value
)

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('zh-CN')
}

function isPassed(iso) {
  return iso && new Date(iso).getTime() < Date.now()
}

function toLocalInput(iso) {
  if (!iso) return null
  const d = new Date(iso)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function load() {
  const { data } = await api.get('/collections/tasks')
  tasks.value = data.data || []
}

async function openTask(row) {
  const { data } = await api.get(`/collections/tasks/${row.id}`)
  currentTask.value = data.data
  entries.value = data.data.entries || []
}

async function goBack() {
  currentTask.value = null
  await load()
}

async function loadStudents() {
  const { data } = await api.get('/students')
  students.value = (data.data && data.data.students) || []
}

function openCreate() {
  createForm.name = ''
  createForm.remark = ''
  createForm.deadline = null
  createForm.studentIds = []
  loadStudents().then(() => {
    createForm.studentIds = students.value.map((s) => s.id)
  })
  createVisible.value = true
}

async function createTask() {
  if (!createForm.name.trim()) {
    ElMessage.warning('任务名称不能为空')
    return
  }
  const { data } = await api.post('/collections/tasks', {
    name: createForm.name.trim(),
    remark: createForm.remark,
    deadline: createForm.deadline,
    studentIds: createForm.studentIds
  })
  createVisible.value = false
  ElMessage.success(`任务已创建，共 ${data.data.total} 人待交`)
  await openTask(data.data)
}

function openEditTask(row) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.remark = row.remark || ''
  editForm.deadline = toLocalInput(row.deadline)
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.name.trim()) {
    ElMessage.warning('任务名称不能为空')
    return
  }
  await api.patch(`/collections/tasks/${editForm.id}`, {
    name: editForm.name.trim(),
    remark: editForm.remark,
    deadline: editForm.deadline
  })
  editVisible.value = false
  ElMessage.success('任务已更新')
  if (currentTask.value && currentTask.value.id === editForm.id) {
    await openTask(currentTask.value)
  }
  await load()
}

async function removeTask(row) {
  await ElMessageBox.confirm(
    `删除任务「${row.name}」？名单和标记记录会一起删除，不可恢复。`,
    '删除任务',
    { type: 'warning' }
  )
  await api.delete(`/collections/tasks/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function toggleRow(row) {
  await api.patch(`/collections/entries/${row.id}`, { submitted: !row.submitted })
  ElMessage.success(row.submitted ? `已标记 ${row.name} 未交` : `${row.name} 已交 ✓`)
  await openTask(currentTask.value)
}

function openMark() {
  markText.value = ''
  markResult.value = null
  markVisible.value = true
}

async function markBatch() {
  if (!markText.value.trim()) {
    ElMessage.warning('请先粘贴名单')
    return
  }
  const { data } = await api.post(`/collections/tasks/${currentTask.value.id}/mark`, {
    text: markText.value,
    submitted: true
  })
  markResult.value = data.data
  await openTask(currentTask.value)
  await load()
}

async function copyUndelivered() {
  const names = entries.value.filter((e) => !e.submitted).map((e) => e.name)
  if (!names.length) {
    ElMessage.success('全部同学都已交齐 🎉')
    return
  }
  const text = `以下同学还未提交「${currentTask.value.name}」：${names.join('、')}，请尽快交给班长，谢谢配合~`
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`已复制 ${names.length} 位未交同学，去群里催交吧 📋`)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function exportTask(scope) {
  const password = localStorage.getItem('accessPassword') || ''
  api
    .get(`/collections/tasks/${currentTask.value.id}/export?format=xlsx&scope=${scope}`, {
      responseType: 'blob',
      headers: password ? { 'X-Access-Password': password } : {}
    })
    .then((res) => {
      const blob = new Blob([res.data])
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${currentTask.value.name}${scope === 'undelivered' ? '_未交名单' : '_提交情况'}.xlsx`
      a.click()
      URL.revokeObjectURL(a.href)
    })
}

onMounted(load)

onDeactivated(() => {
  createVisible.value = false
  editVisible.value = false
  markVisible.value = false
})
</script>

<style scoped>
.deadline-passed {
  color: #dc2626;
  font-weight: 600;
}
</style>
