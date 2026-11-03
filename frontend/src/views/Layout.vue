<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <div class="brand-title">
          <span class="logo">🎀</span>
          班级成员管理
        </div>
        <div class="brand-sub">班长专属 · 名单核对</div>
      </div>
      <el-menu :default-active="active" router class="menu">
        <el-menu-item index="/check"><span class="menu-ico">✨</span> 名单筛查</el-menu-item>
        <el-menu-item index="/students"><span class="menu-ico">🌸</span> 基准名单</el-menu-item>
        <el-menu-item index="/history"><span class="menu-ico">📖</span> 历史记录</el-menu-item>
        <el-menu-item index="/tasks"><span class="menu-ico">🎯</span> 长期任务</el-menu-item>
        <el-menu-item index="/groups"><span class="menu-ico">🫧</span> 自定义分组</el-menu-item>
        <el-menu-item index="/announcements"><span class="menu-ico">📣</span> 群发素材</el-menu-item>
        <el-menu-item index="/leaves"><span class="menu-ico">🌙</span> 请假记录</el-menu-item>
        <el-menu-item index="/scholarships"><span class="menu-ico">🏆</span> 奖学金统计</el-menu-item>
        <el-menu-item index="/collections"><span class="menu-ico">📥</span> 资料收集与分发</el-menu-item>
      </el-menu>
      <div class="aside-footer">{{ footerText }}</div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ title }}</div>
        <div class="header-actions">
          <template v-if="auth.statusLoaded">
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
          <div class="lock-icon">🔒</div>
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
      title="登录 · 班级成员管理"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
    >
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
  </el-container>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api, { onUnauthorized } from '../api/client'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const active = computed(() => route.path)
const title = computed(() => route.meta.title || '班级成员管理系统')
const footerText = computed(() => (auth.authRequired ? '🔒 已启用登录保护' : '🌷 默认仅本机访问'))

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

onMounted(async () => {
  await auth.refresh()
  auth.openLogin()
})
</script>

<style scoped>
.layout {
  height: 100vh;
}

.menu-ico {
  margin-right: 6px;
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
  font-size: 46px;
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
