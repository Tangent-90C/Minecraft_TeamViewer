import { defineConfig } from 'vite';
import monkey from 'vite-plugin-monkey';

export default defineConfig({
  plugins: [
    monkey({
      entry: 'src/index.ts',
      userscript: {
        name: '地图玩家投影 - NodeMC 版',
        namespace: 'https://map.nodemc.cc/',
        version: '0.3.0',
        description: '将远程玩家信息投影到 NodeMC 地图',
        author: 'Prof. Chen',
        match: [
          'https://map.nodemc.cc/*',
          'http://map.nodemc.cc/*',
          'https://map.fltown.cn/*',
          'http://map.fltown.cn/*',
          'file:///*NodeMC*时局图*.html*'
        ],
        'run-at': 'document-start',
        grant: ['GM_xmlhttpRequest', 'unsafeWindow'],
        connect: ['*']
      }
    })
  ],
  build: {
    target: 'es2020'
  }
});
