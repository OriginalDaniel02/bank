import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The browser calls /api on the dev server, which forwards it to the Spring Boot backend.
const backend = process.env.BACKEND_URL || "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: { "/api": { target: backend, changeOrigin: true } },
  },
});
