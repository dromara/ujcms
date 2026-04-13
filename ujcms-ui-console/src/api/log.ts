import axios from '@/utils/request';

export const queryShortMessagePage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/short-message', { params })).data;
export const queryShortMessage = async (id: string): Promise<any> => (await axios.get(`/backend/core/short-message/${id}`)).data;
export const createShortMessage = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/short-message', data)).data;
export const updateShortMessage = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/short-message?_method=put', data)).data;
export const deleteShortMessage = async (data: string[]): Promise<any> => (await axios.post('/backend/core/short-message?_method=delete', data)).data;

export const queryLoginLogPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/login-log', { params })).data;
export const queryLoginLog = async (id: string): Promise<any> => (await axios.get(`/backend/core/login-log/${id}`)).data;
export const createLoginLog = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/login-log', data)).data;
export const updateLoginLog = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/login-log?_method=put', data)).data;
export const deleteLoginLog = async (data: string[]): Promise<any> => (await axios.post('/backend/core/login-log?_method=delete', data)).data;

export const queryOperationLogPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/core/operation-log', { params })).data;
export const queryOperationLog = async (id: string): Promise<any> => (await axios.get(`/backend/core/operation-log/${id}`)).data;
export const createOperationLog = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/operation-log', data)).data;
export const updateOperationLog = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/operation-log?_method=put', data)).data;
export const deleteOperationLog = async (data: string[]): Promise<any> => (await axios.post('/backend/core/operation-log?_method=delete', data)).data;
