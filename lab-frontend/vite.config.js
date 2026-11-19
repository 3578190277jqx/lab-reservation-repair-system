import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    optimizeDeps: { include: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'] },
    build: { rolldownOptions: { output: { strictExecutionOrder:true,codeSplitting:{groups:[
      {name:'charts',test:/node_modules[\\/]((echarts)|(zrender))[\\/]/,maxSize:240000,priority:30},
      {name:'ui',test:/node_modules[\\/](element-plus|@element-plus)[\\/]/,maxSize:240000,priority:20},
      {name:'vendor',test:/node_modules/,maxSize:240000,priority:10}
    ]}}}},
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      warmup: { clientFiles: ['./src/views/StatsView.vue'] },
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        // 前端 /api/user/login -> 后端 /user/login，只移除一次 /api。
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api(?=\/|$)/, ''),
        },
      },
    },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true },
    test: { environment: 'node', clearMocks: true, restoreMocks: true },
  }
})
