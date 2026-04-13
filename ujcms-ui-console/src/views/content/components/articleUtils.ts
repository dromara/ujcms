import { getAccessToken } from '@/utils/auth';

export const openArticleLink = (status: number, url: string, dynamicUrl: string) => {
  if (status === 0 || status === 1) {
    window.open(url);
  } else {
    dynamicUrl = dynamicUrl + '?preview=true';
    const index = dynamicUrl.lastIndexOf('/', dynamicUrl.lastIndexOf('/') - 1);
    const jwtLoginUrl = `${dynamicUrl.substring(0, index)}/auth/jwt/login?code=${getAccessToken()}&redirectUri=${encodeURIComponent(dynamicUrl)}`;
    window.open(jwtLoginUrl);
  }
};
