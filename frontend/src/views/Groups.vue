<template>
  <div class="page-card">
    <div class="toolbar">
      <h2 class="page-title" style="margin: 0">🫧 自定义分组</h2>
      <div style="flex: 1"></div>
      <el-button @click="openGroupCheck">按分组筛查历史</el-button>
      <el-button type="primary" @click="openCreate">新建分组</el-button>
    </div>
    <p class="muted">用于卫生小组、学习小组等精细化管理。可将基准名单成员划分到多个分组；核对后可按小组查看未参与人员。</p>

    <el-table :data="groups" border stripe>
      <el-table-column prop="name" label="分组名称" min-width="140" />
      <el-table-column prop="memberCount" label="人数" width="80" />
      <el-table-column label="成员" min-width="260">
        <template #default="{ row }">
          <span v-for="m in row.members" :key="m.studentId" class="name-chip">{{ m.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openGroupCheck(row)">分组筛查</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分组' : '新建分组'" width="560px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
        <el-form-item label="成员">
          <el-select v-model="form.studentIds" multiple filterable style="width: 100%" placeholder="从基准名单选择">
            <el-option v-for="s in students" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkVisible" title="按分组查看核对结果" width="640px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="选择分组">
          <el-select v-model="checkForm.groupId" style="width: 100%">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择记录">
          <el-select v-model="checkForm.recordId" style="width: 100%" filterable>
            <el-option v-for="r in records" :key="r.id" :label="`${r.title} (${formatTime(r.checkedAt)})`" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button type="primary" :loading="checkLoading" @click="runGroupCheck">查看该组结果</el-button>
      </div>
      <template v-if="checkResult">
        <div class="stat-row">
          <div class="stat-item" style="background:#ecfdf5">
            <div class="label">组内已参与</div>
            <div class="value">{{ checkResult.participatedCount }}</div>
          </div>
          <div class="stat-item" style="background:#fef2f2">
            <div class="label">组内未参与</div>
            <div class="value">{{ checkResult.absentCount }}</div>
          </div>
        </div>
        <h3>组内未参与</h3>
        <div>
          <span v-for="n in checkResult.absent" :key="'ga'+n" class="name-chip absent">{{ n }}</span>
          <span v-if="!checkResult.absent.length" class="muted">（无）</span>
        </div>
        <h3>组内已参与</h3>
        <div>
          <span v-for="n in checkResult.participated" :key="'gp'+n" class="name-chip">{{ n }}</span>
          <span v-if="!checkResult.participated.length" class="muted">（无）</span>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'

const groups = ref([])
const students = ref([])
const records = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', remark: '', studentIds: [] })
const checkVisible = ref(false)
const checkLoading = ref(false)
const checkForm = reactive({ groupId: null, recordId: null })
const checkResult = ref(null)

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN')
}

async function load() {
  const [g, s, r] = await Promise.all([
    api.get('/groups'),
    api.get('/students'),
    api.get('/check-records')
  ])
  groups.value = g.data.data || []
  students.value = s.data.data.students || []
  records.value = r.data.data || []
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.remark = ''
  form.studentIds = []
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.name = row.name
  form.remark = row.remark || ''
  form.studentIds = (row.members || []).map((m) => m.studentId)
  dialogVisible.value = true
}

function openGroupCheck(row) {
  checkResult.value = null
  checkForm.groupId = row && row.id ? row.id : (groups.value[0] && groups.value[0].id)
  checkForm.recordId = records.value[0] ? records.value[0].id : null
  checkVisible.value = true
}

async function runGroupCheck() {
  if (!checkForm.groupId || !checkForm.recordId) {
    ElMessage.warning('请选择分组和历史记录')
    return
  }
  checkLoading.value = true
  try {
    const { data } = await api.get(`/groups/${checkForm.groupId}/check-view`, {
      params: { recordId: checkForm.recordId }
    })
    checkResult.value = data.data
  } finally {
    checkLoading.value = false
  }
}

async function save() {
  const payload = { name: form.name, remark: form.remark, studentIds: form.studentIds }
  if (editingId.value) {
    await api.put(`/groups/${editingId.value}`, payload)
  } else {
    await api.post('/groups', payload)
  }
  dialogVisible.value = false
  ElMessage.success('分组已保存')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除分组「${row.name}」？`, '删除分组', { type: 'warning' })
  await api.delete(`/groups/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
