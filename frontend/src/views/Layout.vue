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
      </el-menu>
      <div class="aside-footer">🌷 默认仅本机访问</div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ title }}</div>
        <div class="header-actions">
          <el-input
            v-if="passwordRequired"
            v-model="password"
            placeholder="访问口令"
            show-password
            style="width: 180px"
            @change="savePassword"
          />
          <span v-else class="muted" style="font-size: 12px">本机使用 · 无需口令</span>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api/client'

const route = useRoute()
const active = computed(() => route.path)
const title = computed(() => route.meta.title || '班级成员管理系统')
const password = ref(localStorage.getItem('accessPassword') || '')
const passwordRequired = ref(false)

function savePassword(val) {
  localStorage.setItem('accessPassword', val || '')
}

onMounted(async () => {
  try {
    const { data } = await api.get('/health')
    passwordRequired.value = !!(data.data && data.data.passwordRequired)
  } catch {
    passwordRequired.value = false
  }
})
</script>

<style scoped>
.layout {
  height: 100vh;
}

.menu-ico {
  margin-right: 6px;
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
