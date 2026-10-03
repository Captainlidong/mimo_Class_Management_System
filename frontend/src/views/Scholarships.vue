<template>
  <div class="page-card">
    <!-- ============ 批次列表视图 ============ -->
    <template v-if="!currentBatch">
      <div class="toolbar">
        <h2 class="page-title" style="margin: 0">🏆 奖学金统计</h2>
        <div style="flex: 1"></div>
        <el-button type="primary" @click="openCreateBatch">新建批次</el-button>
      </div>
      <p class="muted">
        老师发的获奖名单是全院 Excel？直接上传，系统自动认出本班获奖同学和等级；按批次管理，并跟踪每人的申请表收取状态。
      </p>
      <el-table :data="batches" border stripe>
        <el-table-column label="批次名称" min-width="200">
          <template #default="{ row }"><span style="font-weight: 600">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column prop="awardCount" label="获奖人数" width="90" align="center" />
        <el-table-column label="等级分布" min-width="220">
          <template #default="{ row }">
            <el-tag
              v-for="lv in (row.levels || []).slice(0, 4)"
              :key="lv.name"
              size="small"
              effect="plain"
              style="margin: 0 6px 4px 0"
            >{{ lv.name }} ×{{ lv.count }}</el-tag>
            <span v-if="!row.awardCount" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="收表进度" width="130">
          <template #default="{ row }">
            <span v-if="row.awardCount" :style="{ color: row.receivedCount >= row.awardCount ? '#059669' : '#b45309', fontWeight: 600 }">
              已交 {{ row.receivedCount }} / {{ row.awardCount }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="openBatch(row)">进入</el-button>
            <el-button link type="danger" @click="removeBatch(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- ============ 批次详情视图 ============ -->
    <template v-else>
      <div class="toolbar">
        <el-button @click="currentBatch = null">← 返回</el-button>
        <h2 class="page-title" style="margin: 0">{{ currentBatch.name }}</h2>
        <div style="flex: 1"></div>
        <el-button type="primary" @click="uploadVisible = true">📤 上传名单识别</el-button>
        <el-button @click="openManualAdd">＋ 手动添加</el-button>
        <el-button type="success" @click="copyUndelivered">📋 复制未交名单</el-button>
        <el-button @click="exportBatch('xlsx')">导出Excel</el-button>
      </div>

      <div class="stat-row">
        <div class="stat-item"><div class="label">获奖人数</div><div class="value">{{ awards.length }}</div></div>
        <div class="stat-item" style="background:#f0fdf4"><div class="label">已交表</div><div class="value">{{ receivedCount }}</div></div>
        <div class="stat-item" style="background:#fff7ed"><div class="label">未交表</div><div class="value">{{ awards.length - receivedCount }}</div></div>
        <div class="stat-item" style="background:#fefce8"><div class="label">金额合计</div><div class="value">{{ amountSum ? amountSum + ' 元' : '—' }}</div></div>
      </div>
      <div v-if="currentBatch.levels && currentBatch.levels.length" style="margin-bottom: 12px">
        <el-tag
          v-for="lv in currentBatch.levels"
          :key="lv.name"
          effect="plain"
          style="margin: 0 8px 6px 0"
        >{{ lv.name }} ×{{ lv.count }}</el-tag>
      </div>

      <el-table :data="awards" border stripe>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }"><span style="font-weight: 600">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="120">
          <template #default="{ row }">{{ row.studentNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="awardName" label="奖学金" min-width="150" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">{{ row.amount != null ? row.amount + ' 元' : '—' }}</template>
        </el-table-column>
        <el-table-column label="申请表" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.formReceived ? 'success' : 'warning'"
              size="small"
              style="cursor: pointer"
              @click="toggleReceived(row)"
            >{{ row.formReceived ? '已交表' : '未交表' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交表时间" width="150">
          <template #default="{ row }">{{ row.receivedAt ? formatTime(row.receivedAt) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditAward(row)">编辑</el-button>
            <el-button link type="danger" @click="removeAward(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 新建批次 -->
    <el-dialog v-model="batchDialogVisible" title="新建批次" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="批次名称">
          <el-input v-model="batchForm.name" placeholder="如：2025-2026学年秋季奖学金" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remark" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createBatch">创建</el-button>
      </template>
    </el-dialog>

    <!-- 上传名单识别 -->
    <el-dialog v-model="uploadVisible" title="上传获奖名单识别" width="640px" append-to-body :close-on-click-modal="false">
      <input ref="fileInput" type="file" accept=".xlsx,.xls" style="display: none" @change="onFileChange" />
      <div
        class="attach-drop"
        :class="{ 'is-dragover': uploadDragOver }"
        @dragenter.prevent="uploadDragOver = true"
        @dragover.prevent="uploadDragOver = true"
        @dragleave.prevent="uploadDragOver = false"
        @drop.prevent="onDrop"
        @click="fileInput && fileInput.click()"
      >
        <span>{{ uploadDragOver ? '🎉 松开即可识别' : '📥 把全院获奖名单 Excel 拖到这里，或点击选择' }}</span>
        <div class="muted" style="font-weight: 400; margin-top: 4px">
          支持 .xlsx / .xls · 列顺序不限 · 系统自动按学号/姓名认出本班同学
        </div>
      </div>
      <p class="muted" style="margin: 10px 0 0">
        识别后先出预览让你确认，不会直接入库；非本班同学会被自动过滤。
      </p>
    </el-dialog>

    <!-- 手动列映射兜底 -->
    <el-dialog v-model="mappingVisible" title="表头识别失败，请手动指定列" width="720px" append-to-body :close-on-click-modal="false">
      <p class="muted" style="margin-top: 0">
        没找到含"姓名"的表头。下面是文件前几行的原始内容，请填写各列的列号（从 0 开始，不确定的填 -1 跳过）：
      </p>
      <el-table :data="parseResult && parseResult.samples" border size="small" max-height="240">
        <el-table-column
          v-for="c in sampleColCount"
          :key="c"
          :label="'第' + (c - 1) + '列'"
          min-width="90"
        >
          <template #default="{ row }">{{ row[c - 1] || '' }}</template>
        </el-table-column>
      </el-table>
      <el-form label-width="110px" style="margin-top: 12px">
        <el-form-item label="表头所在行">
          <el-input-number v-model="manualMap.headerRow" :min="-1" size="small" />
          <span class="muted" style="margin-left: 8px">无表头文件请填 -1（首行即数据）</span>
        </el-form-item>
        <el-form-item label="姓名列号">
          <el-input-number v-model="manualMap.nameCol" :min="0" size="small" />
        </el-form-item>
        <el-form-item label="学号列号">
          <el-input-number v-model="manualMap.studentNoCol" :min="-1" size="small" />
        </el-form-item>
        <el-form-item label="奖学金列号">
          <el-input-number v-model="manualMap.levelCol" :min="-1" size="small" />
        </el-form-item>
        <el-form-item label="金额列号">
          <el-input-number v-model="manualMap.amountCol" :min="-1" size="small" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mappingVisible = false">取消</el-button>
        <el-button type="primary" @click="reparseWithMapping">重新识别</el-button>
      </template>
    </el-dialog>

    <!-- 识别预览确认 -->
    <el-dialog v-model="previewVisible" title="识别预览（确认后才入库）" width="860px" top="4vh" append-to-body :close-on-click-modal="false">
      <div class="stat-row" style="margin-bottom: 10px">
        <div class="stat-item"><div class="label">全院数据行</div><div class="value">{{ parseResult ? parseResult.totalRows : 0 }}</div></div>
        <div class="stat-item" style="background:#f0fdf4"><div class="label">本班命中</div><div class="value">{{ checkedCount }}</div></div>
        <div class="stat-item" style="background:#fff7ed"><div class="label">未匹配/同名</div><div class="value">{{ (parseResult && parseResult.others ? parseResult.others.length : 0) }}</div></div>
      </div>

      <el-table :data="previewRows" border size="small" max-height="360">
        <el-table-column width="46" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.checked" :disabled="row.duplicate" />
          </template>
        </el-table-column>
        <el-table-column label="姓名" width="90">
          <template #default="{ row }">{{ row.studentName }}</template>
        </el-table-column>
        <el-table-column label="学号" width="115">
          <template #default="{ row }">{{ row.studentStudentNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="奖学金名称/等级" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.awardName" size="small" maxlength="128" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <el-input v-model="row.amountText" size="small" placeholder="—" />
          </template>
        </el-table-column>
        <el-table-column label="匹配方式" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.matchedBy }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.duplicate" type="info" size="small">批次内已存在</el-tag>
            <el-tag v-else type="success" size="small">可导入</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-collapse v-if="parseResult && parseResult.others && parseResult.others.length" style="margin-top: 10px">
        <el-collapse-item :title="`未匹配/同名等 ${parseResult.others.length} 行（点击展开）`">
          <el-table :data="parseResult.others" border size="small" max-height="200">
            <el-table-column prop="name" label="姓名" width="100" />
            <el-table-column prop="studentNo" label="学号" width="120">
              <template #default="{ row }">{{ row.studentNo || '—' }}</template>
            </el-table-column>
            <el-table-column prop="awardName" label="奖学金" min-width="140" />
            <el-table-column prop="reason" label="原因" min-width="200" />
          </el-table>
        </el-collapse-item>
      </el-collapse>

      <template #footer>
        <el-button @click="previewVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!checkedCount" @click="confirmImport">
          确认导入 {{ checkedCount }} 人
        </el-button>
      </template>
    </el-dialog>

    <!-- 手动添加 -->
    <el-dialog v-model="manualVisible" title="手动添加获奖记录" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="获奖同学">
          <el-select v-model="manualForm.studentId" filterable placeholder="输入姓名或学号搜索" style="width: 100%">
            <el-option v-for="s in students" :key="s.id" :label="`${s.name}（${s.studentNo || '无学号'}）`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="奖学金名称">
          <el-input v-model="manualForm.awardName" placeholder="如：国家励志奖学金 / 一等" maxlength="128" />
        </el-form-item>
        <el-form-item label="金额（元）">
          <el-input v-model="manualForm.amount" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="manualForm.remark" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" @click="saveManualAdd">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑获奖记录 -->
    <el-dialog v-model="editVisible" title="编辑获奖记录" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="获奖同学">
          <el-input :value="editForm.name" disabled />
        </el-form-item>
        <el-form-item label="奖学金名称">
          <el-input v-model="editForm.awardName" maxlength="128" />
        </el-form-item>
        <el-form-item label="金额（元）">
          <el-input v-model="editForm.amount" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="512" />
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
import { computed, onDeactivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'

const batches = ref([])
const currentBatch = ref(null)
const awards = ref([])
const students = ref([])
const batchDialogVisible = ref(false)
const batchForm = reactive({ name: '', remark: '' })

const uploadVisible = ref(false)
const uploadDragOver = ref(false)
const fileInput = ref(null)
const parseResult = ref(null)
const previewVisible = ref(false)
const previewRows = ref([])
const mappingVisible = ref(false)
const manualMap = reactive({ headerRow: 0, nameCol: 0, studentNoCol: -1, levelCol: -1, amountCol: -1 })

const manualVisible = ref(false)
const manualForm = reactive({ studentId: null, awardName: '', amount: '', remark: '' })
const editVisible = ref(false)
const editForm = reactive({ id: null, name: '', awardName: '', amount: '', remark: '' })

const receivedCount = computed(() => awards.value.filter((a) => a.formReceived).length)
const amountSum = computed(() => {
  const sum = awards.value.reduce((acc, a) => acc + (a.amount != null ? Number(a.amount) : 0), 0)
  return sum > 0 ? Math.round(sum * 100) / 100 : 0
})
const checkedCount = computed(() => previewRows.value.filter((r) => r.checked).length)
const sampleColCount = computed(() => {
  if (!parseResult.value || !parseResult.value.samples) return 1
  return Math.max(...parseResult.value.samples.map((r) => r.length), 1)
})

function formatTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

async function load() {
  const { data } = await api.get('/scholarships/batches')
  batches.value = data.data || []
}

async function openBatch(row) {
  const { data } = await api.get(`/scholarships/batches/${row.id}`)
  currentBatch.value = data.data
  awards.value = data.data.awards || []
}

function openCreateBatch() {
  batchForm.name = ''
  batchForm.remark = ''
  batchDialogVisible.value = true
}

async function createBatch() {
  if (!batchForm.name.trim()) {
    ElMessage.warning('批次名称不能为空')
    return
  }
  const { data } = await api.post('/scholarships/batches', { ...batchForm })
  batchDialogVisible.value = false
  ElMessage.success('批次已创建，可以上传名单了')
  await openBatch(data.data)
}

async function removeBatch(row) {
  await ElMessageBox.confirm(
    `删除批次「${row.name}」？批内全部获奖记录会一起删除，不可恢复。`,
    '删除批次',
    { type: 'warning' }
  )
  await api.delete(`/scholarships/batches/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

// —— 上传识别 ——

let lastFile = null

function onFileChange(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (file) {
    lastFile = file
    parseFile(file)
  }
}

function onDrop(e) {
  uploadDragOver.value = false
  const files = (e.dataTransfer && e.dataTransfer.files) || []
  if (!files.length) return
  const file = files[0]
  if (!/\.xlsx?$/i.test(file.name)) {
    ElMessage.warning('请上传 .xlsx 或 .xls 文件')
    return
  }
  lastFile = file
  parseFile(file)
}

async function parseFile(file) {
  if (!currentBatch.value) return
  const fd = new FormData()
  fd.append('file', file)
  const password = localStorage.getItem('accessPassword') || ''
  try {
    const { data } = await api.post(
      `/scholarships/batches/${currentBatch.value.id}/import/parse`,
      fd,
      { headers: { 'Content-Type': 'multipart/form-data', ...(password ? { 'X-Access-Password': password } : {}) }, timeout: 60000 }
    )
    parseResult.value = data.data
    if (!data.data.recognized) {
      manualMap.headerRow = 0
      manualMap.nameCol = 0
      manualMap.studentNoCol = -1
      manualMap.levelCol = -1
      manualMap.amountCol = -1
      mappingVisible.value = true
      return
    }
    openPreview()
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

function openPreview() {
  const matched = (parseResult.value && parseResult.value.matched) || []
  previewRows.value = matched.map((r) => ({
    ...r,
    checked: !r.duplicate,
    amountText: r.amount != null ? String(r.amount) : ''
  }))
  uploadVisible.value = false
  previewVisible.value = true
  if (!matched.length) {
    ElMessage.warning('没有识别到本班同学，请确认名单文件是否正确')
  }
}

async function reparseWithMapping() {
  if (!currentBatch.value) return
  const fd = new FormData()
  if (lastFile) fd.append('file', lastFile)
  fd.append('headerRow', String(manualMap.headerRow))
  fd.append('nameCol', String(manualMap.nameCol))
  fd.append('studentNoCol', String(manualMap.studentNoCol))
  fd.append('levelCol', String(manualMap.levelCol))
  fd.append('amountCol', String(manualMap.amountCol))
  const password = localStorage.getItem('accessPassword') || ''
  try {
    const { data } = await api.post(
      `/scholarships/batches/${currentBatch.value.id}/import/parse`,
      fd,
      { headers: { 'Content-Type': 'multipart/form-data', ...(password ? { 'X-Access-Password': password } : {}) }, timeout: 60000 }
    )
    parseResult.value = data.data
    if (!data.data.recognized) {
      ElMessage.error('还是没识别到姓名列，请检查列号')
      return
    }
    mappingVisible.value = false
    openPreview()
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

async function confirmImport() {
  const rows = previewRows.value
    .filter((r) => r.checked)
    .map((r) => ({
      studentId: r.studentId,
      awardName: r.awardName,
      amount: r.amountText
    }))
  if (!rows.length) return
  await api.post(`/scholarships/batches/${currentBatch.value.id}/import/confirm`, {
    rows,
    sourceFile: parseResult.value && parseResult.value.fileName
  })
  previewVisible.value = false
  ElMessage.success(`已导入 ${rows.length} 条获奖记录 🏆`)
  await openBatch(currentBatch.value)
  await load()
}

// —— 收表 / 记录 ——

async function toggleReceived(row) {
  await api.post(`/scholarships/awards/${row.id}/mark-received`, { received: !row.formReceived })
  ElMessage.success(row.formReceived ? `已标记 ${row.name} 未交表` : `${row.name} 申请表已收到 🌷`)
  await openBatch(currentBatch.value)
}

async function loadStudents() {
  const { data } = await api.get('/students')
  students.value = (data.data && data.data.students) || []
}

function openManualAdd() {
  manualForm.studentId = null
  manualForm.awardName = ''
  manualForm.amount = ''
  manualForm.remark = ''
  loadStudents()
  manualVisible.value = true
}

async function saveManualAdd() {
  if (!manualForm.studentId) {
    ElMessage.warning('请选择获奖同学')
    return
  }
  await api.post(`/scholarships/batches/${currentBatch.value.id}/awards`, {
    studentId: manualForm.studentId,
    awardName: manualForm.awardName,
    amount: manualForm.amount,
    remark: manualForm.remark
  })
  manualVisible.value = false
  ElMessage.success('已添加')
  await openBatch(currentBatch.value)
  await load()
}

function openEditAward(row) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.awardName = row.awardName || ''
  editForm.amount = row.amount != null ? String(row.amount) : ''
  editForm.remark = row.remark || ''
  editVisible.value = true
}

async function saveEdit() {
  await api.patch(`/scholarships/awards/${editForm.id}`, {
    awardName: editForm.awardName,
    amount: editForm.amount,
    remark: editForm.remark
  })
  editVisible.value = false
  ElMessage.success('已更新')
  await openBatch(currentBatch.value)
}

async function removeAward(row) {
  await ElMessageBox.confirm(`删除 ${row.name} 的获奖记录？`, '删除记录', { type: 'warning' })
  await api.delete(`/scholarships/awards/${row.id}`)
  ElMessage.success('已删除')
  await openBatch(currentBatch.value)
  await load()
}

// —— 复制 / 导出 ——

async function copyUndelivered() {
  const names = awards.value.filter((a) => !a.formReceived).map((a) => a.name)
  if (!names.length) {
    ElMessage.success('全部同学的申请表都收齐啦 🎉')
    return
  }
  const text = `以下同学还未提交${currentBatch.value.name}申请表：${names.join('、')}，请尽快交给班长，谢谢配合~`
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`已复制 ${names.length} 位未交表同学，去群里催交吧 📋`)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function exportBatch(format) {
  const password = localStorage.getItem('accessPassword') || ''
  api
    .get(`/scholarships/batches/${currentBatch.value.id}/export?format=${format}`, {
      responseType: 'blob',
      headers: password ? { 'X-Access-Password': password } : {}
    })
    .then((res) => {
      const blob = new Blob([res.data])
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${currentBatch.value.name}_获奖名单.xlsx`
      a.click()
      URL.revokeObjectURL(a.href)
    })
}

onMounted(load)

onDeactivated(() => {
  uploadVisible.value = false
  previewVisible.value = false
  mappingVisible.value = false
  manualVisible.value = false
  editVisible.value = false
  batchDialogVisible.value = false
})
</script>

<style scoped>
.attach-drop {
  min-height: 88px;
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
  padding: 12px;
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
</style>
