import axios from '@/utils/request';

// 公众号管理
export const queryWechatAccountList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-account', { params })).data;
export const queryWechatAccount = async (id: string): Promise<any> => (await axios.get(`/backend/ext/wechat-account/${id}`)).data;
export const createWechatAccount = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-account', data)).data;
export const updateWechatAccount = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-account?_method=put', data)).data;
export const updateWechatAccountOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/ext/wechat-account/update-order', { fromId, toId })).data;
export const deleteWechatAccount = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-account?_method=delete', data)).data;
export const previewWechatAccountStyle = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-account/style-preview', data)).data;

// 推送记录
export const queryWechatArticlePage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-article', { params })).data;
export const pushWechatArticle = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-article/push', data)).data;
export const previewWechatArticle = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-article/preview', data)).data;
export const publishWechatArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-article/publish', data)).data;
export const refreshWechatArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-article/refresh', data)).data;
export const deleteWechatArticle = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-article?_method=delete', data)).data;

// 素材管理
export const queryWechatMaterialList = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-material', { params })).data;
export const queryWechatMaterialCount = async (accountId: string): Promise<any> => (await axios.get('/backend/ext/wechat-material/count', { params: { accountId } })).data;
export const downloadWechatMaterial = async (params: Record<string, any>): Promise<Blob> =>
  (await axios.get('/backend/ext/wechat-material/download', { params, responseType: 'blob', skipErrorHandler: true } as any)).data;
export const queryWechatMaterialVideo = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-material/video', { params })).data;
export const uploadWechatMaterial = async (data: FormData): Promise<any> => (await axios.post('/backend/ext/wechat-material', data)).data;
export const deleteWechatMaterial = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-material?_method=delete', data)).data;

// 草稿箱
export const queryWechatDraftList = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-draft', { params })).data;
export const publishWechatDraft = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-draft/publish', data)).data;
export const deleteWechatDraft = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-draft?_method=delete', data)).data;

// 已发布
export const queryWechatPublishList = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-publish', { params })).data;
export const deleteWechatPublish = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-publish?_method=delete', data)).data;

// 自定义菜单
export const queryWechatMenu = async (accountId: string): Promise<any> => (await axios.get('/backend/ext/wechat-menu', { params: { accountId } })).data;
export const publishWechatMenu = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-menu?_method=put', data)).data;
export const deleteWechatMenu = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-menu?_method=delete', data)).data;

// 粉丝管理
export const queryWechatFanList = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-fan', { params })).data;
export const updateWechatFanRemark = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-fan/remark?_method=put', data)).data;
export const taggingWechatFan = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-fan/tagging?_method=put', data)).data;
export const blacklistWechatFan = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-fan/blacklist?_method=put', data)).data;

// 自动回复
export const queryWechatReplyList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-reply', { params })).data;
export const queryWechatReply = async (id: string): Promise<any> => (await axios.get(`/backend/ext/wechat-reply/${id}`)).data;
export const createWechatReply = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-reply', data)).data;
export const updateWechatReply = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-reply?_method=put', data)).data;
export const updateWechatReplyOrder = async (fromId: string, toId: string): Promise<any> => (await axios.post('/backend/ext/wechat-reply/update-order', { fromId, toId })).data;
export const deleteWechatReply = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-reply?_method=delete', data)).data;

// 消息记录
export const queryWechatMessagePage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-message', { params })).data;
export const deleteWechatMessage = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-message?_method=delete', data)).data;

// 带参数二维码
export const queryWechatQrcodePage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-qrcode', { params })).data;
export const createWechatQrcode = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-qrcode', data)).data;
export const updateWechatQrcode = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-qrcode?_method=put', data)).data;
export const deleteWechatQrcode = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-qrcode?_method=delete', data)).data;

// 模板消息
export const queryWechatTemplateList = async (accountId: string): Promise<any> => (await axios.get('/backend/ext/wechat-template', { params: { accountId } })).data;
export const queryWechatTemplateIndustry = async (accountId: string): Promise<any> => (await axios.get('/backend/ext/wechat-template/industry', { params: { accountId } })).data;
export const sendWechatTemplate = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-template/send', data)).data;
export const deleteWechatTemplate = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-template?_method=delete', data)).data;
export const queryWechatTemplateLogPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-template-log', { params })).data;
export const deleteWechatTemplateLog = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-template-log?_method=delete', data)).data;

// 群发管理
export const queryWechatMassPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-mass', { params })).data;
export const queryWechatMassQuota = async (accountId: string): Promise<any> => (await axios.get('/backend/ext/wechat-mass/quota', { params: { accountId } })).data;
export const sendWechatMass = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-mass', data)).data;
export const previewWechatMass = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-mass/preview', data)).data;
export const refreshWechatMass = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-mass/refresh', data)).data;
export const revokeWechatMass = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-mass/revoke', data)).data;
export const deleteWechatMass = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/wechat-mass?_method=delete', data)).data;

// 数据统计
export const queryWechatStatUser = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-stat/user', { params })).data;
export const queryWechatStatArticle = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-stat/article', { params })).data;
export const queryWechatStatInterface = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-stat/interface', { params })).data;

// 标签管理
export const queryWechatTagList = async (accountId: string): Promise<any> => (await axios.get('/backend/ext/wechat-tag', { params: { accountId } })).data;
export const queryWechatTagUserList = async (params: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/wechat-tag/user', { params })).data;
export const createWechatTag = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-tag', data)).data;
export const updateWechatTag = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-tag?_method=put', data)).data;
export const deleteWechatTag = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/wechat-tag?_method=delete', data)).data;
