import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: './web/src/setupTests.ts',
    include: ['./web/src/**/*.test.tsx'],
  },
});
