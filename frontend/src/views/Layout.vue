<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="brand">
        <div class="brand-title">
          <span class="logo"><DesktopIcon name="cat" :size="17" /></span>
          <span v-if="!collapsed" class="brand-name">喵班</span>
        </div>
        <div v-if="!collapsed" class="brand-sub">喵陪班 · 班级事务管理系统</div>
      </div>
      <el-menu
        :default-active="active"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="menu"
      >
        <el-menu-item index="/dashboard">
          <span class="menu-ico"><DesktopIcon name="home" :size="18" /></span>
          <template #title><span class="menu-text">工作台</span></template>
        </el-menu-item>
        <el-menu-item-group>
          <template #title>核心事务</template>
          <el-menu-item index="/check">
            <span class="menu-ico"><DesktopIcon name="sparkle" :size="18" /></span>
            <template #title><span class="menu-text">名单筛查</span></template>
          </el-menu-item>
          <el-menu-item index="/collections">
            <span class="menu-ico"><DesktopIcon name="clipboard" :size="18" /></span>
            <template #title><span class="menu-text">资料收集与分发</span></template>
          </el-menu-item>
          <el-menu-item index="/leaves">
            <span class="menu-ico"><DesktopIcon name="moon" :size="18" /></span>
            <template #title><span class="menu-text">请假记录</span></template>
          </el-menu-item>
          <el-menu-item index="/todos">
            <span class="menu-ico"><DesktopIcon name="clock" :size="18" /></span>
            <template #title><span class="menu-text">待办事务</span></template>
          </el-menu-item>
          <el-menu-item index="/scholarships">
            <span class="menu-ico"><DesktopIcon name="trophy" :size="18" /></span>
            <template #title><span class="menu-text">奖学金统计</span></template>
          </el-menu-item>
          <el-menu-item index="/announcements">
            <span class="menu-ico"><DesktopIcon name="megaphone" :size="18" /></span>
            <template #title><span class="menu-text">群发素材</span></template>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group>
          <template #title>基础资料</template>
          <el-menu-item index="/students">
            <span class="menu-ico"><DesktopIcon name="users" :size="18" /></span>
            <template #title><span class="menu-text">基准名单</span></template>
          </el-menu-item>
          <el-menu-item index="/history">
            <span class="menu-ico"><DesktopIcon name="book" :size="18" /></span>
            <template #title><span class="menu-text">历史记录</span></template>
          </el-menu-item>
          <el-menu-item index="/tasks">
            <span class="menu-ico"><DesktopIcon name="calendar" :size="18" /></span>
            <template #title><span class="menu-text">长期任务</span></template>
          </el-menu-item>
          <el-menu-item index="/groups">
            <span class="menu-ico"><DesktopIcon name="grid" :size="18" /></span>
            <template #title><span class="menu-text">自定义分组</span></template>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group>
          <template #title>系统管理</template>
          <el-menu-item index="/settings">
            <span class="menu-ico"><DesktopIcon name="settings" :size="18" /></span>
            <template #title><span class="menu-text">系统设置</span></template>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
      <div class="aside-collapse">
        <button class="collapse-btn" :title="collapsed ? '展开菜单' : '收起菜单'" @click="collapsed = !collapsed">
          <DesktopIcon name="chevron" :size="16" :style="collapsed ? '' : 'transform: rotate(180deg)'" />
        </button>
      </div>
      <div v-if="!collapsed" class="aside-footer">{{ footerText }}</div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ title }}</div>
        <div class="header-actions">
          <template v-if="auth.statusLoaded">
            <el-dropdown class="theme-dd" trigger="click" @command="onThemeCommand">
              <span class="theme-btn" title="主题（深色模式开关）">
                <DesktopIcon name="moon" :size="18" />
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="auto">跟随系统</el-dropdown-item>
                  <el-dropdown-item command="light">浅色</el-dropdown-item>
                  <el-dropdown-item command="dark">深色</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-select
              v-if="term.loaded"
              :model-value="term.viewTermId"
              class="term-select"
              @change="onTermChange"
            >
              <el-option
                v-for="t in term.terms"
                :key="t.id"
                :value="t.id"
                :label="t.name + (t.archived ? '（已归档）' : '')"
              />
            </el-select>
            <el-tag v-if="term.browsingHistory" type="warning" size="small" effect="light">历史学期 · 只读</el-tag>
            <el-badge
              v-if="!auth.authRequired || auth.loggedIn"
              :value="unread"
              :hidden="!unread"
              class="bell-badge"
            >
              <button class="bell-btn" title="消息中心" @click="openNotifications">
                <DesktopIcon name="bell" :size="18" />
              </button>
            </el-badge>
            <template v-if="auth.authRequired">
              <template v-if="auth.loggedIn">
                <span class="muted" style="font-size: 12px">已登录 · {{ auth.username }}</span>
                <el-button link type="danger" @click="auth.logout()">退出</el-button>
              </template>
              <el-button v-else type="primary" size="small" @click="auth.openLogin()">登录</el-button>
            </template>
            <span v-else class="muted" style="font-size: 12px">本机使用 · 无需口令</span>
          </template>
        </div>
      </el-header>
      <el-main class="main">
        <div v-if="!auth.statusLoaded" class="lock-pane muted">⏳ 正在检查登录状态…</div>
        <div v-else-if="auth.authRequired && !auth.loggedIn" class="lock-pane">
          <div class="lock-icon"><DesktopIcon name="bell" :size="46" /></div>
          <p class="muted">系统已启用登录保护，登录后才能使用各项功能</p>
          <el-button type="primary" @click="auth.openLogin()">立即登录</el-button>
        </div>
        <router-view v-else v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>

    <el-dialog
      v-model="auth.loginVisible"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
    >
      <template #header>
        <div class="login-head">
          <span class="login-logo"><DesktopIcon name="cat" :size="26" /></span>
          <div>
            <div class="login-title">登录喵班</div>
            <div class="login-sub">喵陪班 · 班级事务管理系统</div>
          </div>
        </div>
      </template>
      <el-form label-width="70px" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="账号" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="密码"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="不区分大小写"
              @keyup.enter="submit"
            />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              class="captcha-img"
              title="看不清？点击刷新"
              alt="验证码"
              @click="loadCaptcha"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auth.loginVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">登录</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="notifyDrawer" title="消息中心" size="380px" append-to-body>
      <div v-if="notifications.length">
        <div class="notify-toolbar">
          <el-button size="small" link type="primary" @click="markAllRead">全部已读</el-button>
        </div>
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notify-item"
          :class="{ unread: !n.read }"
          @click="readOne(n)"
        >
          <div class="notify-title-line">
            <span v-if="!n.read" class="notify-dot"></span>
            <span class="notify-title">{{ n.title }}</span>
          </div>
          <div v-if="n.body" class="muted notify-body">{{ n.body }}</div>
          <div class="muted notify-time">{{ fmtTime(n.createdAt) }}</div>
        </div>
      </div>
      <div v-else class="muted" style="text-align: center; padding: 40px 0">暂无消息，处理中的事项到期会出现在这里</div>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import api, { onUnauthorized } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useTermStore } from '../stores/term'
import { useThemeStore } from '../stores/theme'
import DesktopIcon from '../components/DesktopIcon.vue'

const route = useRoute()
const auth = useAuthStore()
const term = useTermStore()
const theme = useThemeStore()
const active = computed(() => route.path)
const title = computed(() => route.meta.title || '喵班 · 班级事务管理系统')
const footerText = computed(() => (auth.authRequired ? '🔒 已启用登录保护' : '默认仅本机访问'))
const collapsed = ref(false)

const unread = ref(0)
const notifications = ref([])
const notifyDrawer = ref(false)

function fmtTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

async function loadNotifications() {
  try {
    const { data } = await api.get('/notifications')
    notifications.value = data.data.items || []
    unread.value = data.data.unread || 0
  } catch (e) {
    /* 未登录/网络问题静默 */
  }
}

function openNotifications() {
  notifyDrawer.value = true
  loadNotifications()
}

async function markAllRead() {
  try {
    await api.post('/notifications/read-all')
    await loadNotifications()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function readOne(n) {
  if (n.read) return
  try {
    await api.post(`/notifications/${n.id}/read`)
    n.read = true
    unread.value = Math.max(0, unread.value - 1)
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function onTermChange(id) {
  term.switchTo(id)
}

function onThemeCommand(cmd) {
  theme.setPref(cmd)
}

const form = reactive({ username: '', password: '', captchaCode: '' })
const captchaImage = ref('')
const captchaId = ref('')
const submitting = ref(false)

watch(
  () => auth.loginVisible,
  (visible) => {
    if (visible) {
      loadCaptcha()
    }
  }
)

async function loadCaptcha() {
  try {
    const { data } = await api.get('/auth/captcha')
    captchaId.value = data.data.captchaId
    captchaImage.value = data.data.image
    form.captchaCode = ''
  } catch {
    // 拦截器已统一提示
  }
}

async function submit() {
  if (submitting.value) return
  if (!form.username.trim() || !form.password || !form.captchaCode.trim()) {
    ElMessage.warning('请填写账号、密码和验证码')
    return
  }
  submitting.value = true
  try {
    await api.post('/auth/login', {
      username: form.username.trim(),
      password: form.password,
      captchaId: captchaId.value,
      captchaCode: form.captchaCode.trim()
    })
    auth.loggedIn = true
    auth.username = form.username.trim()
    auth.loginVisible = false
    form.password = ''
    form.captchaCode = ''
    ElMessage.success('登录成功')
    // 登录成功后补拉学期上下文与通知（挂载时尚未登录，首拉会 401 静默失败）
    await term.refresh()
    loadNotifications()
  } catch {
    // 失败原因由拦截器统一提示；验证码是一次性的，失败后换一张
    loadCaptcha()
  } finally {
    submitting.value = false
  }
}

onUnauthorized(() => {
  auth.handleUnauthorized()
})

// 登录后如有 已过期/今天到期 的待办，弹一张提醒卡片
async function notifyTodos() {
  if (auth.authRequired && !auth.loggedIn) return
  try {
    const { data } = await api.get('/todos')
    const urgent = (data.data || []).filter((t) => !t.done && (t.overdue || t.dueToday))
    if (!urgent.length) return
    const names = urgent.slice(0, 3).map((t) => t.title).join('、')
    ElNotification.warning({
      title: '待办提醒',
      message: `有 ${urgent.length} 件待办需要处理：${names}${urgent.length > 3 ? ' 等' : ''}`,
      duration: 8000
    })
  } catch (e) {
    /* 未登录或网络问题静默 */
  }
}

onMounted(async () => {
  theme.init()
  await auth.refresh()
  await term.refresh()
  loadNotifications()
  auth.openLogin()
  notifyTodos()
})
</script>

<style scoped>
.layout {
  height: 100vh;
}

.brand-name {
  letter-spacing: 0.06em;
}

.term-select {
  width: 176px;
}

.theme-dd {
  display: inline-flex;
}

.theme-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1.5px solid var(--c-line);
  background: var(--c-card);
  color: var(--c-primary-deep);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.theme-btn:hover {
  background: var(--c-primary-soft);
}

.theme-btn .el-dropdown-selfdefine,
.theme-btn svg {
  outline: none;
}

.term-select :deep(.el-select__wrapper) {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.75);
  box-shadow: 0 0 0 1.5px var(--c-line) inset !important;
  min-height: 34px;
}

.bell-badge {
  display: inline-flex;
}

.bell-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1.5px solid var(--c-line);
  background: rgba(255, 255, 255, 0.8);
  color: var(--c-primary-deep);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.bell-btn:hover {
  background: var(--c-primary-soft);
}

.notify-toolbar {
  text-align: right;
  margin-bottom: 6px;
}

.notify-item {
  padding: 12px 10px;
  border-bottom: 1px solid var(--c-line);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.notify-item:hover,
.notify-item.unread {
  background: #fbf8f0;
}

.notify-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notify-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--c-danger);
  flex-shrink: 0;
}

.notify-title {
  font-weight: 700;
  color: var(--c-ink);
  font-size: 13.5px;
}

.notify-body {
  margin-top: 4px;
  font-size: 12px;
}

.notify-time {
  margin-top: 4px;
  font-size: 11.5px;
}

.menu-ico {
  margin-right: 8px;
  display: inline-flex;
  align-items: center;
}

.el-menu--collapse .menu-ico {
  margin-right: 0;
}

.aside-collapse {
  padding: 4px 10px 8px;
  position: relative;
  z-index: 1;
  display: flex;
}

.collapse-btn {
  width: 100%;
  height: 30px;
  border: 1px dashed var(--c-line);
  background: transparent;
  border-radius: 10px;
  color: var(--c-ink-soft);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.collapse-btn:hover {
  color: var(--c-primary-deep);
  background: rgba(255, 255, 255, 0.8);
}

.login-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.login-logo {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: var(--c-primary-soft);
  color: var(--c-primary-deep);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.login-title {
  font-size: 17px;
  font-weight: 800;
  color: var(--c-ink);
}

.login-sub {
  font-size: 12px;
  color: var(--c-ink-soft);
  margin-top: 2px;
}

.captcha-row {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.captcha-img {
  height: 36px;
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
}

.lock-pane {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding-top: 140px;
}

.lock-icon {
  color: var(--c-ink-soft);
}

.page-enter-active,
.page-leave-active {
  transition: opacity 0.28s ease, transform 0.28s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
