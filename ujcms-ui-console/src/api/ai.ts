import axios from '@/utils/request';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';

export const queryConfigAi = async (): Promise<any> => (await axios.get('/backend/core/config/ai')).data;
export const updateConfigAi = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/ai?_method=put', data)).data;
export const testConfigAi = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/ai/test', data)).data;
export const testConfigAiMt = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/config/ai/mt-test', data)).data;

export const updateSiteAiSettings = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/core/site-settings/ai?_method=put', data)).data;
/** 站点 AI 功能页的全局就绪状态（全局开关、各功能服务商是否可解析），仅布尔值；权限 siteSettings:ai:update */
export const queryAiReadiness = async (): Promise<any> => (await axios.get('/backend/core/site-settings/ai/readiness')).data;

export const queryAiLogPage = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/ai-log', { params })).data;
export const updateAiLogAccepted = async (id: string): Promise<any> => (await axios.post(`/backend/ext/ai-log/${id}/accepted?_method=put`)).data;

export const aiScreen = async (data: { text: string }): Promise<any> => (await axios.post('/backend/ext/ai/screen', data)).data;

/** 批量判定接口的单次请求上限（服务端 AiModerationController.BATCH_MAX_IDS） */
const AI_MODERATION_BATCH_MAX = 100;
export const queryAiModerationBatch = async (type: string, refIds: string[]): Promise<any[]> => {
  // 列表页大小可达 800，须按服务端上限分批请求
  const chunks: string[][] = [];
  for (let i = 0; i < refIds.length; i += AI_MODERATION_BATCH_MAX) {
    chunks.push(refIds.slice(i, i + AI_MODERATION_BATCH_MAX));
  }
  const results = await Promise.all(
    chunks.map(
      async (ids) =>
        (await axios.get('/backend/commercial/ai-moderation', { params: { type, refIds: ids.join(',') } })).data,
    ),
  );
  return results.flat();
};
export const queryAiModerationPage = async (params?: Record<string, any>): Promise<any> =>
  (await axios.get('/backend/commercial/ai-moderation', { params })).data;
export const retryAiModeration = async (id: string): Promise<any> => (await axios.post(`/backend/commercial/ai-moderation/${id}/retry`)).data;
export const aiReplyDraft = async (data: { type: string; refId: string }): Promise<any> => (await axios.post('/backend/commercial/ai-reply-draft', data)).data;

export const aiArticleMeta = async (data: { title?: string; text: string; targets: string[] }): Promise<any> =>
  (await axios.post('/backend/ext/ai/article-meta', data)).data;
/** 候选标题（docs/spec/ai/editorial-assist.md §4）：返回 {titles: string[], logId}，最多 5 个、不含现有标题 */
export const aiArticleTitles = async (data: { title?: string; text: string }): Promise<any> =>
  (await axios.post('/backend/ext/ai/article-titles', data)).data;
/** 候选别名：返回 {slugs: string[], logId}，最多 3 个，服务端已 slug 化并去重；与智能SEO同一开关 */
export const aiArticleSlugs = async (data: { title: string; text?: string; id?: string }): Promise<any> =>
  (await axios.post('/backend/ext/ai/article-slug', data)).data;

/** 相关文章 AI 推荐（docs/spec/article-related/plan.md §4）：在启发式候选池上重排并给出理由，返回 {items, logId} */
export const aiRelatedArticles = async (data: {
  id?: string;
  title?: string;
  text?: string;
  channelId?: string;
  tagNames: string[];
  excludeIds: string[];
}, quiet = false): Promise<any> => (await axios.post('/backend/ext/ai/related-articles', data, { skipErrorHandler: quiet } as any)).data;

/**
 * AI 预翻译（docs/spec/ai/article-translate.md）。阻塞调用，最长可达服务商读取超时（600秒），
 * 必须覆盖 axios 默认 30 秒超时；signal 仅中止等待——服务端调用不会取消，token 照常计量
 */
export const aiTranslateArticle = async (
  data: { sourceArticleId: string; targetArticleId: string; provider?: string },
  signal?: AbortSignal,
): Promise<any> => (await axios.post('/backend/ext/ai/translate-article', data, { timeout: 630_000, signal })).data;
/**
 * 单篇预翻译可选的服务商（按目标站点）：{ providers: [{code,name,mt}], defaultProvider, reason }
 */
export const queryAiTranslateProviders = async (targetSiteId: string): Promise<any> =>
  (await axios.get('/backend/ext/ai/translate-article/providers', { params: { targetSiteId } })).data;

export interface AiRewriteHandlers {
  /** 增量文本（追加式） */
  onDelta: (text: string) => void;
  /** 正常结束（含取消后的部分结果不经此处；服务端 done 事件） */
  onDone: (data: { logId: string; promptTokens: number; completionTokens: number }) => void;
  /** 出错（错误代码 AiErrorCode 或本地化消息）。开始前与流中出错均经此处 */
  onError: (error: string) => void;
}

/**
 * 写作助手改写（docs/spec/ai/editorial-assist.md §5）。SSE 须 POST，EventSource 不支持，
 * 故用 fetch + ReadableStream 手工解析；中断经 AbortSignal（服务端按部分结果记录日志）。
 * 事件 data 均为 JSON（delta 为 JSON 字符串，避免换行在 SSE 分帧中丢失）
 */
export type AiRewriteMode = 'polish' | 'expand' | 'condense' | 'proofread' | 'continue';

export const aiRewrite = async (
  data: { mode: AiRewriteMode; text: string; format?: 'text' | 'html'; instruction?: string },
  handlers: AiRewriteHandlers,
  signal?: AbortSignal,
): Promise<void> => {
  let response: Response;
  try {
    response = await fetch(`${import.meta.env.VITE_BASE_API}/backend/ext/ai/rewrite`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders(), ...getSiteHeaders(), ...getLocaleHeaders() },
      body: JSON.stringify(data),
      signal,
    });
  } catch (e: any) {
    if (e?.name !== 'AbortError') {
      handlers.onError(String(e?.message ?? e));
    }
    return;
  }
  if (!response.ok || response.body == null) {
    let message = `HTTP ${response.status}`;
    try {
      const body = await response.json();
      message = body.message ?? body.error ?? message;
    } catch {
      // 保留 HTTP 状态信息
    }
    handlers.onError(message);
    return;
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let eventName = '';
  let dataLines: string[] = [];
  const dispatch = () => {
    if (dataLines.length === 0) {
      eventName = '';
      return;
    }
    // SSE 规范：多条 data 行以换行拼接
    const payload = dataLines.join('\n');
    dataLines = [];
    const name = eventName;
    eventName = '';
    try {
      if (name === 'delta') {
        handlers.onDelta(JSON.parse(payload));
      } else if (name === 'done') {
        handlers.onDone(JSON.parse(payload));
      } else if (name === 'error') {
        handlers.onError(JSON.parse(payload).code);
      }
    } catch {
      handlers.onError('INVALID_RESPONSE');
    }
  };
  const processLine = (line: string) => {
    if (line === '') {
      dispatch();
    } else if (line.startsWith('event:')) {
      eventName = line.substring(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.substring(5).replace(/^ /, ''));
    }
    // 其余字段（id:/retry:/注释）忽略
  };
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let index = buffer.search(/\r?\n/);
      while (index >= 0) {
        const line = buffer.substring(0, index);
        buffer = buffer.substring(index + (buffer.charAt(index) === '\r' ? 2 : 1));
        processLine(line);
        index = buffer.search(/\r?\n/);
      }
    }
    processLine(buffer);
    dispatch();
  } catch (e: any) {
    if (e?.name !== 'AbortError') {
      handlers.onError(String(e?.message ?? e));
    }
  }
};
