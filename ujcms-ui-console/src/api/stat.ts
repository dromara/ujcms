import axios from '@/utils/request';

export const queryTrendStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/trend-stat', { params })).data;
export const queryVisitedPageStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/visited-page-stat', { params })).data;
export const queryEntryPageStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/entry-page-stat', { params })).data;
export const queryVisitorStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/visitor-stat', { params })).data;
export const querySourceStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/source-stat', { params })).data;
export const queryCountryStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/country-stat', { params })).data;
export const queryProvinceStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/province-stat', { params })).data;
export const queryDeviceStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/device-stat', { params })).data;
export const queryOsStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/os-stat', { params })).data;
export const queryBrowserStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/browser-stat', { params })).data;
export const querySourceTypeStat = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/visit/source-type-stat', { params })).data;

export const queryArticleStatByUser = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/article-stat/by-user', { params })).data;
export const queryArticleStatByOrg = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/article-stat/by-org', { params })).data;
export const queryArticleStatByChannel = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/article-stat/by-channel', { params })).data;

export const queryPerformanceStatByUser = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/performance-stat/by-user', { params })).data;
export const queryPerformanceStatByOrg = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/performance-stat/by-org', { params })).data;
