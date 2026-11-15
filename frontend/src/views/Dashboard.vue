<template>
  <div>
    <!-- 欢迎区 + 快捷操作 -->
    <div class="page-card hero">
      <div class="hero-left">
        <div class="hero-greet">{{ greeting }}，{{ auth.username || '班长' }}</div>
        <div class="muted">{{ todayText }} · 今天也要好好生活喵</div>
        <div class="hero-actions">
          <el-button type="primary" @click="$router.push('/check')"><DesktopIcon name="sparkle" :size="16" />&nbsp;一键筛查</el-button>
          <el-button @click="$router.push('/collections')"><DesktopIcon name="clipboard" :size="16" />&nbsp;新建收集</el-button>
          <el-button @click="$router.push('/leaves')"><DesktopIcon name="moon" :size="16" />&nbsp;登记请假</el-button>
          <el-button @click="$router.push('/todos')"><DesktopIcon name="clock" :size="16" />&nbsp;新建待办</el-button>
        </div>
      </div>
      <div class="hero-cat" aria-hidden="true">
        <DesktopIcon name="cat" :size="84" :stroke-width="1.4" />
      </div>
    </div>

    <!-- 五张指标卡 -->
    <div class="stat-row">
      <div class="dash-card dash-todo" @click="$router.push('/todos')">
        <div class="dash-ico"><DesktopIcon name="clock" :size="22" /></div>
        <div class="dash-num">
          <span class="warn-text" v-if="data.todos?.overdue">{{ data.todos.overdue }}</span>
          <span v-else class="ok-text">0</span>
          <span class="dash-sep">/</span>{{ data.todos?.dueToday ?? 0 }}
        </div>
        <div class="dash-label">待办 · 逾期/今日到期</div>
        <div class="muted dash-sub">共 {{ data.todos?.pending ?? 0 }} 件未完成</div>
      </div>
      <div class="dash-card dash-collect" @click="$router.push('/collections')">
        <div class="dash-ico"><DesktopIcon name="clipboard" :size="22" /></div>
        <div class="dash-num">{{ data.collections?.tasks ?? 0 }}</div>
        <div class="dash-label">收集与分发 · 任务数</div>
        <div class="muted dash-sub">
          {{ data.collections?.submittedEntries ?? 0 }}/{{ data.collections?.totalEntries ?? 0 }} 条已提交
        </div>
      </div>
      <div class="dash-card dash-leave" @click="$router.push('/leaves')">
        <div class="dash-ico"><DesktopIcon name="moon" :size="22" /></div>
        <div class="dash-num">{{ data.leaves?.active ?? 0 }}</div>
        <div class="dash-label">请假中 · 人次</div>
        <div class="muted dash-sub">点击登记 / 销假</div>
      </div>
      <div class="dash-card dash-check" @click="$router.push('/history')">
        <div class="dash-ico"><DesktopIcon name="sparkle" :size="22" /></div>
        <div class="dash-num">
          {{ data.lastCheck ? data.lastCheck.absentCount : '—' }}
        </div>
        <div class="dash-label">最近筛查 · 未参与</div>
        <div class="muted dash-sub ellipsis">
          {{ data.lastCheck ? `${data.lastCheck.title} · 基准 ${data.lastCheck.baselineCount} 人 · ${fmtCheckTime(data.lastCheck.checkedAt)}` : '还没有筛查记录' }}
        </div>
      </div>
      <div class="dash-card dash-students" @click="$router.push('/students')">
        <div class="dash-ico"><DesktopIcon name="users" :size="22" /></div>
        <div class="dash-num">{{ data.students?.count ?? 0 }}</div>
        <div class="dash-label">班级人数</div>
        <div class="muted dash-sub">
          {{ data.students?.locked ? '名单已锁定' : '名单未锁定' }}
        </div>
      </div>
    </div>

    <!-- 数据洞察 -->
    <div class="page-card" style="margin-bottom: 16px">
      <h3 style="margin: 0 0 10px">数据洞察</h3>
      <el-row :gutter="12">
        <el-col :md="12" :sm="24" style="margin-bottom: 12px">
          <div class="chart-card">
            <div class="chart-title">近 8 周请假人次</div>
            <TrendChart :labels="trends.weeks" :series="leaveSeries" :height="210" />
          </div>
        </el-col>
        <el-col :md="12" :sm="24" style="margin-bottom: 12px">
          <div class="chart-card">
            <div class="chart-title">近 8 周筛查次数</div>
            <TrendChart :labels="trends.weeks" :series="checkSeries" :height="210" />
          </div>
        </el-col>
        <el-col :md="12" :sm="24" style="margin-bottom: 12px">
          <div class="chart-card">
            <div class="chart-title">请假类型分布（近 30 天）</div>
            <DonutChart :items="leavePie" :height="210" />
          </div>
        </el-col>
        <el-col :md="12" :sm="24" style="margin-bottom: 12px">
          <div class="chart-card">
            <div class="chart-title">收集任务进度</div>
            <div class="bar-list">
              <div v-for="b in trends.collectionBars" :key="b.name" class="bar-row">
                <span class="bar-name" :title="b.name">{{ b.name }}</span>
                <div class="bar-track">
                  <div class="bar-fill" :style="{ width: pct(b) + '%', background: b.kind === 'distribute' ? '#D9A441' : '#3E5C4B' }"></div>
                </div>
                <span class="bar-num">{{ b.received }}/{{ b.total }}</span>
              </div>
              <div v-if="!trends.collectionBars.length" class="muted" style="padding: 20px 0; text-align: center">
                暂无收集任务
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 工作台说明 -->
    <div class="page-card">
      <h3>工作台说明</h3>
      <p class="muted" style="margin: 6px 0 0">
        这里汇总今天最需要关注的五件事：逾期待办、今日到期、收集任务进度、请假中和最近一次筛查结果。
        点击卡片直达对应模块；具体操作在各功能页完成。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import DesktopIcon from '../components/DesktopIcon.vue'
import TrendChart from '../components/TrendChart.vue'
import DonutChart from '../components/DonutChart.vue'

const auth = useAuthStore()
const data = ref({})
const trends = ref({ weeks: [], weeklyLeave: [], weeklyChecks: [], leaveTypePie: [], collectionBars: [] })
const trendsLoaded = ref(false)
const loaded = ref(false)

const leaveSeries = computed(() => [
  { name: '请假人次', data: trends.value.weeklyLeave, color: '#3E5C4B', type: 'line' }
])
const checkSeries = computed(() => [
  { name: '筛查次数', data: trends.value.weeklyChecks, color: '#D9A441', type: 'bar' }
])
const leavePie = computed(() =>
  (trends.value.leaveTypePie || []).map((p, i) => ({
    name: p.name,
    value: p.value,
    color: ['#C96F5E', '#D9A441', '#8FB79B', '#CBDACB'][i % 4]
  }))
)

const greeting = (() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})()

const todayText = (() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})()

// 历史记录的 checkedAt 是字符串时间（可能为 ISO 长串），统一转本地展示
function fmtCheckTime(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// 收集横条百分比
function pct(b) {
  return b.total ? Math.round((b.received / b.total) * 100) : 0
}

async function load() {
  try {
    const { data: res } = await api.get('/dashboard/summary')
    data.value = res.data || {}
    loaded.value = true
  } catch {
    // 拦截器已提示（未登录等场景）
  }
  try {
    const { data: res } = await api.get('/dashboard/trends')
    trends.value = res.data || trends.value
    trendsLoaded.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)
// keep-alive 缓存下每次切回工作台都刷新一次
onActivated(load)
</script>

<style scoped>
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #fbf8f0 0%, #eef4ee 100%);
  margin-bottom: 16px;
}

.hero-greet {
  font-size: 22px;
  font-weight: 800;
  color: var(--c-primary-deep);
}

.hero-actions {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-cat {
  color: var(--c-primary);
  opacity: 0.85;
  animation: floatY 3.5s ease-in-out infinite;
}

.stat-row {
  margin-bottom: 16px;
}

.chart-card {
  border: 1px solid var(--c-line);
  border-radius: 14px;
  padding: 12px 14px 6px;
  background: #fff;
}

.chart-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--c-ink);
  margin-bottom: 4px;
}

.bar-list {
  padding: 8px 4px 4px;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.bar-name {
  width: 150px;
  font-size: 12.5px;
  color: var(--c-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 12px;
  background: var(--c-bg-2);
  border-radius: 999px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.5s ease;
}

.bar-num {
  font-size: 12px;
  color: var(--c-ink-soft);
  flex-shrink: 0;
  width: 56px;
  text-align: right;
}

.dash-card {
  flex: 1;
  min-width: 170px;
  padding: 16px 18px;
  border-radius: 16px;
  background: var(--c-card);
  border: 1px solid var(--c-line);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  animation: popIn 0.35s ease both;
}

.dash-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 22px rgba(62, 92, 75, 0.14);
}

.dash-card:nth-child(2) {
  animation-delay: 0.05s;
}
.dash-card:nth-child(3) {
  animation-delay: 0.1s;
}
.dash-card:nth-child(4) {
  animation-delay: 0.15s;
}
.dash-card:nth-child(5) {
  animation-delay: 0.2s;
}

.dash-ico {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
  background: var(--c-primary-soft);
  color: var(--c-primary-deep);
}

.dash-todo .dash-ico {
  background: var(--c-warn-soft);
  color: #8a6412;
}
.dash-collect .dash-ico {
  background: var(--c-primary-soft);
  color: var(--c-primary-deep);
}
.dash-leave .dash-ico {
  background: var(--c-taro-soft);
  color: #4e6a52;
}
.dash-check .dash-ico {
  background: var(--c-peach-soft);
  color: #a85b41;
}
.dash-students .dash-ico {
  background: var(--c-mint-soft);
  color: #2f6a4f;
}

.dash-num {
  font-size: 26px;
  font-weight: 800;
  color: var(--c-primary-deep);
}

.dash-sep {
  color: var(--c-ink-soft);
  font-weight: 600;
  margin: 0 4px;
  font-size: 18px;
}

.warn-text {
  color: var(--c-danger);
}

.ok-text {
  color: var(--c-ok);
}

.dash-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--c-ink);
  margin-top: 2px;
}

.dash-sub {
  margin-top: 4px;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 210px;
}
</style>
