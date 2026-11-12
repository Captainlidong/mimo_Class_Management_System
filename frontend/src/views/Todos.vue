<template>
  <div class="page-card">
    <div class="toolbar">
      <h2 class="page-title" style="margin: 0">⏰ 待办事务</h2>
      <span class="muted">未完成 {{ undoneCount }} 件 · 已完成 {{ doneCount }} 件</span>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">＋ 新建待办</el-button>
    </div>
    <p class="muted" style="margin-top: 0">
      有期限的收齐任务、没解决不能忘的跟进事项都放这里。已过期排最前并标红，处理完点一下状态就归档沉底。
    </p>

    <el-table :data="todos" border stripe>
      <el-table-column label="状态" width="86">
        <template #default="{ row }">
          <el-tag
            :type="row.done ? 'info' : (row.overdue ? 'danger' : 'warning')"
            size="small"
            style="cursor: pointer"
            @click="toggleDone(row)"
          >{{ row.done ? '已完成' : (row.overdue ? '已过期' : '待办') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="事项" min-width="280">
        <template #default="{ row }">
          <div :style="row.done ? 'color: #9b93a9; text-decoration: line-through;' : 'font-weight: 600; color: #4a4258;'">
            {{ row.title }}
          </div>
          <div v-if="row.detail" class="muted" style="font-size: 12px; margin-top: 2px">{{ row.detail }}</div>
        </template>
      </el-table-column>
      <el-table-column label="截止时间" width="190">
        <template #default="{ row }">
          <span v-if="row.dueAt" :class="dueClass(row)">{{ dueLabel(row) }}</span>
          <span v-else class="muted">无固定期限</span>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" width="160">
        <template #default="{ row }">{{ row.done && row.doneAt ? fmt(row.doneAt) : '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑待办' : '新建待办'" width="520px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="事项" required>
          <el-input v-model="form.title" maxlength="128" placeholder="如：结课前一周收齐课程作业" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.detail" type="textarea" :rows="3" maxlength="512" placeholder="补充要求、来源、注意事项（可选）" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="form.dueAt"
            type="datetime"
            placeholder="可选：什么时候要做这件事"
            style="width: 100%"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
          <div class="muted" style="font-size: 12px; width: 100%">
            填了就会按时间提醒排序；不填则作为无期限的跟进事项（如"等通知再办"）。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{ form.id ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'

const todos = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, title: '', detail: '', dueAt: null })

const undoneCount = computed(() => todos.value.filter((t) => !t.done).length)
const doneCount = computed(() => todos.value.filter((t) => t.done).length)

function fmt(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

function isToday(iso) {
  const d = new Date(iso)
  const n = new Date()
  return d.getFullYear() === n.getFullYear() && d.getMonth() === n.getMonth() && d.getDate() === n.getDate()
}

function dueLabel(row) {
  const t = fmt(row.dueAt)
  if (row.overdue) return `已过期 · ${t}`
  if (isToday(row.dueAt)) return `今天到期 · ${t.slice(-5)}`
  return `截止 ${t}`
}

function dueClass(row) {
  if (row.overdue) return 'due-overdue'
  if (isToday(row.dueAt)) return 'due-today'
  return 'muted'
}

async function load() {
  try {
    const { data } = await api.get('/todos')
    todos.value = data.data || []
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function openCreate() {
  form.id = null
  form.title = ''
  form.detail = ''
  form.dueAt = null
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.title = row.title
  form.detail = row.detail || ''
  form.dueAt = row.dueAt ? row.dueAt.slice(0, 19) : null
  dialogVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.title.trim()) {
    ElMessage.warning('待办标题不能为空')
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      detail: form.detail,
      dueAt: form.dueAt || null
    }
    if (form.id) {
      await api.patch(`/todos/${form.id}`, payload)
      ElMessage.success('待办已更新')
    } else {
      await api.post('/todos', payload)
      ElMessage.success('待办已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function toggleDone(row) {
  try {
    const { data } = await api.patch(`/todos/${row.id}`, { done: !row.done })
    row.done = data.data.done
    row.doneAt = data.data.doneAt
    ElMessage.success(row.done ? `已完成：${row.title} ✓` : `已重新打开：${row.title}`)
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function remove(row) {
  ElMessageBox.confirm(`删除待办「${row.title}」？不可恢复。`, '删除待办', {
    type: 'warning',
    confirmButtonText: '删除'
  })
    .then(async () => {
      await api.delete(`/todos/${row.id}`)
      ElMessage.success('已删除')
      await load()
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.due-overdue {
  color: #dc2626;
  font-weight: 600;
}
.due-today {
  color: #b45309;
  font-weight: 600;
}
</style>
