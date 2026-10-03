import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    redirect: '/check',
    children: [
      { path: 'students', name: 'students', component: () => import('../views/Students.vue'), meta: { title: '基准名单' } },
      { path: 'check', name: 'check', component: () => import('../views/Check.vue'), meta: { title: '名单筛查' } },
      { path: 'history', name: 'history', component: () => import('../views/History.vue'), meta: { title: '历史记录' } },
      { path: 'tasks', name: 'tasks', component: () => import('../views/Tasks.vue'), meta: { title: '长期任务' } },
      { path: 'groups', name: 'groups', component: () => import('../views/Groups.vue'), meta: { title: '自定义分组' } },
      { path: 'announcements', name: 'announcements', component: () => import('../views/Announcements.vue'), meta: { title: '群发素材' } },
      { path: 'leaves', name: 'leaves', component: () => import('../views/Leaves.vue'), meta: { title: '请假记录' } },
      { path: 'scholarships', name: 'scholarships', component: () => import('../views/Scholarships.vue'), meta: { title: '奖学金统计' } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
