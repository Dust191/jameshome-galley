import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 开发期把 /api 与健康检查转发到后端，前端不直连数据库、不硬编码后端地址
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/healthz': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
