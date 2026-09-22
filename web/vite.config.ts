import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.CASEGEN_DEV_API_TARGET ?? 'http://localhost:8080'
  const proxy = {
    '/api': {
      target: apiTarget,
      changeOrigin: true,
    },
    '/ws': {
      target: apiTarget,
      changeOrigin: true,
      ws: true,
    },
  }

  return {
    plugins: [react(), tailwindcss()],
    server: { proxy },
    preview: { proxy },
  }
})
