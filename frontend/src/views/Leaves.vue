<template>
  <div class="page-card">
    <div class="toolbar">
      <h2 class="page-title" style="margin: 0">🌙 请假记录</h2>
      <el-input v-model="keyword" placeholder="搜姓名/学号" style="width: 160px" clearable />
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 130px" @change="load">
        <el-option label="请假中" value="active" />
        <el-option label="已销假" value="closed" />
      </el-select>
      <el-select v-model="type" clearable placeholder="全部类型" style="width: 120px" @change="load">
        <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="请假开始"
        end-placeholder="请假结束"
        style="width: 250px"
        @change="load"
      />
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">登记请假</el-button>
      <el-button @click="exportLeaves('xlsx')">导出Excel</el-button>
      <el-button @click="exportLeaves('csv')">CSV</el-button>
    </div>

    <div class="stat-row">
      <div class="stat-item"><div class="label">当前请假中</div><div class="value">{{ stats.activeCount || 0 }} 人</div></div>
      <div class="stat-item" style="background:#fefce8"><div class="label">本月请假人次</div><div class="value">{{ stats.monthCount || 0 }}</div></div>
      <div class="stat-item" style="background:#f0fdf4"><div class="label">累计记录</div><div class="value">{{ stats.total || 0 }}</div></div>
    </div>

    <el-table :data="filtered" border stripe>
      <el-table-column label="学生" width="150">
        <template #default="{ row }">
          <span style="font-weight: 600">{{ row.name }}</span>
          <span v-if="row.studentNo" class="muted" style="margin-left: 6px; font-size: 12px">{{ row.studentNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.leaveType)" size="small" effect="plain">{{ typeLabel(row.leaveType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="请假时间" min-width="230">
        <template #default="{ row }">{{ formatTime(row.startTime) }} ～ {{ formatTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'active'" type="warning" size="small">请假中</el-tag>
          <el-tag v-else type="success" size="small">已销假</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="返校时间" width="140">
        <template #default="{ row }">{{ row.returnedAt ? formatTime(row.returnedAt) : '—' }}</template>
      </el-table-column>
      <el-table-column prop="approver" label="批假人" width="100">
        <template #default="{ row }">{{ row.approver || '—' }}</template>
      </el-table-column>
      <el-table-column label="原因 / 备注" min-width="160">
        <template #default="{ row }">
          <span v-if="row.reason">{{ row.reason }}</span>
          <span v-if="row.remark" class="muted">（{{ row.remark }}）</span>
          <span v-if="!row.reason && !row.remark">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 'active'" link type="success" @click="closeRow(row)">销假</el-button>
          <el-button v-else link @click="reopenRow(row)">撤销销假</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑请假记录' : '登记请假'" width="560px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item v-if="!form.id" label="请假学生">
          <el-select v-model="form.studentIds" multiple filterable placeholder="可多选，如整宿舍集体请假" style="width: 100%">
            <el-option v-for="s in students" :key="s.id" :label="`${s.name}（${s.studentNo || '无学号'}）`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="请假学生">
          <el-input :value="form.name" disabled />
        </el-form-item>
        <el-form-item label="请假类型">
          <el-radio-group v-model="form.leaveType">
            <el-radio v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="请假时间">
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="请假原因">
          <el-input v-model="form.reason" placeholder="如：感冒发烧 / 家中有事" maxlength="512" />
        </el-form-item>
        <el-form-item label="批假人">
          <el-input v-model="form.approver" placeholder="如：导员张老师" maxlength="64" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onDeactivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'

const typeOptions = [
  { value: 'sick', label: '病假' },
  { value: 'personal', label: '事假' },
  { value: 'official', label: '公假' },
  { value: 'other', label: '其他' }
]
const typeMap = Object.fromEntries(typeOptions.map((t) => [t.value, t.label]))
const typeLabel = (v) => typeMap[v] || '其他'
const typeTag = (v) => ({ sick: 'danger', personal: 'primary', official: 'success', other: 'info' }[v] || 'info')

const records = ref([])
const students = ref([])
const stats = ref({})
const keyword = ref('')
const status = ref(null)
const type = ref(null)
const dateRange = ref(null)
const dialogVisible = ref(false)

const form = reactive({
  id: null,
  name: '',
  studentIds: [],
  leaveType: 'personal',
  range: null,
  reason: '',
  approver: '',
  remark: ''
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return records.value
  return records.value.filter(
    (r) => (r.name || '').toLowerCase().includes(kw) || (r.studentNo || '').toLowerCase().includes(kw)
  )
})

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

function toLocalInput(iso) {
  if (!iso) return null
  const d = new Date(iso)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function nowLocal() {
  return toLocalInput(new Date().toISOString()).slice(0, 16).replace('T', ' ')
}

async function load() {
  const params = {}
  if (status.value) params.status = status.value
  if (type.value) params.type = type.value
  if (dateRange.value && dateRange.value.length === 2) {
    params.from = `${dateRange.value[0]}T00:00:00`
    params.to = `${dateRange.value[1]}T23:59:59`
  }
  const { data } = await api.get('/leaves', { params })
  records.value = data.data || []
}

async function loadStats() {
  const { data } = await api.get('/leaves/stats')
  stats.value = data.data || {}
}

async function loadStudents() {
  const { data } = await api.get('/students')
  // /students 返回 { count, locked, students: [...] }，学生数组在 students 字段
  students.value = (data.data && data.data.students) || []
}

function openCreate() {
  Object.assign(form, {
    id: null, name: '', studentIds: [], leaveType: 'personal',
    range: null, reason: '', approver: '', remark: ''
  })
  dialogVisible.value = true
}

async function openEdit(row) {
  Object.assign(form, {
    id: row.id, name: row.name, studentIds: [row.studentId], leaveType: row.leaveType,
    range: [toLocalInput(row.startTime), toLocalInput(row.endTime)],
    reason: row.reason || '', approver: row.approver || '', remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function save() {
  if (form.id) {
    if (!form.range || form.range.length !== 2) {
      ElMessage.warning('请选择请假时间段')
      return
    }
    await api.patch(`/leaves/${form.id}`, {
      leaveType: form.leaveType,
      startTime: form.range[0],
      endTime: form.range[1],
      reason: form.reason,
      approver: form.approver,
      remark: form.remark
    })
    ElMessage.success('已更新')
  } else {
    if (!form.studentIds.length) {
      ElMessage.warning('请选择请假学生')
      return
    }
    if (!form.range || form.range.length !== 2) {
      ElMessage.warning('请选择请假时间段')
      return
    }
    const { data } = await api.post('/leaves', {
      studentIds: form.studentIds,
      leaveType: form.leaveType,
      startTime: form.range[0],
      endTime: form.range[1],
      reason: form.reason,
      approver: form.approver,
      remark: form.remark
    })
    ElMessage.success(`已为 ${data.data.length} 名同学登记请假 🌙`)
  }
  dialogVisible.value = false
  await Promise.all([load(), loadStats()])
}

async function closeRow(row) {
  let returnedAt = ''
  try {
    const { value } = await ElMessageBox.prompt('返校时间（留空则记为当前时间）', `为 ${row.name} 销假`, {
      confirmButtonText: '确认销假',
      cancelButtonText: '取消',
      inputValue: nowLocal()
    })
    returnedAt = (value || '').trim()
  } catch {
    return
  }
  await api.post(`/leaves/${row.id}/close`, returnedAt ? { returnedAt } : {})
  ElMessage.success(`${row.name} 已销假 🌷`)
  await Promise.all([load(), loadStats()])
}

async function reopenRow(row) {
  await ElMessageBox.confirm(`撤销 ${row.name} 的销假记录，恢复为「请假中」？`, '撤销销假', { type: 'warning' })
  await api.post(`/leaves/${row.id}/reopen`)
  ElMessage.success('已恢复为请假中')
  await Promise.all([load(), loadStats()])
}

async function remove(row) {
  await ElMessageBox.confirm(`删除 ${row.name} 的这条请假记录？`, '删除记录', { type: 'warning' })
  await api.delete(`/leaves/${row.id}`)
  ElMessage.success('已删除')
  await Promise.all([load(), loadStats()])
}

function exportLeaves(format) {
  const password = localStorage.getItem('accessPassword') || ''
  api
    .get(`/leaves/export?format=${format}`, {
      responseType: 'blob',
      headers: password ? { 'X-Access-Password': password } : {}
    })
    .then((res) => {
      const blob = new Blob([res.data])
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `请假记录.${format === 'csv' ? 'csv' : 'xlsx'}`
      a.click()
      URL.revokeObjectURL(a.href)
    })
}

onMounted(() => {
  load()
  loadStats()
  loadStudents()
})

// keep-alive 页面被切走时，挂在 body 上的弹层不会自动隐藏，这里兜底关闭
onDeactivated(() => {
  dialogVisible.value = false
})
</script>
