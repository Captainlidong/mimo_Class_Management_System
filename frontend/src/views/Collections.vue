<template>
  <div class="page-card">
    <!-- ============ 任务列表视图 ============ -->
    <template v-if="!currentTask">
      <div class="toolbar">
        <h2 class="page-title" style="margin: 0">📥 资料收集与分发</h2>
        <div style="flex: 1"></div>
        <el-button type="primary" @click="openCreate">新建任务</el-button>
      </div>
      <p class="muted">
        收材料（培养计划、问卷、各类表格）或发材料（通知附件、盖章资料）都在这里：建任务自动生成名单（初始全部未完成），点一下标记状态，随时复制/导出未完成名单催办。
      </p>
      <el-table :data="tasks" border stripe>
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag :type="row.kind === 'distribute' ? 'warning' : 'success'" size="small">
              {{ row.kind === 'distribute' ? '📤 分发' : '📥 收集' }}
            </el-tag>
          </template>
        </el-table-column>
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
        <el-table-column label="进度" width="130">
          <template #default="{ row }">
            <span v-if="row.total" :style="{ color: row.received >= row.total ? '#059669' : '#b45309', fontWeight: 600 }">
              {{ row.kind === 'distribute' ? '已领' : '已交' }} {{ row.received }} / {{ row.total }}
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
        <el-button @click="openAddStudents">＋ 添加人员</el-button>
        <el-button type="danger" plain @click="openBatchRemove">批量移除</el-button>
        <el-button type="success" @click="copyUndone">{{ kindText.copyBtn }}</el-button>
        <el-button @click="openExport('all')">导出全部</el-button>
        <el-button type="warning" plain @click="openExport('undelivered')">{{ kindText.exportUndone }}</el-button>
        <el-button @click="openEditTask(currentTask)">编辑任务</el-button>
      </div>

      <div class="stat-row">
        <div class="stat-item"><div class="label">{{ kindText.statTotal }}</div><div class="value">{{ entries.length }}</div></div>
        <div class="stat-item" style="background:#f0fdf4"><div class="label">{{ kindText.done }}</div><div class="value">{{ receivedCount }}</div></div>
        <div class="stat-item" style="background:#fff7ed"><div class="label">{{ kindText.undone }}</div><div class="value">{{ entries.length - receivedCount }}</div></div>
        <div class="chart-box">
          <DonutChart
            :items="donutItems"
            :center-value="completionPct"
            :center-label="`${kindText.done} ${receivedCount} / ${entries.length}`"
            :height="190"
          />
        </div>
      </div>

      <div style="margin-bottom: 10px; display: flex; align-items: center; gap: 28px; flex-wrap: wrap">
        <el-switch v-model="onlyUndone" :active-text="kindText.onlyToggle" />
        <el-switch :model-value="allDone" :active-text="kindText.markAllToggle" @change="onMarkAllChange" />
      </div>

      <el-table :data="filteredEntries" border stripe>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }"><span style="font-weight: 600">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="130">
          <template #default="{ row }">{{ row.studentNo || '—' }}</template>
        </el-table-column>
        <el-table-column :label="kindText.statusCol" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.submitted ? 'success' : 'warning'"
              size="small"
              style="cursor: pointer"
              @click="toggleRow(row)"
            >{{ row.submitted ? kindText.done : kindText.undone }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="kindText.timeCol" min-width="150">
          <template #default="{ row }">{{ row.submittedAt ? formatTime(row.submittedAt) : '—' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">
            <el-input
              v-model="row.remark"
              size="small"
              maxlength="512"
              :placeholder="remarkPlaceholder"
              @blur="saveRemark(row)"
              @keyup.enter="saveRemark(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeRow(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 新建任务 -->
    <el-dialog v-model="createVisible" title="新建任务" width="560px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="任务类型">
          <el-radio-group v-model="createForm.kind">
            <el-radio-button value="collect">📥 收集材料</el-radio-button>
            <el-radio-button value="distribute">📤 分发材料</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="createForm.name" :placeholder="createForm.kind === 'distribute' ? '如：盖章成绩单分发' : '如：培养计划提交'" maxlength="128" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="createForm.deadline" type="datetime" placeholder="可选" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" maxlength="512" />
        </el-form-item>
        <el-form-item label="名单范围">
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
        <el-form-item label="任务类型">
          <el-radio-group v-model="editForm.kind">
            <el-radio-button value="collect">📥 收集材料</el-radio-button>
            <el-radio-button value="distribute">📤 分发材料</el-radio-button>
          </el-radio-group>
        </el-form-item>
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

    <!-- 添加人员 -->
    <el-dialog v-model="addStudentsVisible" title="添加人员" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="添加同学">
          <div style="width: 100%">
            <div style="margin-bottom: 6px">
              <span class="muted">只显示还没在该任务名单里的同学，共 {{ availableStudents.length }} 人；新人员默认为{{ kindText.undone }}。</span>
            </div>
            <el-select v-model="addStudentIds" multiple filterable placeholder="搜索或选择要补充的同学" style="width: 100%">
              <el-option v-for="s in availableStudents" :key="s.id" :label="`${s.name}（${s.studentNo || '无学号'}）`" :value="s.id" />
            </el-select>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addStudentsVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAddStudents">添加</el-button>
      </template>
    </el-dialog>

    <!-- 批量移除 -->
    <el-dialog v-model="batchRemoveVisible" title="批量移除人员" width="560px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="移除同学">
          <div style="width: 100%">
            <div style="margin-bottom: 6px">
              <el-button size="small" @click="selectAllUndone">{{ kindText.selectAllUndone }}</el-button>
              <el-button size="small" @click="batchRemoveIds = []">清空</el-button>
              <span class="muted" style="margin-left: 8px">已选 {{ batchRemoveIds.length }} / {{ entries.length }} 人</span>
            </div>
            <el-select v-model="batchRemoveIds" multiple filterable placeholder="搜索或选择要移除的同学" style="width: 100%">
              <el-option v-for="e in entries" :key="e.studentId" :label="`${e.name}（${e.studentNo || '无学号'}）`" :value="e.studentId" />
            </el-select>
            <p class="muted" style="margin: 8px 0 0">移除会连同该同学的{{ kindText.done }}记录一起删除，不可恢复。</p>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchRemoveVisible = false">取消</el-button>
        <el-button type="danger" :disabled="!batchRemoveIds.length" @click="saveBatchRemove">移除</el-button>
      </template>
    </el-dialog>

    <!-- 导出选项 -->
    <el-dialog v-model="exportVisible" title="导出名单" width="540px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="导出范围">
          <el-radio-group v-model="exportForm.scope">
            <el-radio-button value="all">全部名单</el-radio-button>
            <el-radio-button value="undelivered">{{ kindText.exportUndoneScope }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="包含列">
          <div style="width: 100%">
            <el-checkbox-group v-model="exportForm.columns">
              <el-checkbox v-for="c in exportColumnOptions" :key="c.key" :value="c.key">{{ c.label }}</el-checkbox>
            </el-checkbox-group>
            <el-button link type="primary" size="small" @click="exportForm.columns = exportColumnOptions.map((c) => c.key)">恢复默认全选</el-button>
          </div>
        </el-form-item>
        <el-form-item label="统计信息">
          <div style="width: 100%">
            <el-switch v-model="exportForm.summary" />
            <span class="muted" style="margin-left: 10px">附任务名与{{ kindText.statSummary }}统计，出现在名单末尾</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!exportForm.columns.length" @click="exportTask('xlsx')">导出Excel</el-button>
        <el-button :disabled="!exportForm.columns.length" @click="exportTask('csv')">导出CSV</el-button>
      </template>
    </el-dialog>

    <!-- 粘贴名单批量标记 -->
    <el-dialog v-model="markVisible" :title="kindText.markTitle" width="560px" append-to-body>
      <p class="muted" style="margin-top: 0">
        把<strong>{{ kindText.done }}同学</strong>的名单粘贴到下面（一行一个，或用逗号/顿号/空格分隔），系统自动匹配标记。
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
import DonutChart from '../components/DonutChart.vue'

const tasks = ref([])
const currentTask = ref(null)
const entries = ref([])
const students = ref([])
const onlyUndone = ref(false)

const createVisible = ref(false)
const createForm = reactive({ kind: 'collect', name: '', remark: '', deadline: null, studentIds: [] })
const editVisible = ref(false)
const editForm = reactive({ id: null, kind: 'collect', name: '', remark: '', deadline: null })
const markVisible = ref(false)
const markText = ref('')
const markResult = ref(null)
const addStudentsVisible = ref(false)
const addStudentIds = ref([])
const batchRemoveVisible = ref(false)
const batchRemoveIds = ref([])
const exportVisible = ref(false)
const exportForm = reactive({ scope: 'all', columns: [], summary: false })

// 添加人员时只显示还没在该任务名单里的同学
const availableStudents = computed(() => {
  const used = new Set(entries.value.map((e) => e.studentId))
  return students.value.filter((s) => !used.has(s.id))
})

const receivedCount = computed(() => entries.value.filter((e) => e.submitted).length)
// 一键标记开关的状态由数据派生：全员已完成时为开
const allDone = computed(() => entries.value.length > 0 && receivedCount.value === entries.value.length)
const filteredEntries = computed(() =>
  onlyUndone.value ? entries.value.filter((e) => !e.submitted) : entries.value
)

// 详情页文案随任务类型（收集/分发）切换
const kindText = computed(() => {
  const distribute = !!(currentTask.value && currentTask.value.kind === 'distribute')
  return distribute
    ? {
        statTotal: '应发人数',
        done: '已领',
        undone: '未领',
        statusCol: '领取状态',
        timeCol: '领取时间',
        onlyToggle: '只看未领',
        copyBtn: '复制未领名单',
        exportUndone: '导出未领',
        exportSuffix: '_领取情况',
        exportUndoneSuffix: '_未领名单',
        markTitle: '粘贴名单批量标记已领',
        markAllToggle: '一键标记全部已领',
        selectAllUndone: '勾选全部未领',
        exportUndoneScope: '仅未领',
        statSummary: '应发/已领',
        copyDone: '全部同学都已领取 🎉',
        copyTemplate: (names, task) => `以下同学还未领取「${task}」：${names}，请尽快找班长领取，谢谢配合~`,
        copyTip: (n) => `已复制 ${n} 位未领同学，去群里通知大家来领吧 📋`
      }
    : {
        statTotal: '应交人数',
        done: '已交',
        undone: '未交',
        statusCol: '提交状态',
        timeCol: '提交时间',
        onlyToggle: '只看未交',
        copyBtn: '复制未交名单',
        exportUndone: '导出未交',
        exportSuffix: '_提交情况',
        exportUndoneSuffix: '_未交名单',
        markTitle: '粘贴名单批量标记已交',
        markAllToggle: '一键标记全部已交',
        selectAllUndone: '勾选全部未交',
        exportUndoneScope: '仅未交',
        statSummary: '应交/已交',
        copyDone: '全部同学都已交齐 🎉',
        copyTemplate: (names, task) => `以下同学还未提交「${task}」：${names}，请尽快交给班长，谢谢配合~`,
        copyTip: (n) => `已复制 ${n} 位未交同学，去群里催交吧 📋`
      }
})

const remarkPlaceholder = computed(() =>
  currentTask.value && currentTask.value.kind === 'distribute' ? '如：1份未盖章' : '如：少交1份'
)

// 导出对话框候选列（文案随任务类型切换）
const exportColumnOptions = computed(() => {
  const distribute = !!(currentTask.value && currentTask.value.kind === 'distribute')
  return [
    { key: 'no', label: '序号' },
    { key: 'name', label: '姓名' },
    { key: 'studentNo', label: '学号' },
    { key: 'status', label: distribute ? '领取状态' : '提交状态' },
    { key: 'time', label: distribute ? '领取时间' : '提交时间' },
    { key: 'remark', label: '备注' }
  ]
})

// 环形图数据：已完成 vs 未完成
const donutItems = computed(() => [
  { name: kindText.value.done, value: receivedCount.value, color: '#5eead4' },
  { name: kindText.value.undone, value: entries.value.length - receivedCount.value, color: '#fdba74' }
])
const completionPct = computed(() => {
  if (!entries.value.length) return '—'
  return Math.round((receivedCount.value / entries.value.length) * 100) + '%'
})

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
  entries.value = (data.data.entries || []).map((e) => ({ ...e, _savedRemark: e.remark || '' }))
  onlyUndone.value = false
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
  createForm.kind = 'collect'
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
    kind: createForm.kind,
    name: createForm.name.trim(),
    remark: createForm.remark,
    deadline: createForm.deadline,
    studentIds: createForm.studentIds
  })
  createVisible.value = false
  ElMessage.success(`任务已创建，共 ${data.data.total} 人`)
  await openTask(data.data)
}

function openEditTask(row) {
  editForm.id = row.id
  editForm.kind = row.kind || 'collect'
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
    kind: editForm.kind,
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
  const nowDone = !row.submitted
  ElMessage.success(nowDone ? `${row.name} ${kindText.value.done} ✓` : `已标记 ${row.name} ${kindText.value.undone}`)
  await openTask(currentTask.value)
}

// 备注行内编辑：失焦/回车时若有变更则静默保存
async function saveRemark(row) {
  if ((row.remark || '') === row._savedRemark) return
  try {
    const { data } = await api.patch(`/collections/entries/${row.id}`, { remark: row.remark })
    row.remark = data.data.remark || ''
    row._savedRemark = row.remark
  } catch {
    row.remark = row._savedRemark || ''
  }
}

function openMark() {
  markText.value = ''
  markResult.value = null
  markVisible.value = true
}

function openAddStudents() {
  addStudentIds.value = []
  loadStudents().then(() => {
    addStudentsVisible.value = true
  })
}

async function saveAddStudents() {
  if (!addStudentIds.value.length) {
    ElMessage.warning('请选择要添加的同学')
    return
  }
  const { data } = await api.post(`/collections/tasks/${currentTask.value.id}/students`, {
    studentIds: addStudentIds.value
  })
  addStudentsVisible.value = false
  const r = data.data
  ElMessage.success(`已添加 ${r.added} 人${r.skipped ? `，跳过已在名单的 ${r.skipped} 人` : ''}`)
  await openTask(currentTask.value)
  await load()
}

async function removeRow(row) {
  await ElMessageBox.confirm(
    `从任务名单移除 ${row.name}？其记录会一并删除。`,
    '移除人员',
    { type: 'warning' }
  )
  await api.delete(`/collections/tasks/${currentTask.value.id}/students/${row.studentId}`)
  ElMessage.success('已移除')
  await openTask(currentTask.value)
  await load()
}

// —— 批量移除 ——
function openBatchRemove() {
  batchRemoveIds.value = []
  batchRemoveVisible.value = true
}

function selectAllUndone() {
  batchRemoveIds.value = entries.value.filter((e) => !e.submitted).map((e) => e.studentId)
  if (!batchRemoveIds.value.length) {
    ElMessage.success(kindText.value.copyDone)
  }
}

async function saveBatchRemove() {
  const names = entries.value
    .filter((e) => batchRemoveIds.value.includes(e.studentId))
    .map((e) => e.name)
  await ElMessageBox.confirm(
    `将从任务中移除 ${names.length} 人：${names.slice(0, 8).join('、')}${names.length > 8 ? ' 等' : ''}？记录会一并删除，不可恢复。`,
    '批量移除',
    { type: 'warning', confirmButtonText: '移除' }
  )
  const { data } = await api.post(`/collections/tasks/${currentTask.value.id}/students/batch-remove`, {
    studentIds: batchRemoveIds.value
  })
  batchRemoveVisible.value = false
  ElMessage.success(`已移除 ${data.data.removed} 人${data.data.skipped ? `，跳过 ${data.data.skipped} 人` : ''}`)
  await openTask(currentTask.value)
  await load()
}

// —— 一键标记全部（开关状态由数据派生，切换需确认） ——
async function onMarkAllChange(target) {
  const done = kindText.value.done
  const undone = kindText.value.undone
  const confirmText = target
    ? `将把全部 ${entries.value.length} 人标记为${done}，确认？`
    : `将清空全部${done}标记（${receivedCount.value} 人回${undone}），确认？`
  try {
    await ElMessageBox.confirm(confirmText, '一键标记', { type: 'warning', confirmButtonText: '确定' })
  } catch {
    return // 取消：开关保持数据派生状态
  }
  try {
    const { data } = await api.post(`/collections/tasks/${currentTask.value.id}/mark-all`, { submitted: target })
    ElMessage.success(target
      ? `已全部标记${done}（共 ${data.data.total} 人）`
      : `已清空标记（${data.data.changed} 人回${undone}）`)
  } finally {
    await openTask(currentTask.value)
  }
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

async function copyUndone() {
  const names = entries.value.filter((e) => !e.submitted).map((e) => e.name)
  if (!names.length) {
    ElMessage.success(kindText.value.copyDone)
    return
  }
  const text = kindText.value.copyTemplate(names.join('、'), currentTask.value.name)
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(kindText.value.copyTip(names.length))
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

// —— 导出（先选范围/列/统计，再下载） ——
function openExport(scope) {
  exportForm.scope = scope
  exportForm.columns = exportColumnOptions.value.map((c) => c.key)
  exportForm.summary = false
  exportVisible.value = true
}

function exportTask(format) {
  if (!exportForm.columns.length) {
    ElMessage.warning('请至少选择一列')
    return
  }
  api
    .get(
      `/collections/tasks/${currentTask.value.id}/export?format=${format}&scope=${exportForm.scope}`
      + `&columns=${exportForm.columns.join(',')}&summary=${exportForm.summary}`,
      { responseType: 'blob' }
    )
    .then((res) => {
      const blob = new Blob([res.data])
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${currentTask.value.name}${exportForm.scope === 'undelivered' ? kindText.value.exportUndoneSuffix : kindText.value.exportSuffix}.${format === 'csv' ? 'csv' : 'xlsx'}`
      a.click()
      URL.revokeObjectURL(a.href)
      exportVisible.value = false
      ElMessage.success('已导出')
    })
}

onMounted(load)

onDeactivated(() => {
  createVisible.value = false
  editVisible.value = false
  markVisible.value = false
  addStudentsVisible.value = false
  batchRemoveVisible.value = false
  exportVisible.value = false
})
</script>

<style scoped>
.deadline-passed {
  color: #dc2626;
  font-weight: 600;
}

.chart-box {
  flex: 1;
  min-width: 260px;
  max-width: 330px;
  border: 1px solid #e8e2d4;
  border-radius: 16px;
  padding: 8px 10px 0;
  background: #fffdfa;
}
</style>
