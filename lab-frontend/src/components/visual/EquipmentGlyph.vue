<script setup>
import { computed } from 'vue'
const props = defineProps({ name: { type: String, default: '' } })
// Illustrations suggest a category only; names/models remain the source of truth.
const kind = computed(() => /显微镜/.test(props.name) ? 'microscope' : /万用表|电表/.test(props.name) ? 'meter' : /光学/.test(props.name) ? 'optics' : /示波器/.test(props.name) ? 'scope' : 'equipment')
</script>
<template>
  <svg class="equipment-glyph" viewBox="0 0 200 110" fill="none" aria-hidden="true" focusable="false">
    <path d="M30 96H170" stroke="currentColor" opacity=".22" />
    <g stroke="currentColor" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round">
      <g v-if="kind === 'scope'">
        <rect x="36" y="30" width="128" height="62" rx="6" fill="white" fill-opacity=".65" />
        <rect x="45" y="39" width="78" height="42" rx="3" fill="currentColor" fill-opacity=".07" />
        <path d="M51 62H61L69 46L80 75L91 55L100 62H116M48 92V96M151 92V96" />
        <circle cx="144" cy="49" r="7" /><circle cx="139" cy="72" r="2" /><circle cx="151" cy="72" r="2" /><path d="M58 24H141" />
      </g>
      <g v-else-if="kind === 'microscope'">
        <path d="M95 39C138 38 150 68 122 88M102 87V65M77 65H117M81 88H134L140 96H75Z" fill="white" fill-opacity=".6" />
        <path d="M98 17L88 42L103 48L114 23ZM96 13L119 23M92 45L88 53M101 48L98 56" /><circle cx="126" cy="59" r="7" />
      </g>
      <g v-else-if="kind === 'meter'">
        <rect x="68" y="17" width="64" height="78" rx="9" fill="white" fill-opacity=".6" />
        <rect x="77" y="27" width="46" height="20" rx="2" fill="currentColor" fill-opacity=".09" />
        <path d="M84 37H91M98 37H105M112 37H116M100 54V61" /><circle cx="100" cy="68" r="12" /><path d="M100 62V68M85 87H88M112 87H115" />
      </g>
      <g v-else-if="kind === 'optics'">
        <path d="M37 78H167L157 91H27ZM42 91V96M147 91V96M57 77V48M114 76V48M144 74V43" fill="white" fill-opacity=".6" />
        <ellipse cx="57" cy="40" rx="7" ry="17" /><ellipse cx="114" cy="38" rx="10" ry="21" /><path d="M138 25H150V52H138Z" fill="currentColor" fill-opacity=".1" /><path d="M65 40H139" stroke-dasharray="4 5" />
      </g>
      <g v-else>
        <rect x="52" y="29" width="96" height="62" rx="7" fill="white" fill-opacity=".6" /><rect x="63" y="41" width="49" height="29" rx="3" /><circle cx="130" cy="47" r="4" /><path d="M126 62H134M65 81H98M63 91V96M136 91V96M69 23H131" />
      </g>
    </g>
  </svg>
</template>
<style scoped>
.equipment-glyph { width: 180px; max-width: 76%; height: 100px; flex-shrink: 0; }
</style>
