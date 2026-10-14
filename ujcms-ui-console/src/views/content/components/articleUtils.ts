import { getAccessToken } from '@/utils/auth';

// 须传完整地址（fullUrl / dynamicFullUrl）：接口的 url 为站点相对路径，直接打开会落在控制台自身域名下
export const openArticleLink = (status: number, fullUrl: string, dynamicFullUrl: string) => {
  if (status === 0 || status === 1) {
    window.open(fullUrl);
  } else {
    dynamicFullUrl = dynamicFullUrl + '?preview=true';
    const index = dynamicFullUrl.lastIndexOf('/', dynamicFullUrl.lastIndexOf('/') - 1);
    const jwtLoginUrl = `${dynamicFullUrl.substring(0, index)}/auth/jwt/login?code=${getAccessToken()}&redirectUri=${encodeURIComponent(dynamicFullUrl)}`;
    window.open(jwtLoginUrl);
  }
};

/** 状态标签的类型：与文章列表的状态列一致（0 已发布 → success，1 已归档 → primary，20/21 → warning，22 已退回 → danger，其余 info） */
export const articleStatusTagType = (status: number): 'success' | 'primary' | 'warning' | 'danger' | 'info' => {
  if (status === 0) return 'success';
  if (status === 1) return 'primary';
  if (status === 20 || status === 21) return 'warning';
  if (status === 22) return 'danger';
  return 'info';
};
