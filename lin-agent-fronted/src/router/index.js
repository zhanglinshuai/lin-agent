import { createRouter, createWebHistory } from 'vue-router'

const Home = () => import('@/pages/Home.vue')
const AssistantChat = () => import('@/pages/AssistantChat.vue')
const AdminConsole = () => import('@/pages/AdminConsole.vue')
const Login = () => import('@/pages/Login.vue')

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: Home },
    { path: '/assistant', name: 'assistant', component: AssistantChat },
    { path: '/admin', name: 'admin', component: AdminConsole },
    { path: '/login', name: 'login', component: Login },
    { path: '/emotion', redirect: '/assistant' },
    { path: '/manus', redirect: '/assistant' },
  ],
})

router.beforeEach((to, from, next) => {
  if (to.path !== '/admin') {
    next()
    return
  }
  let userRole = ''
  try {
    userRole = String(localStorage.getItem('user_role') || '')
  } catch (e) {
    userRole = ''
  }
  if (userRole === '1') {
    next()
    return
  }
  next('/assistant')
})

export default router
