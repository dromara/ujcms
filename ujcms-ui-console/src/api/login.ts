import axios from '@/utils/request';

export interface LoginParam {
  username: string;
  password: string;
  captcha?: string;
  captchaToken?: string;
  browser?: boolean;
}

export interface SecondFactorVerifyParam {
  mfaToken: string;
  shortMessageId: string;
  code: string;
}

export interface RefreshTokenParam {
  refreshToken: string;
  browser?: boolean;
}

export const accountLogin = async (data: LoginParam): Promise<any> => (await axios.post('/auth/jwt/login', data)).data;
export const accountLogout = async (refreshToken: string): Promise<any> => (await axios.post('/auth/jwt/logout', { refreshToken })).data;
export const accountRefreshToken = async (data: RefreshTokenParam): Promise<any> => (await axios.post('/auth/jwt/refresh-token', data)).data;
export const queryBackendState = async (): Promise<any> => (await axios.get('/env/backend-state')).data;
export const queryCurrentSiteList = async (): Promise<any> => (await axios.get('/env/current-site-list')).data;
export const queryClientPublicKey = async (): Promise<any> => (await axios.get('/env/client-public-key')).data;
export const queryConfig = async (): Promise<any> => (await axios.get('/env/config')).data;
export const queryCaptcha = async (): Promise<any> => (await axios.get('/captcha')).data;
export const queryIsDisplayCaptcha = async (): Promise<any> => (await axios.get('/captcha/is-display')).data;
// 双因子登录：口令通过后登录接口返回 status 1020 与 mfaToken，凭它发送、校验短信验证码。验证码发往账号登记的手机号码
export const sendSecondFactorCode = async (mfaToken: string): Promise<any> => (await axios.post('/auth/jwt/mfa/send', { mfaToken })).data;
export const verifySecondFactorCode = async (data: SecondFactorVerifyParam): Promise<any> => (await axios.post('/auth/jwt/mfa/verify', data)).data;
export const tryCaptcha = async (token: string, captcha: string): Promise<any> => (await axios.get('/captcha/try', { params: { token, captcha } })).data;
export const updatePassword = async (data: Record<string, any>): Promise<any> => (await axios.post('/update-password?_method=put', data)).data;
