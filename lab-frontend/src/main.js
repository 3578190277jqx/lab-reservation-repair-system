import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { pinia } from './stores'
import router from './router'
import './styles/tokens.css'
import './styles/index.css'
import './styles/workspaces.css'
import './styles/interactions.css'
import { revealDirective } from './utils/reveal'

const app = createApp(App)
// 守卫和组件共用同一个 Pinia 实例，先安装 Pinia，再安装 Router。
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('reveal', revealDirective)
app.mount('#app')
