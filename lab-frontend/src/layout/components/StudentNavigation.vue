<script setup>
import { nextTick, onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { studentNavigation } from '@/utils/role-ui'

const route = useRoute()
const navigation = shallowRef(null)
let observer
async function revealCurrent() {
  await nextTick()
  const root = navigation.value
  const active = root?.querySelector('[aria-current="page"]')
  if (!active) return
  const bounds = root.getBoundingClientRect(), item = active.getBoundingClientRect()
  if (item.left < bounds.left || item.right > bounds.right) {
    active.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  }
}
watch(() => route.path, revealCurrent)
onMounted(() => {
  revealCurrent()
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(revealCurrent)
    observer.observe(navigation.value)
  }
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <nav class="student-navigation" aria-label="学生功能导航">
    <div ref="navigation">
      <RouterLink v-for="item in studentNavigation" :key="item.path" :to="item.path" active-class="selected">{{ item.label }}</RouterLink>
    </div>
  </nav>
</template>
