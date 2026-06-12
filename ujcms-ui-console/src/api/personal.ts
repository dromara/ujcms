import axios from '@/utils/request';

export const updatePersonalPassword = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/personal/password?_method=put', data)).data;

export const queryMachineCode = async (): Promise<any> => (await axios.get('/backend/core/machine/code')).data;
export const queryMachineLicense = async (): Promise<any> => (await axios.get('/backend/core/machine/license')).data;

export const querySystemInfo = async (): Promise<any> => (await axios.get('/backend/core/homepage/system-info')).data;
export const querySystemMonitor = async (): Promise<any> => (await axios.get('/backend/core/homepage/system-monitor')).data;
export const querySystemLoad = async (): Promise<any> => (await axios.get('/backend/core/homepage/system-load')).data;
export const queryGeneratedKey = async (): Promise<any> => (await axios.get('/backend/core/homepage/generated-key')).data;
export const queryContentStat = async (): Promise<any> => (await axios.get('/backend/core/homepage/content-stat')).data;
