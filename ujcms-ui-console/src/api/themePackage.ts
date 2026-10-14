// 主题包库（install-flow §6 P3 / D5）：ThemePackageController /api/backend/core/theme-package
// 权限码：theme:package（面板/上传/删除，核心权限——Q6）；安装到站点沿用 theme:install
import axios from '@/utils/request';
import type { ActivateTarget, SeedScope } from '@/api/theme';

export interface ThemePackageInfo {
  // Long 全局序列化为 string（Application.jackson2ObjectMapperBuilderCustomizer）
  id: string;
  name: string;
  version: string;
  sha256: string;
  size: string;
  source: 'marketplace' | 'upload' | 'export' | 'bundled';
  marketplaceSlug?: string;
  created: string;
}

export interface ThemePackageInstallParams {
  packageId: string;
  siteId: string | number;
  themeName?: string;
  /** 装后激活到的槽位（desktop | mobile | both）；缺省只安装 */
  activate?: ActivateTarget;
  seedScope?: SeedScope;
}

export const queryThemePackageList = async (): Promise<ThemePackageInfo[]> =>
  (await axios.get('/backend/core/theme-package')).data;

export const uploadThemePackage = async (data: FormData): Promise<ThemePackageInfo> =>
  (await axios.post('/backend/core/theme-package/upload', data)).data;

export const installThemePackage = async (params: ThemePackageInstallParams): Promise<any> =>
  (await axios.post('/backend/core/theme-package/install', params)).data;

export const deleteThemePackage = async (id: string): Promise<any> =>
  (await axios.delete(`/backend/core/theme-package/${id}`)).data;
