import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/calm-hub-status': {
        target: 'http://localhost:8090/q/openapi',
        changeOrigin: true,
        rewrite: () => '/q/openapi',
      },
    },
  },
})
