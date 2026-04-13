import axios from '@/utils/request';

export const queryMessageBoardPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/message-board', { params })).data;
export const queryMessageBoardUnreviewedCount = async (params?: Record<string, any>): Promise<any> =>
  (await axios.get('/backend/ext/message-board/unreviewed-count', { params })).data;
export const queryMessageBoard = async (id: string): Promise<any> => (await axios.get(`/backend/ext/message-board/${id}`)).data;
export const createMessageBoard = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/message-board', data)).data;
export const updateMessageBoard = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/message-board?_method=put', data)).data;
export const updateMessageBoardStatus = async (ids: string[], status: number): Promise<any> =>
  (await axios.post('/backend/ext/message-board/status?_method=put', { ids, status })).data;
export const deleteMessageBoard = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/message-board?_method=delete', data)).data;

export const queryVotePage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/vote', { params })).data;
export const queryVote = async (id: string): Promise<any> => (await axios.get(`/backend/ext/vote/${id}`)).data;
export const createVote = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/vote', data)).data;
export const updateVote = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/vote?_method=put', data)).data;
export const updateVoteOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/ext/vote/update-order', { fromId, toId })).data;
export const deleteVote = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/vote?_method=delete', data)).data;

export const querySurveyPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/survey', { params })).data;
export const querySurvey = async (id: string): Promise<any> => (await axios.get(`/backend/ext/survey/${id}`)).data;
export const createSurvey = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/survey', data)).data;
export const updateSurvey = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/survey?_method=put', data)).data;
export const updateSurveyOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/ext/survey/update-order', { fromId, toId })).data;
export const deleteSurvey = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/survey?_method=delete', data)).data;
export const querySurveyOptionFeedbackPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/survey/option-feedback', { params })).data;
export const querySurveyItemFeedbackPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/survey/item-feedback', { params })).data;
export const updateSurveyItemFeedback = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/survey/item-feedback?_method=put', data)).data;

export const queryExamplePage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/example', { params })).data;
export const queryExample = async (id: string): Promise<any> => (await axios.get(`/backend/ext/example/${id}`)).data;
export const createExample = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/example', data)).data;
export const updateExample = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/example?_method=put', data)).data;
export const deleteExample = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/example?_method=delete', data)).data;

export const queryCollectionPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/collection', { params })).data;
export const queryCollection = async (id: string): Promise<any> => (await axios.get(`/backend/ext/collection/${id}`)).data;
export const createCollection = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/collection', data)).data;
export const updateCollection = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/collection?_method=put', data)).data;
export const updateCollectionOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/ext/collection/update-order', { fromId, toId })).data;
export const deleteCollection = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/collection?_method=delete', data)).data;
export const startCollection = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/collection/start?_method=put', data)).data;
export const pauseCollection = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/collection/pause?_method=put', data)).data;
export const stopCollection = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/collection/stop?_method=put', data)).data;
export const collectionSetupListUrls = async (listUrls: string, pageBegin: number, pageEnd: number, listDesc: boolean): Promise<any> =>
  (await axios.get(`/backend/ext/collection/setup/list-urls`, { params: { listUrls, pageBegin, pageEnd, listDesc } })).data;
export const collectionSetupDetailUrls = async (
  listUrls: string,
  pageBegin: number,
  pageEnd: number,
  userAgent: string,
  charset: string,
  listAreaPattern: string,
  itemUrlPattern: string,
  itemUrlReg: boolean,
  itemUrlJs: boolean,
): Promise<any> =>
  (
    await axios.get(`/backend/ext/collection/setup/detail-urls`, {
      params: { listUrls, pageBegin, pageEnd, userAgent, charset, listAreaPattern, itemUrlPattern, itemUrlReg, itemUrlJs },
    })
  ).data;
export const collectionSetupFetchContent = async (url: string, userAgent: string, charset: string): Promise<any> =>
  (await axios.get(`/backend/ext/collection/setup/fetch-content`, { params: { url, userAgent, charset } })).data;
export const collectionSetupMatch = async (text: string, texts: string[] | undefined, pattern: string, multi?: boolean, reg?: boolean, js?: boolean): Promise<any> =>
  (await axios.post(`/backend/ext/collection/setup/match?_method=put`, { text, texts, pattern, multi, reg, js })).data;
export const collectionSetupFilter = async (text: string, texts: string[] | undefined, filter: string, multi?: boolean): Promise<any> =>
  (await axios.post(`/backend/ext/collection/setup/filter?_method=put`, { text, texts, filter, multi })).data;
