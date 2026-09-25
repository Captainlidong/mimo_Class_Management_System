<template>
  <div class="page-card">
    <div class="toolbar">
      <h2 class="page-title" style="margin: 0">🎯 长期任务</h2>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">新建任务</el-button>
    </div>
    <p class="muted">
      长期任务用于「青年大学习、德育打卡」等按期核对工作：在名单筛查页选择任务后保存，即可在此汇总各期记录。
    </p>

    <el-table :data="tasks" border stripe>
      <el-table-column prop="name" label="任务名称" min-width="160" />
      <el-table-column prop="type" label="类型" width="120" />
      <el-table-column prop="recordCount" label="核对期数" width="100" />
      <el-table-column prop="remark" label="备注" min-width="160" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="showRecords(row)">查看各期</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建长期任务" width="480px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如：青年大学习" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="form.type" placeholder="如：学习/打卡/材料" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="create">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" :title="currentTask ? currentTask.name + ' · 各期记录' : ''" size="40%" append-to-body>
      <el-table :data="records" border>
        <el-table-column prop="title" label="期次/标题" min-width="140" />
        <el-table-column prop="absentCount" label="未参与" width="90" />
        <el-table-column prop="participatedCount" label="已参与" width="90" />
        <el-table-column prop="checkedAt" label="时间" min-width="150">
          <template #default="{ row }">{{ new Date(row.checkedAt).toLocaleString('zh-CN') }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'

const tasks = ref([])
const records = ref([])
const createVisible = ref(false)
const drawerVisible = ref(false)
const currentTask = ref(null)
const form = reactive({ name: '', type: '自定义', remark: '' })

async function load() {
  const { data } = await api.get('/tasks')
  tasks.value = data.data || []
}

function openCreate() {
  form.name = ''
  form.type = '自定义'
  form.remark = ''
  createVisible.value = true
}

async function create() {
  await api.post('/tasks', { ...form })
  createVisible.value = false
  ElMessage.success('任务已创建')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除任务「${row.name}」？历史核对记录会保留。`, '删除任务', {
    type: 'warning'
  })
  await api.delete(`/tasks/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function showRecords(row) {
  currentTask.value = row
  const { data } = await api.get(`/tasks/${row.id}/records`)
  records.value = data.data || []
  drawerVisible.value = true
}

onMounted(load)
</script>
