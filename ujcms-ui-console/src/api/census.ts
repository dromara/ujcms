import axios from '@/utils/request';

export const queryCensusPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/census', { params })).data;
export const queryCensus = async (id: string): Promise<any> => (await axios.get(`/backend/ext/census/${id}`)).data;
export const queryCensusIndicators = async (): Promise<any> => (await axios.get('/backend/ext/census/indicators')).data;
export const queryCensusItems = async (id: string): Promise<any> => (await axios.get(`/backend/ext/census/${id}/items`)).data;
export const createCensus = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/census', data)).data;
export const stopCensus = async (ids: string[]): Promise<any> => (await axios.post('/backend/ext/census/stop?_method=put', { ids })).data;
export const updateCensusScore = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/census/score?_method=put', data)).data;
export const deleteCensus = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/census?_method=delete', data)).data;

export const queryCensusIssuePage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/census-issue', { params })).data;
export const queryCensusIssue = async (id: string): Promise<any> => (await axios.get(`/backend/ext/census-issue/${id}`)).data;
export const processCensusIssue = async (ids: string[], status: number, processText?: string): Promise<any> =>
  (await axios.post('/backend/ext/census-issue/process?_method=put', { ids, status, processText })).data;
export const deleteCensusIssue = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/census-issue?_method=delete', data)).data;
