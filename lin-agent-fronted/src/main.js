import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import router from '@/router'
import { setupAxiosInterceptors } from '@/services/request'

setupAxiosInterceptors()
createApp(App).use(router).mount('#app')
