<template>
  <div>
    <div class="toolbar">
      <h2 class="page-title" style="margin: 0">系统设置</h2>
      <span class="muted" style="margin-left: auto">喵班 v{{ version }} · 班级事务管理系统</span>
    </div>

    <!-- 数据备份 -->
    <div class="page-card" style="margin-bottom: 14px">
      <h3>学期管理</h3>
      <div class="info-grid" style="margin-top: 10px">
        <div class="info-item">
          <div class="muted">当前学期</div>
          <div class="info-value">{{ term.viewName || '—' }}{{ term.browsingHistory ? '（历史学期 · 只读浏览）' : '' }}</div>
        </div>
        <div class="info-item">
          <div class="muted">学期总数</div>
          <div class="info-value">{{ term.terms.length }}</div>
        </div>
      </div>
      <div style="margin-top: 12px">
        <el-button type="primary" @click="openTermCreate"><DesktopIcon name="plus" :size="16" />&nbsp;新建学期并换届</el-button>
        <span class="muted" style="margin-left: 10px">换届后旧学期归档只读、新学期从空白名单开始；建议先导出一份备份。</span>
      </div>
      <el-table v-if="term.terms.length" :data="term.terms" size="small" style="margin-top: 12px">
        <el-table-column prop="name" label="学期" min-width="200" />
        <el-table-column label="起止" width="230">
          <template #default="{ row }">{{ row.startDate || '—' }} ~ {{ row.endDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.id === term.activeTermId" type="success" size="small">当前</el-tag>
            <el-tag v-else type="info" size="small">已归档</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="row.id !== term.viewTermId" link type="primary" @click="switchTerm(row)">切换浏览</el-button>
            <span v-else class="muted">浏览中</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card" style="margin-bottom: 14px">
      <h3>数据备份与恢复</h3>
      <p class="muted" style="margin: 4px 0 12px">
        备份导出包含名单、筛查历史、分组、群发素材、请假、奖学金、收集任务等全部数据（JSON）。
        恢复会<b style="color: var(--c-danger)">覆盖当前全部数据</b>，请务必先导出一份最新备份再操作。
      </p>
      <div class="backup-actions">
        <el-button type="primary" :loading="exporting" @click="exportBackup">
          <DesktopIcon name="download" :size="16" />&nbsp;导出备份
        </el-button>
        <el-button :loading="importing" @click="pickImport">
          <DesktopIcon name="upload" :size="16" />&nbsp;从备份恢复
        </el-button>
        <input
          ref="fileInput"
          type="file"
          accept=".json,application/json"
          style="display: none"
          @change="onImportFile"
        />
        <span v-if="lastExportAt" class="muted">上次导出：{{ lastExportAt }}</span>
      </div>

      <el-alert
        v-if="restorePreview"
        type="warning"
        :closable="false"
        style="margin-top: 12px"
      >
        <template #title>
          已读取备份文件（导出时间 {{ restorePreview.exportedAt || '未知' }}），将覆盖写入以下数据：
        </template>
        <div class="restore-counts">
          <span v-for="item in restorePreview.items" :key="item.key" class="restore-chip">
            {{ item.label }} <b>{{ item.count }}</b>
          </span>
        </div>
        <div style="margin-top: 10px">
          <el-button type="danger" :loading="importing" @click="doRestore">确认恢复（覆盖现有数据）</el-button>
          <el-button @click="restorePreview = null">取消</el-button>
        </div>
      </el-alert>
    </div>

    <!-- 运行状态 -->
    <div class="page-card" style="margin-bottom: 14px">
      <h3>运行状态</h3>
      <div class="info-grid">
        <div class="info-item">
          <div class="muted">后端服务</div>
          <div class="info-value">
            <span :class="health.up ? 'ok-text' : 'bad-text'">{{ health.up ? '运行中' : '连接失败' }}</span>
          </div>
        </div>
        <div class="info-item">
          <div class="muted">登录保护</div>
          <div class="info-value">{{ health.passwordRequired ? '已启用（账号 + 密码 + 验证码）' : '未启用（仅本机使用）' }}</div>
        </div>
        <div class="info-item">
          <div class="muted">名单状态</div>
          <div class="info-value">{{ health.locked ? '已锁定' : '未锁定' }} · {{ health.students ?? '—' }} 人</div>
        </div>
        <div class="info-item">
          <div class="muted">登录会话</div>
          <div class="info-value">
            <template v-if="sessionExpiresAt">7 天滑动免登录 · 至 {{ sessionExpiresAt }}</template>
            <template v-else>—</template>
          </div>
        </div>
      </div>
    </div>

    <!-- 关于 -->
    <div class="page-card">
      <h3>关于喵班</h3>
      <p class="muted" style="margin: 6px 0 0">
        喵班 · 班级事务管理系统 —— 面向班长和班委的名单核对、资料收集、请假登记、通知素材、待办和奖学金事务工作台。
        桌面端与移动端（H5 / APP）数据互通，同一账号登录。
      </p>
    </div>
    <el-dialog v-model="termDialogVisible" title="新建学期并换届" width="460px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="学期名称" required>
          <el-input v-model="termForm.name" maxlength="64" placeholder="如：2026-2027 学年第一学期" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="termForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="可选" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="termForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="termDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="termCreating" @click="submitTermCreate">创建并换届</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import DesktopIcon from '../components/DesktopIcon.vue'
import { useTermStore } from '../stores/term'

const version = '1.2.0'
const exporting = ref(false)
const importing = ref(false)
const fileInput = ref(null)
const restorePreview = ref(null)
const lastExportAt = ref(localStorage.getItem('mb_last_backup_at') || '')
const health = ref({ up: false, passwordRequired: false, students: null, locked: false })
const sessionExpiresAt = ref('')
const term = useTermStore()
const termDialogVisible = ref(false)
const termCreating = ref(false)
const termForm = reactive({ name: '', startDate: null, endDate: null })

const KEY_LABELS = {
  students: '班级名单',
  groups: '自定义分组',
  groupMembers: '分组成员',
  tasks: '长期任务',
  checkRecords: '筛查历史',
  announcements: '群发素材',
  announcementVersions: '素材版本',
  announcementFiles: '素材附件',
  leaveRecords: '请假记录',
  scholarshipBatches: '奖学金批次',
  scholarshipAwards: '获奖记录',
  collectionTasks: '收集任务',
  collectionEntries: '收集条目'
}

function fmtLocal(value) {
  const d = value instanceof Date ? value : new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadHealth() {
  try {
    const { data } = await api.get('/health')
    health.value.up = true
    health.value.passwordRequired = !!data.data?.passwordRequired
    health.value.students = data.data?.students
  } catch {
    health.value.up = false
  }
  try {
    const { data } = await api.get('/students')
    health.value.locked = !!data.data?.locked
    if (data.data?.count != null) health.value.students = data.data.count
  } catch {
    /* 未登录时静默，锁定状态留空 */
  }
  try {
    const { data } = await api.get('/auth/status')
    if (data.data?.sessionExpiresAt) {
      sessionExpiresAt.value = fmtLocal(data.data.sessionExpiresAt)
    } else {
      sessionExpiresAt.value = ''
    }
  } catch {
    sessionExpiresAt.value = ''
  }
}

function openTermCreate() {
  termForm.name = ''
  termForm.startDate = null
  termForm.endDate = null
  termDialogVisible.value = true
}

async function submitTermCreate() {
  if (!termForm.name.trim()) {
    ElMessage.warning('学期名称不能为空')
    return
  }
  try {
    await ElMessageBox.confirm(
      '换届将把当前学期归档（变为只读历史），新学期从空白名单开始。此操作建议先导出备份。确定继续吗？',
      '换届确认',
      { type: 'warning', confirmButtonText: '创建并换届', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  termCreating.value = true
  try {
    await term.createAndSwitch({
      name: termForm.name.trim(),
      startDate: termForm.startDate,
      endDate: termForm.endDate
    })
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    termCreating.value = false
  }
}

async function switchTerm(row) {
  await term.switchTo(row.id)
}

async function exportBackup() {
  exporting.value = true
  try {
    const res = await api.get('/backup/export', { responseType: 'blob', timeout: 120000 })
    const blob = new Blob([res.data], { type: 'application/json' })
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const name = `backup_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}.json`
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = name
    a.click()
    URL.revokeObjectURL(a.href)
    lastExportAt.value = fmtLocal(now)
    localStorage.setItem('mb_last_backup_at', lastExportAt.value)
    ElMessage.success('备份已导出，请妥善保存 JSON 文件')
  } catch {
    // 拦截器已统一提示
  } finally {
    exporting.value = false
  }
}

function pickImport() {
  fileInput.value?.click()
}

async function onImportFile(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  let parsed
  try {
    parsed = JSON.parse(await file.text())
  } catch {
    ElMessage.error('文件不是有效的 JSON，无法恢复')
    return
  }
  const items = Object.keys(KEY_LABELS)
    .filter((key) => Array.isArray(parsed[key]))
    .map((key) => ({ key, label: KEY_LABELS[key], count: parsed[key].length }))
  if (!items.length) {
    ElMessage.error('备份文件里没有可恢复的数据（缺少数据数组字段）')
    return
  }
  restorePreview.value = { exportedAt: parsed.exportedAt, items, raw: parsed }
}

async function doRestore() {
  if (!restorePreview.value) return
  try {
    await ElMessageBox.confirm(
      '恢复将清空当前全部数据并写入备份内容，此操作不可撤销。确定继续吗？',
      '高风险操作确认',
      { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  importing.value = true
  try {
    const { data } = await api.post('/backup/import', restorePreview.value.raw, {
      headers: { 'Content-Type': 'application/json' },
      timeout: 120000
    })
    const counts = data.data || data
    ElMessage.success('恢复完成，页面数据将以恢复后的内容为准，建议刷新一次')
    restorePreview.value = null
    loadHealth()
    return counts
  } catch {
    // 拦截器已统一提示
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  loadHealth()
  term.refresh()
})
onActivated(() => {
  loadHealth()
  term.refresh()
})
</script>

<style scoped>
.backup-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.restore-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.restore-chip {
  background: #fff;
  border: 1px solid var(--c-line);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--c-ink);
}

.restore-chip b {
  color: var(--c-primary-deep);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.info-item {
  background: #fbf8f0;
  border: 1px solid var(--c-line);
  border-radius: 14px;
  padding: 12px 14px;
}

.info-value {
  font-weight: 700;
  margin-top: 4px;
  color: var(--c-ink);
}

.ok-text {
  color: var(--c-ok);
}

.bad-text {
  color: var(--c-danger);
}
</style>
