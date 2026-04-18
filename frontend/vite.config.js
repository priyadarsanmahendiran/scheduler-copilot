import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/events': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Disable response buffering so SSE frames reach the browser immediately
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            proxyRes.headers['cache-control'] = 'no-cache'
          })
        },
      },
      '/api': 'http://localhost:8080',
      '/machines': 'http://localhost:8080',
      '/schedule': 'http://localhost:8080',
    }
  }
})
