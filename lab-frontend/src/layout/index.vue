<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { ArrowUp } from '@element-plus/icons-vue'
import { useScrollState } from '@/composables/useScrollState'
import Sidebar from './components/Sidebar.vue'
import Topbar from './components/Topbar.vue'
import StudentNavigation from './components/StudentNavigation.vue'
import CommandPalette from '@/components/interaction/CommandPalette.vue'

const user = useUserStore()
const student = computed(() => user.role === 'student')
const collapsed = ref(false)
const mobile = ref(false)
const drawerOpen = ref(false)
const commandPalette = ref(null)
const { scrolled, showBackTop, backToTop } = useScrollState()
let media
function syncMedia() { mobile.value = media.matches; if (!media.matches) drawerOpen.value = false }
function toggleSidebar() { if (mobile.value) drawerOpen.value = !drawerOpen.value; else collapsed.value = !collapsed.value }
onMounted(() => { media = window.matchMedia('(max-width: 959px)'); syncMedia(); media.addEventListener('change', syncMedia) })
onBeforeUnmount(() => media?.removeEventListener('change', syncMedia))
</script>

<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': collapsed && !student, 'student-shell': student, 'manager-shell': !student }">
    <a class="skip-link" href="#main-content">跳转到主要内容</a>
    <aside v-if="!student" id="workspace-sidebar" class="desktop-sidebar"><Sidebar :collapsed="collapsed" /></aside>
    <el-drawer v-if="!student" v-model="drawerOpen" direction="ltr" size="260px" :with-header="false" class="navigation-drawer" aria-label="主导航"><Sidebar @navigate="drawerOpen = false" /></el-drawer>
    <div class="app-main">
      <div class="workspace-header" :class="{ 'is-scrolled': scrolled }">
        <Topbar :collapsed="collapsed" :mobile="mobile" :student="student" :navigation-expanded="mobile ? drawerOpen : !collapsed" @toggle="toggleSidebar" @search="commandPalette?.open()" />
        <StudentNavigation v-if="student" />
      </div>
      <main id="main-content" class="page-content" tabindex="-1">
        <RouterView v-slot="{ Component, route }">
          <Transition name="workspace-page" mode="out-in">
            <div v-if="Component" :key="route.path" class="route-stage"><component :is="Component" /></div>
          </Transition>
        </RouterView>
      </main>
      <footer class="app-footer"><span>知行实验室 · 设备预约与报修平台</span><span>专注探索，有序协作</span></footer>
    </div>
    <CommandPalette ref="commandPalette" />
    <Transition name="back-top"><button v-if="showBackTop" type="button" class="back-top-button" aria-label="返回页面顶部" @click="backToTop"><el-icon><ArrowUp /></el-icon></button></Transition>
  </div>
</template>
