import axios from '@/utils/request';

export const updatePersonalPassword = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/personal/password?_method=put', data)).data;

export const querySystemInfo = async (): Promise<any> => (await axios.get('/backend/core/homepage/system-info')).data;
export const querySystemMonitor = async (): Promise<any> => (await axios.get('/backend/core/homepage/system-monitor')).data;
export const querySystemLoad = async (): Promise<any> => (await axios.get('/backend/core/homepage/system-load')).data;
export const queryGeneratedKey = async (): Promise<any> => (await axios.get('/backend/core/homepage/generated-key')).data;
export const queryContentStat = async (): Promise<any> => (await axios.get('/backend/core/homepage/content-stat')).data;

// 控制台偏好（docs/spec/console-shell）：每个后台用户读写自己的偏好，接口只要求 backend 权限
export const queryPreferences = async (): Promise<any> => (await axios.get('/backend/core/personal/preferences')).data;
export const updatePreferences = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/personal/preferences?_method=put', data)).data;
