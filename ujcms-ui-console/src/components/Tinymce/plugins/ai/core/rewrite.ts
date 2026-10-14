import { Editor } from 'tinymce';
import i18n from '@/i18n';
import { aiRewrite, updateAiLogAccepted } from '@/api/ai';
import type { AiRewriteMode } from '@/api/ai';
import { annotateDiff, htmlToText, sanitizeHtml } from './diff';

const t = (key: string, named?: Record<string, unknown>): string => (named ? i18n.global.t(key, named) : i18n.global.t(key));

export interface RewriteMode {
  value: AiRewriteMode;
  /** 模式显示名的 i18n 键（`editor.ai.mode.*`），显示时再翻译 */
  key: string;
}

/** 模式显示名（润色/扩写/…），随控制台语言 */
export const modeText = (mode: RewriteMode): string => t(mode.key);

// docs/spec/ai/editorial-assist.md §6.1：润色/扩写/缩写/校对/续写
export const REWRITE_MODES: RewriteMode[] = [
  { value: 'polish', key: 'editor.ai.mode.polish' },
  { value: 'expand', key: 'editor.ai.mode.expand' },
  { value: 'condense', key: 'editor.ai.mode.condense' },
  { value: 'proofread', key: 'editor.ai.mode.proofread' },
  { value: 'continue', key: 'editor.ai.mode.continue' },
];

// 与服务端 REWRITE_TEXT_MAX_LENGTH / REWRITE_HTML_MAX_LENGTH 一致，超长在本地先行拦截
const TEXT_MAX_LENGTH = 12000;
const HTML_MAX_LENGTH = 30000;
// 流式预览的刷新节拍：每个增量都重写 iframe 文档既闪烁又浪费，合并后按节拍刷新
const PREVIEW_INTERVAL = 150;

// 服务端错误码 → 提示文案（`editor.ai.error.<code>`）；语言包为扁平键，te() 恒为 false，用 t(key) !== key 判断是否登记，未登记的码原样显示
const errorMessage = (code: string): string => {
  const key = `editor.ai.error.${code}`;
  const message = t(key);
  return message !== key ? message : code;
};

const escapeHtml = (text: string): string => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/**
 * 纯文本转 HTML：空行分段，段内换行为 <br>。
 * inline 为真（选区位于段落内部、不含标签）时不生成段落标签，避免替换时把所在段落拆开
 */
const textToHtml = (text: string, inline = false): string => {
  if (inline) {
    return escapeHtml(text.trim()).replace(/\n{2,}/g, '<br /><br />').replace(/\n/g, '<br />');
  }
  return text
    .split(/\n{2,}/)
    .filter((paragraph) => paragraph.trim() !== '')
    .map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\n/g, '<br />')}</p>`)
    .join('');
};

// 模型偶尔无视指令用代码块包裹输出：剥除开头的 ```html 围栏与结尾围栏（流式期间结尾可能尚未到达）
const stripFence = (text: string): string => text.replace(/^\s*```[a-zA-Z]*[ \t]*\n?/, '').replace(/\n?[ \t]*```\s*$/, '');

// 预览 iframe 内的补充样式：改动标注、生成中的等待提示；编辑器 content_css 另行内联
const PREVIEW_STYLE = [
  'body{margin:0;padding:8px 12px;word-break:break-word}',
  'body[contenteditable]{outline:none;min-height:calc(100vh - 16px)}',
  'img{max-width:100%;height:auto}',
  'ins{background:#dcffe4;color:#0a6b2e;text-decoration:none}',
  'del{background:#ffe0e3;color:#b31d28}',
  '.ai-notice{margin:0 0 12px;padding:8px 12px;border-radius:4px;background:#e8f5ec;color:#0a6b2e;font-size:14px}',
  '.ai-waiting{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;height:60vh;color:#8a8f98;font-size:14px}',
  '.ai-spinner{width:24px;height:24px;border:3px solid #dfe3e8;border-top-color:#006ce7;border-radius:50%;animation:ai-spin .8s linear infinite}',
  '@keyframes ai-spin{to{transform:rotate(360deg)}}',
].join('\n');
const waitingHtml = (): string => `<div class="ai-waiting"><div class="ai-spinner"></div><div>${escapeHtml(t('editor.ai.waiting'))}</div></div>`;
// 建议与原文逐字相同时的提示：校对最常见（文本本就没有错），明确告知而不是让人从“没有红绿标注”中自行推断
const unchangedNotice = (mode: RewriteMode): string =>
  `<div class="ai-notice">${escapeHtml(t(mode.value === 'proofread' ? 'editor.ai.unchanged.proofread' : 'editor.ai.unchanged'))}</div>`;

// 编辑器的 content_css 只抓取一次并内联：流式预览频繁重写 iframe 文档，外链样式表会反复加载并闪烁
const contentCssCache = new Map<string, Promise<string>>();
const loadContentCss = (editor: Editor): Promise<string> => {
  const urls = editor.options.get('content_css') ?? [];
  const key = urls.join('|');
  let cached = contentCssCache.get(key);
  if (cached == null) {
    cached = Promise.all(
      urls.map((url) =>
        fetch(url)
          .then((response) => (response.ok ? response.text() : ''))
          .catch(() => ''),
      ),
    ).then((list) => list.join('\n'));
    contentCssCache.set(key, cached);
  }
  return cached;
};

// editable：生成完成后的建议文档以 contenteditable 的 body 呈现，可直接修改，应用时从 iframe 读回
const previewDocument = (css: string, body: string, editable = false): string =>
  `<!DOCTYPE html><html><head><meta charset="utf-8"><style>${css}\n${PREVIEW_STYLE}</style></head><body class="mce-content-body"${
    editable ? ' contenteditable="true"' : ''
  }>${body}</body></html>`;

// 对话框布局：皮肤里的二列网格不参与纵向伸展，两个预览 iframe 只剩 200px 的最小高度；给本对话框加类名后让网格撑满
const DIALOG_CLASS = 'ujcms-ai-rewrite';
const DIALOG_STYLE_ID = 'ujcms-ai-rewrite-style';
const DIALOG_STYLE = [
  `.tox-dialog.${DIALOG_CLASS} .tox-form__grid{flex:1 1 0%;min-height:0;align-content:stretch}`,
  `.tox-dialog.${DIALOG_CLASS} .tox-form__grid>.tox-form__group{min-height:0}`,
  `.tox-dialog.${DIALOG_CLASS} .tox-form__grid .tox-navobj{min-height:0}`,
  `.tox-dialog.${DIALOG_CLASS} .tox-form__grid iframe{min-height:0}`,
].join('\n');
const markDialog = (): Element | null => {
  if (document.getElementById(DIALOG_STYLE_ID) == null) {
    const style = document.createElement('style');
    style.id = DIALOG_STYLE_ID;
    style.textContent = DIALOG_STYLE;
    document.head.appendChild(style);
  }
  const dialogs = document.querySelectorAll('.tox-dialog');
  const dialog = dialogs[dialogs.length - 1] ?? null;
  dialog?.classList.add(DIALOG_CLASS);
  return dialog;
};

interface RewriteContext {
  /** 送入模型的内容：HTML 片段或纯文本 */
  source: string;
  /** 是否以 HTML 片段模式改写（服务端追加保标签规则） */
  useHtml: boolean;
  /** 是否作用于选区（否则为全文） */
  useSelection: boolean;
  /** 选区位于段落内部且不含标签：结果不生成段落标签 */
  inline: boolean;
  /** 预览 iframe 使用的样式（编辑器 content_css + content_style） */
  css: string;
}

/**
 * 改写对话框：原文与 AI 建议左右对照，建议随 SSE 增量在预览中渐进渲染；生成完成后按字符级对比在两侧标注
 * 删除与新增（续写除外），建议面板随即可直接编辑（标注只作提示，应用时去除）。可填写补充要求后「重新生成」。
 * 任何内容都不会未经点击进入正文；应用（续写追加到正文末尾，其余替换选区或全文）时标记采纳并通知 ai_accept_handler
 * （docs/spec/ai/editorial-assist.md §6.1）
 */
const openDialog = (editor: Editor, mode: RewriteMode, ctx: RewriteContext): void => {
  const doc = (body: string, editable = false) => previewDocument(ctx.css, body, editable);
  const originalHtml = ctx.useHtml ? sanitizeHtml(ctx.source) : textToHtml(ctx.source, ctx.inline);
  const toHtml = (raw: string) => (ctx.useHtml ? sanitizeHtml(stripFence(raw)) : textToHtml(raw, ctx.inline));
  const hasText = (html: string) => htmlToText(html).trim() !== '';
  // 续写追加到正文末尾，全是新增内容，没有可对照的改动；其余模式替换选区或全文
  const isContinue = mode.value === 'continue';
  const canDiff = !isContinue;
  // TinyMCE 把 iframe 组件的 label 写入 title 属性，据此在对话框内找到建议面板
  const suggestionLabel = t(canDiff ? 'editor.ai.suggestion.diff' : 'editor.ai.suggestion.editable');
  let dialogEl: Element | null = null;

  // 生成完成后建议文档的 body 为 contenteditable：应用时从 iframe 读回编辑后的 HTML，去掉 <ins>/<del> 标注包裹；
  // 文档尚未写入完成版（Safari/Firefox 下 TinyMCE 节流写入）或找不到面板时返回 null，退回未编辑的建议
  const readEditedSuggestion = (): string | null => {
    const frame = dialogEl?.querySelector<HTMLIFrameElement>(`iframe[title="${suggestionLabel}"]`);
    const body = frame?.contentDocument?.body;
    if (body == null || !body.hasAttribute('contenteditable')) {
      return null;
    }
    const clone = body.cloneNode(true) as HTMLElement;
    clone.querySelectorAll('ins, del').forEach((mark) => mark.replaceWith(...Array.from(mark.childNodes)));
    return clone.innerHTML;
  };

  let aborter: AbortController | null = null;
  let streaming = false;
  let suggestion = '';
  let logId: string | null = null;
  let previewTimer: number | null = null;

  // TinyMCE 6 对话框：按钮/组件的可用性键为 enabled（v5 的 disabled 会被忽略），实例 API 为 setEnabled。
  // 「停止生成」= 中止并保留已生成的部分，「放弃」= 中止并关闭；尚无输出时二者等价，故停止按钮到首个 delta 才启用
  // 每种模式只有一个应用按钮，按钮文字即结果去向：续写「追加到末尾」，其余「替换选中内容 / 替换全文」。
  // 此前另有「插入」：有选区时 insertContent 同样先删除选区，与替换无异；全文模式则把整篇复制一份插到光标处
  const applyText = t(isContinue ? 'editor.ai.button.append' : ctx.useSelection ? 'editor.ai.button.replaceSelection' : 'editor.ai.button.replaceAll');
  const buttons: any[] = [
    { type: 'custom', name: 'cancelStream', text: t('editor.ai.button.stop'), enabled: false },
    { type: 'cancel', name: 'discard', text: t('editor.ai.button.discard') },
    { type: 'custom', name: 'regenerate', text: t('editor.ai.button.regenerate'), enabled: false },
    { type: 'custom', name: 'apply', text: applyText, primary: true, enabled: false },
  ];

  const cancelPreview = () => {
    if (previewTimer != null) {
      window.clearTimeout(previewTimer);
      previewTimer = null;
    }
  };
  const schedulePreview = (api: any) => {
    if (previewTimer == null) {
      previewTimer = window.setTimeout(() => {
        previewTimer = null;
        api.setData({ suggestion: doc(toHtml(suggestion)) });
      }, PREVIEW_INTERVAL);
    }
  };

  const finishStream = (api: any) => {
    if (!streaming) {
      return;
    }
    streaming = false;
    cancelPreview();
    const html = toHtml(suggestion);
    const hasResult = hasText(html);
    // 可见文字与原文完全一致：显示提示，建议面板不可编辑，应用按钮禁用（应用只会是空操作，或悄悄改动看不见的结构）
    let unchanged = false;
    if (hasResult && canDiff) {
      const marked = annotateDiff(originalHtml, html);
      unchanged = !marked.changed;
      if (unchanged) {
        api.setData({ original: doc(originalHtml), suggestion: doc(unchangedNotice(mode) + html) });
      } else {
        api.setData({ original: doc(marked.original), suggestion: doc(marked.suggestion, true) });
      }
    } else {
      api.setData({ suggestion: doc(hasResult ? html : '', hasResult) });
    }
    api.setEnabled('cancelStream', false);
    api.setEnabled('regenerate', true);
    api.setEnabled('apply', hasResult && !unchanged);
  };

  // 首次生成与「重新生成」共用：重新生成时带上补充要求，预览回到等待状态
  const start = (api: any) => {
    aborter?.abort();
    aborter = new AbortController();
    streaming = true;
    suggestion = '';
    logId = null;
    api.setData({ original: doc(originalHtml), suggestion: doc(waitingHtml()) });
    api.setEnabled('cancelStream', false);
    api.setEnabled('regenerate', false);
    api.setEnabled('apply', false);
    const instruction = String(api.getData().instruction ?? '').trim();
    aiRewrite(
      { mode: mode.value, text: ctx.source, format: ctx.useHtml ? 'html' : 'text', instruction: instruction || undefined },
      {
        onDelta: (text) => {
          const first = suggestion === '';
          suggestion += text;
          if (first) {
            api.setEnabled('cancelStream', true);
          }
          schedulePreview(api);
        },
        onDone: (data) => {
          logId = data.logId;
          finishStream(api);
        },
        onError: (error) => {
          finishStream(api);
          editor.windowManager.alert(t('editor.ai.failed', { mode: modeText(mode), reason: errorMessage(error) }));
        },
      },
      aborter.signal,
    );
  };

  const apply = (api: any) => {
    const edited = readEditedSuggestion();
    const html = edited != null ? sanitizeHtml(edited) : toHtml(suggestion);
    if (!hasText(html)) {
      api.close();
      return;
    }
    if (isContinue) {
      // 续写是全文之后的内容，与光标位置无关：未点入编辑器时光标在首字前，insertContent 会把续写放到文章开头，
      // 光标在段落中间则把段落拆开。按字符串拼接经解析器写回，新段落一律成为正文末尾的同级块
      editor.setContent(editor.getContent() + html);
      const last = editor.getBody().lastElementChild;
      if (last instanceof HTMLElement) {
        editor.selection.select(last, true);
        editor.selection.collapse(false);
        editor.selection.scrollIntoView(last);
      }
    } else if (ctx.useSelection) {
      editor.selection.setContent(html);
    } else {
      editor.setContent(html);
    }
    editor.undoManager.add();
    // 采纳标记：区分“已生成”与“已采用”（审计与产品信号），失败不影响编辑
    if (logId != null) {
      updateAiLogAccepted(logId).catch(() => undefined);
    }
    const onAccept = editor.getParam('ai_accept_handler');
    if (typeof onAccept === 'function') {
      onAccept(mode.value);
    }
    api.close();
  };

  const dialog = editor.windowManager.open({
    title: t('editor.ai.title', { mode: modeText(mode) }),
    size: 'large',
    body: {
      type: 'panel',
      items: [
        {
          type: 'grid',
          columns: 2,
          items: [
            { type: 'iframe', name: 'original', label: t('editor.ai.original'), border: true },
            { type: 'iframe', name: 'suggestion', label: suggestionLabel, border: true, streamContent: true },
          ],
        },
        {
          type: 'input',
          name: 'instruction',
          label: t('editor.ai.instruction'),
          placeholder: t('editor.ai.instruction.placeholder'),
        },
      ],
    },
    initialData: { original: doc(originalHtml), suggestion: doc(waitingHtml()), instruction: '' },
    buttons,
    onAction: (api: any, details: any) => {
      if (details.name === 'cancelStream') {
        aborter?.abort();
        const hadResult = hasText(toHtml(suggestion));
        finishStream(api);
        // 已到达的输出仅为空白（或围栏）时无内容可留，直接关闭
        if (!hadResult) {
          api.close();
        }
      } else if (details.name === 'regenerate') {
        start(api);
      } else if (details.name === 'apply') {
        apply(api);
      }
    },
    onClose: () => {
      cancelPreview();
      // 生成中关闭视同取消：服务端按部分结果记录日志
      if (streaming) {
        aborter?.abort();
      }
    },
  } as any);
  dialogEl = markDialog();
  start(dialog);
};

/**
 * 打开改写对话框。作用范围：续写始终以全文纯文本为上下文、追加到正文末尾；其余模式优先选区，否则全文。
 * 全文及含标签的选区以 HTML 片段送入（服务端追加保标签规则，替换后不丢失图片、标题、链接与表格）；
 * 不含标签的选区退回纯文本，避免模型输出被当作 HTML 解析
 */
export const openRewriteDialog = (editor: Editor, mode: RewriteMode): void => {
  const selectionText = editor.selection.getContent({ format: 'text' }) as string;
  const useSelection = mode.value !== 'continue' && selectionText.trim() !== '';
  let source: string;
  let useHtml: boolean;
  let inline = false;
  if (mode.value === 'continue') {
    source = editor.getContent({ format: 'text' }) as string;
    useHtml = false;
  } else if (useSelection) {
    const html = editor.selection.getContent() as string;
    useHtml = /<[a-z][^>]*>/i.test(html);
    source = useHtml ? html : selectionText;
    inline = !useHtml;
  } else {
    source = editor.getContent() as string;
    useHtml = true;
  }
  const plain = useHtml ? htmlToText(source) : source;
  if (!plain.trim()) {
    editor.windowManager.alert(t('editor.ai.empty'));
    return;
  }
  const maxLength = useHtml ? HTML_MAX_LENGTH : TEXT_MAX_LENGTH;
  if (source.length > maxLength) {
    editor.windowManager.alert(t('editor.ai.tooLong', { max: maxLength }));
    return;
  }
  loadContentCss(editor).then((contentCss) => {
    const css = `${contentCss}\n${editor.options.get('content_style') ?? ''}`;
    openDialog(editor, mode, { source, useHtml, useSelection, inline, css });
  });
};
