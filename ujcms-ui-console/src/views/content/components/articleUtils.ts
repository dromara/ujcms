import { getAccessToken } from '@/utils/auth';

export const openArticleLink = (status: number, url: string, dynamicFullUrl: string) => {
  if (status === 0 || status === 1) {
    window.open(url);
  } else {
    dynamicFullUrl = dynamicFullUrl + '?preview=true';
    const index = dynamicFullUrl.lastIndexOf('/', dynamicFullUrl.lastIndexOf('/') - 1);
    const jwtLoginUrl = `${dynamicFullUrl.substring(0, index)}/auth/jwt/login?code=${getAccessToken()}&redirectUri=${encodeURIComponent(dynamicFullUrl)}`;
    window.open(jwtLoginUrl);
  }
};
