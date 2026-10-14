import { defineStore } from 'pinia';

// 数据迁移的连接信息与取数 SQL 只在本次会话（标签页）里保留。以前存在 localStorage，迁移做完多久它们都还留在浏览器里：
// 旧系统的数据库地址、账号、表结构不该这样长期留存。升级前留下的那一份在此清掉
try {
  localStorage.removeItem('ujcmsImportDataStore');
} catch {
  // 隐私模式等场景下 localStorage 不可用：无可清理
}

export interface ImportDataSource {
  type?: string;
  host?: string;
  port?: number;
  database?: string;
  options?: string;
  username?: string;
}

export const useImportDataStore = defineStore('ujcmsImportDataStore', {
  state: () => ({
    datasource: {} as ImportDataSource,
    channel: { sql: '' } as { sql: string },
    article: { sql: '', defaultChannelId: undefined } as { sql: string; defaultChannelId?: string },
  }),
  persist: {
    storage: sessionStorage,
  },
});

// 数据库口令只放在内存里，不落任何存储：刷新页面后须重新输入。
// 它须有自己的 store id——以前与上面的 store 同名，实际拿到的是同一个 store
export const useImportDataPasswordStore = defineStore('ujcmsImportDataPasswordStore', {
  state: () => ({
    password: '',
  }),
});
