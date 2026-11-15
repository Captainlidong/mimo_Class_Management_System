<template>
  <div class="page-card">
    <div class="toolbar">
      <h2 class="page-title" style="margin: 0">📖 历史记录</h2>
      <el-input v-model="q" placeholder="按任务名称搜索" style="width: 220px" clearable @change="load" />
      <el-select v-model="taskId" clearable placeholder="按长期任务筛选" style="width: 200px" @change="load">
        <el-option v-for="t in tasks" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="records" border stripe>
      <el-table-column prop="title" label="任务名称" min-width="160" />
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column prop="checkedAt" label="核对时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.checkedAt) }}</template>
      </el-table-column>
      <el-table-column prop="participatedCount" label="已参与" width="90" />
      <el-table-column prop="absentCount" label="未参与" width="90" />
      <el-table-column prop="invalidCount" label="无效" width="80" />
      <el-table-column prop="duplicateCount" label="重复" width="80" />
      <el-table-column label="操作" width="330">
        <template #default="{ row }">
          <el-button link type="primary" @click="view(row)">查看</el-button>
          <el-button link type="success" @click="rerun(row)">重新筛查</el-button>
          <el-button link type="success" @click="convert(row)">转收集</el-button>
          <el-button link @click="openEdit(row)">改名/备注</el-button>
          <el-button link type="primary" @click="exportRow(row, 'xlsx')">Excel</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" :title="detail && detail.title" width="720px" append-to-body>
      <template v-if="detail">
        <div class="stat-row">
          <div class="stat-item"><div class="label">已参与</div><div class="value">{{ detail.counts.participated }}</div></div>
          <div class="stat-item" style="background:#faebe7"><div class="label">未参与</div><div class="value">{{ detail.counts.absent }}</div></div>
          <div class="stat-item" style="background:#fff7ed"><div class="label">无效</div><div class="value">{{ detail.counts.invalid }}</div></div>
        </div>
        <p v-if="detail.remark" class="muted">备注：{{ detail.remark }}</p>
        <div class="group-view-bar">
          <el-select
            v-model="groupViewId"
            clearable
            placeholder="按分组查看（可选）"
            size="small"
            style="width: 200px"
            @change="loadGroupView"
          >
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
          <span v-if="groupView" class="muted">
            分组「{{ groupView.group && groupView.group.name ? groupView.group.name : '' }}」：已参与 {{ groupView.participatedCount }} / 未参与 {{ groupView.absentCount }}
          </span>
        </div>
        <template v-if="!groupView">
          <h3>已参与</h3>
          <div><span v-for="n in detail.participated" :key="n" class="name-chip">{{ n }}</span></div>
          <h3>未参与</h3>
          <div><span v-for="n in detail.absent" :key="n" class="name-chip absent">{{ n }}</span></div>
          <h3>无效/异常</h3>
          <div><span v-for="n in detail.invalid" :key="n" class="name-chip invalid">{{ n }}</span></div>
        </template>
        <template v-else>
          <h3>该分组已参与</h3>
          <div><span v-for="n in groupView.participated" :key="n" class="name-chip">{{ n }}</span><span v-if="!groupView.participated.length" class="muted">（无）</span></div>
          <h3>该分组未参与</h3>
          <div><span v-for="n in groupView.absent" :key="n" class="name-chip absent">{{ n }}</span><span v-if="!groupView.absent.length" class="muted">（无）</span></div>
        </template>
        <div class="rerun-bar">
          <el-button type="success" plain @click="rerun({ id: detail.id, title: detail.title, taskId: detail.taskId })">重新筛查这份名单</el-button>
          <el-button type="primary" plain @click="convert(detail)">未参与转收集任务</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑记录（不改名单快照）" width="480px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="任务名称">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { useCheckStore } from '../stores/check'

const router = useRouter()
const checkStore = useCheckStore()
const records = ref([])
const tasks = ref([])
const groups = ref([])
const q = ref('')
const taskId = ref(null)
const detailVisible = ref(false)
const detail = ref(null)
const groupViewId = ref(null)
const groupView = ref(null)
const editVisible = ref(false)
const editId = ref(null)
const editForm = reactive({ title: '', remark: '' })

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN')
}

async function load() {
  const params = {}
  if (q.value) params.q = q.value
  if (taskId.value) params.taskId = taskId.value
  const { data } = await api.get('/check-records', { params })
  records.value = data.data || []
}

async function loadTasks() {
  const { data } = await api.get('/tasks')
  tasks.value = data.data || []
}

async function loadGroups() {
  const { data } = await api.get('/groups')
  groups.value = data.data || []
}

async function view(row) {
  const { data } = await api.get(`/check-records/${row.id}`)
  detail.value = data.data
  groupViewId.value = null
  groupView.value = null
  detailVisible.value = true
}

async function loadGroupView() {
  if (!groupViewId.value || !detail.value) {
    groupView.value = null
    return
  }
  const { data } = await api.get(`/groups/${groupViewId.value}/check-view`, {
    params: { recordId: detail.value.recordId }
  })
  groupView.value = data.data
}

/** 重新筛查：把这份历史记录的原始名单回填到筛查页草稿（不修改原快照）。 */
async function rerun(row) {
  try {
    const { data } = await api.get(`/check-records/${row.id}`)
    checkStore.setText(data.data.rawText || '')
    checkStore.setTitle(row.title)
    checkStore.setTaskId(row.taskId ?? null)
    checkStore.setResult(null)
  } catch {
    return
  }
  ElMessage.success('名单已回填到筛查页，可直接一键筛查')
  router.push('/check')
}

/** 未参与名单一键转成补交收集任务。 */
async function convert(detailRow) {
  if (!detailRow.counts || detailRow.counts.absent === 0) {
    ElMessage.warning('这份记录没有未参与同学，无需转收集')
    return
  }
  try {
    await ElMessageBox.confirm(
      `把 ${detailRow.counts.absent} 位未参与同学建成一个补交收集任务？`,
      '转收集任务',
      { type: 'success', confirmButtonText: '创建', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const { data } = await api.post(`/check-records/${detailRow.id}/convert-to-collection`)
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

function openEdit(row) {
  editId.value = row.id
  editForm.title = row.title
  editForm.remark = row.remark || ''
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.title.trim()) {
    ElMessage.warning('任务名称不能为空')
    return
  }
  await api.patch(`/check-records/${editId.value}`, {
    title: editForm.title.trim(),
    remark: editForm.remark
  })
  editVisible.value = false
  ElMessage.success('已更新（名单快照未改动）')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除记录', { type: 'warning' })
  await api.delete(`/check-records/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

function exportRow(row, format) {
  api
    .get(`/check-records/${row.id}/export?format=${format}`, {
      responseType: 'blob'
    })
    .then((res) => {
      const blob = new Blob([res.data])
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${row.title || '核对结果'}.${format}`
      a.click()
      URL.revokeObjectURL(a.href)
    })
}

onMounted(() => {
  load()
  loadTasks()
  loadGroups()
})
</script>

<style scoped>
.group-view-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.rerun-bar {
  margin-top: 14px;
  display: flex;
  gap: 10px;
}
</style>
