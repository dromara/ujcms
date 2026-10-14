import axios from '@/utils/request';
import { queryBackendState } from '@/api/login';

// 安装接口（install-flow §5）。除 state 外的端点必须携带一次性安装令牌（后端启动日志打印）。
const TOKEN_HEADER = 'ujcms-install-token';

const tokenHeaders = (token: string) => ({ headers: { [TOKEN_HEADER]: token } });

// 安装状态探测缓存：backend-state 匿名响应携带 installed（install-flow §3）。已安装即粘滞，
// 正常使用零探测；探测失败一律视为已安装（fail-open），绝不把用户困在安装页外。
let installedCache: boolean | undefined;
export const isSystemInstalled = async (): Promise<boolean> => {
  if (installedCache === true) return true;
  try {
    const data = await queryBackendState();
    installedCache = data?.installed !== false;
  } catch {
    installedCache = true;
  }
  return installedCache;
};

export const queryInstallState = async (): Promise<any> => (await axios.get('/install/state', { skipErrorHandler: true } as any)).data;
export const verifyInstallToken = async (token: string): Promise<any> =>
  (await axios.post('/install/verify-token', undefined, { ...tokenHeaders(token), skipErrorHandler: true } as any)).data;
export const installSite = async (token: string, data: Record<string, any>): Promise<any> =>
  (await axios.post('/install/site', data, tokenHeaders(token))).data;
export const installAdmin = async (token: string, data: Record<string, any>): Promise<any> =>
  (await axios.post('/install/admin', data, tokenHeaders(token))).data;
// 主题步骤可选项（D30）：站点 1 当前主题、内置可用性、包库存量、市场开关
export const queryInstallThemeOptions = async (token: string): Promise<any> =>
  (await axios.get('/install/theme-options', tokenHeaders(token))).data;
// 主题步骤：{ source: 'bundled'|'library'|'marketplace', ref?, seedScope }
export const installTheme = async (token: string, data: Record<string, any>): Promise<any> =>
  (await axios.post('/install/theme', data, { ...tokenHeaders(token), timeout: 300000 })).data;
// 主题上传步骤（upload 来源）：ZIP 写穿包库后从库内副本安装（包内清单必须声明 themeName）
export const installThemeUpload = async (token: string, data: FormData): Promise<any> =>
  (await axios.post('/install/theme-upload', data, { ...tokenHeaders(token), timeout: 300000 })).data;
export const installComplete = async (token: string): Promise<any> =>
  (await axios.post('/install/complete', undefined, { ...tokenHeaders(token), timeout: 300000 })).data;
