import { createRouter, createWebHistory } from 'vue-router'

const Home = () => import('@/pages/Home.vue')
const AssistantChat = () => import('@/pages/AssistantChat.vue')
const Login = () => import('@/pages/Login.vue')

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: Home },
    { path: '/assistant', name: 'assistant', component: AssistantChat },
    { path: '/login', name: 'login', component: Login },
    { path: '/emotion', redirect: '/assistant' },
    { path: '/manus', redirect: '/assistant' },
  ],
})

export default router
