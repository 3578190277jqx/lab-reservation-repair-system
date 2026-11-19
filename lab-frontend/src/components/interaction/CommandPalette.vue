<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search, ArrowRight, Plus, Close } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { filterCommands, moveCommandIndex, navigationCommands } from '@/utils/commands'

const router = useRouter()
const user = useUserStore()
const visible = shallowRef(false)
const query = shallowRef('')
const selected = shallowRef(0)
const input = shallowRef(null)
const commands = computed(() => navigationCommands(user.role))
const results = computed(() => filterCommands(commands.value, query.value))
const activeId = computed(() => results.value.length ? 'command-option-' + selected.value : undefined)
watch([query, () => user.role], () => { selected.value = 0 })
watch(() => user.token, () => { visible.value = false })

function open() {
  if (!user.role) return
  query.value = ''
  selected.value = 0
  visible.value = true
}
function shortcut(event) {
  if (!(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey || event.key.toLowerCase() !== 'k' || event.isComposing) return
  if (!visible.value && document.activeElement?.closest('[role="dialog"], [role="alertdialog"]')) return
  event.preventDefault()
  if (visible.value) visible.value = false
  else open()
}
async function move(delta) {
  selected.value = moveCommandIndex(selected.value, delta, results.value.length)
  await nextTick()
  if (activeId.value) document.getElementById(activeId.value)?.scrollIntoView({ block: 'nearest' })
}
function select(command) {
  if (!command || !commands.value.some(item => item.id === command.id)) return
  visible.value = false
  router.push(command.to)
}
function keydown(event) {
  if (event.isComposing || event.keyCode === 229) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    move(event.key === 'ArrowDown' ? 1 : -1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    select(results.value[selected.value])
  }
}
onMounted(() => window.addEventListener('keydown', shortcut))
onBeforeUnmount(() => window.removeEventListener('keydown', shortcut))
defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="快捷搜索" width="min(640px, calc(100vw - 24px))" top="12vh" class="command-dialog" :show-close="false" append-to-body @opened="input?.focus()">
    <template #header>
      <div class="command-heading"><span>快捷搜索</span><button type="button" aria-label="关闭快捷搜索" @click="visible = false"><el-icon><Close /></el-icon></button></div>
    </template>
    <div @keydown="keydown">
      <el-input ref="input" v-model="query" placeholder="搜索页面或操作，例如：预约、报修" :prefix-icon="Search" maxlength="80" container-role="combobox" aria-label="搜索页面或操作" aria-autocomplete="list" aria-controls="command-results" :aria-expanded="visible" :aria-activedescendant="activeId" clearable />
      <p class="command-context">只显示当前账号可访问的入口</p>
      <ul id="command-results" class="command-results" role="listbox" aria-label="搜索结果">
        <li v-for="(command, index) in results" :id="'command-option-' + index" :key="command.id" role="option" :aria-selected="selected === index" class="command-option" :class="{ 'is-selected': selected === index }" @pointermove="selected = index" @click="select(command)">
          <span class="command-icon"><el-icon><Plus v-if="command.type === 'action'" /><ArrowRight v-else /></el-icon></span>
          <span><strong>{{ command.label }}</strong><small>{{ command.detail }}</small></span>
          <span class="command-return" aria-hidden="true">↵</span>
        </li>
      </ul>
      <p v-if="!results.length" class="command-empty" role="status">没有找到相关入口，试试“设备”或“报修”。</p>
    </div>
    <template #footer><div class="command-help"><span><kbd>↑</kbd><kbd>↓</kbd> 选择</span><span><kbd>Enter</kbd> 前往</span><span><kbd>Esc</kbd> 关闭</span></div></template>
  </el-dialog>
</template>
