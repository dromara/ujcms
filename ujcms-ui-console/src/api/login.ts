import axios from '@/utils/request';

export interface LoginParam {
  username: string;
  password: string;
  browser?: boolean;
}

export interface RefreshTokenParam {
  refreshToken: string;
  browser?: boolean;
}

export const accountLogin = async (data: LoginParam): Promise<any> => (await axios.post('/auth/jwt/login', data)).data;
export const accountLogout = async (refreshToken: string): Promise<any> => (await axios.post('/auth/jwt/logout', { refreshToken })).data;
export const accountRefreshToken = async (data: RefreshTokenParam): Promise<any> => (await axios.post('/auth/jwt/refresh-token', data)).data;
export const queryCurrentUser = async (): Promise<any> => (await axios.get('/env/current-user')).data;
export const queryCurrentSiteList = async (): Promise<any> => (await axios.get('/env/current-site-list')).data;
export const queryClientPublicKey = async (): Promise<any> => (await axios.get('/env/client-public-key')).data;
export const queryConfig = async (): Promise<any> => (await axios.get('/env/config')).data;
export const queryCaptcha = async (): Promise<any> => (await axios.get('/captcha')).data;
export const queryIsDisplayCaptcha = async (): Promise<any> => (await axios.get('/captcha/is-display')).data;
export const sendMobileMessage = async (captchaToken: string, captcha: string, mobile: string, usage: number): Promise<any> =>
  (await axios.post('/sms/mobile', { captchaToken, captcha, receiver: mobile, usage })).data;
export const queryIsMfaLogin = async (): Promise<any> => (await axios.get('/env/is-mfa-login')).data;
export const tryCaptcha = async (token: string, captcha: string): Promise<any> => (await axios.get('/captcha/try', { params: { token, captcha } })).data;
export const mobileNotExist = async (mobile: string): Promise<any> => (await axios.get('/user/mobile-not-exist', { params: { mobile } })).data;
export const updatePassword = async (data: Record<string, any>): Promise<any> => (await axios.post('/update-password?_method=put', data)).data;
