import { defineConfig } from 'vite';
import monkey from 'vite-plugin-monkey';
import vue from '@vitejs/plugin-vue';
import { USERSCRIPT_META } from './src/meta';

const USERSCRIPT_VERSION = USERSCRIPT_META.version;

export default defineConfig({
  define: {
    __USERSCRIPT_VERSION__: JSON.stringify(USERSCRIPT_VERSION),
  },
  plugins: [
    vue(),
    monkey({
      entry: 'src/index.ts',
      userscript: {
        ...USERSCRIPT_META,
        match: [...USERSCRIPT_META.match],
        grant: [...USERSCRIPT_META.grant],
        connect: [...USERSCRIPT_META.connect],
      }
    })
  ],
  build: {
    target: 'es2020'
  }
});
