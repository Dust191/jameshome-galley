import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 后端端口可被环境变量改写：本机开发常用 `--server.port=18080` 避开被占用的 8080。
const apiTarget = process.env.GALLEY_API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
      '/healthz': { target: apiTarget, changeOrigin: true },
      '/actuator': { target: apiTarget, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // antd 与 React 体积大，拆 vendor：业务代码改动后浏览器只需重新下载小包
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          antd: ['antd', '@ant-design/icons'],
        },
      },
    },
  },
})
