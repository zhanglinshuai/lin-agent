import { createRouter, createWebHistory } from 'vue-router'

const Home = () => import('@/pages/Home.vue')
const EmotionChat = () => import('@/pages/EmotionChat.vue')
const ManusChat = () => import('@/pages/ManusChat.vue')

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: Home },
    { path: '/emotion', name: 'emotion', component: EmotionChat },
    { path: '/manus', name: 'manus', component: ManusChat },
  ],
})

export default router