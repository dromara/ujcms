import { getProcessor } from 'bytemd';
import type { BytemdPlugin } from 'bytemd';
import TurndownService from 'turndown';
import { imageUploadUrl } from '@/api/config';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import { getLocaleHeaders } from '@/i18n';

let turndown: TurndownService | undefined;

const getTurndown = (): TurndownService => {
  if (!turndown) {
    turndown = new TurndownService({
      headingStyle: 'atx',
      codeBlockStyle: 'fenced',
      hr: '---',
      bulletListMarker: '-',
      emDelimiter: '*',
    });
  }
  return turndown;
};

/** 旧数据：仅有 HTML、无 markdown 源时，转换为 markdown 写入编辑器 */
export const htmlToMarkdown = (html: string): string => {
  if (!html) {
    return '';
  }
  try {
    return getTurndown().turndown(html);
  } catch (err) {
    if (import.meta.env.MODE !== 'production') {
      console.warn('HTML→Markdown conversion failed', err);
    }
    return '';
  }
};

/**
 * Renders markdown to sanitized HTML through ByteMD's own remark/rehype
 * pipeline, so the stored `text` is byte-identical to the editor preview.
 * Reuses one processor per plugin set (keyed by reference) to avoid rebuilding
 * the unified processor on every keystroke.
 */
let cachedPlugins: BytemdPlugin[] | undefined;
let cachedProcessor: ReturnType<typeof getProcessor> | undefined;

export const markdownToHtml = (markdown: string, plugins: BytemdPlugin[]): string => {
  if (!markdown) {
    return '';
  }
  if (plugins !== cachedPlugins || !cachedProcessor) {
    cachedPlugins = plugins;
    cachedProcessor = getProcessor({ plugins });
  }
  try {
    return String(cachedProcessor.processSync(markdown));
  } catch (err) {
    if (import.meta.env.MODE !== 'production') {
      console.warn('Markdown→HTML conversion failed', err);
    }
    return '';
  }
};

// Portions derived from TinyMCE 6 (MIT, Copyright (c) 2022 Ephox Corporation DBA Tiny Technologies, Inc.; see NOTICE):
// this image upload handler is adapted from the editor core's default upload handler.
const uploadOne = (file: File): Promise<string | undefined> =>
  new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', imageUploadUrl);
    Object.entries({ ...getSiteHeaders(), ...getLocaleHeaders() }).forEach(([key, value]: any) => xhr.setRequestHeader(key, value));
    Object.entries(getAuthHeaders()).forEach(([key, value]: any) => xhr.setRequestHeader(key, value));

    xhr.onload = () => {
      if (xhr.status < 200 || xhr.status >= 300) {
        ElMessageBox.alert(`HTTP Error: ${xhr.status}`, { type: 'warning' });
        resolve(undefined);
        return;
      }
      let json: any;
      try {
        json = JSON.parse(xhr.responseText);
      } catch {
        ElMessageBox.alert(`Invalid JSON: ${xhr.responseText}`, { type: 'warning' });
        resolve(undefined);
        return;
      }
      if (!json || typeof json.url !== 'string') {
        ElMessageBox.alert(`Invalid JSON: ${xhr.responseText}`, { type: 'warning' });
        resolve(undefined);
        return;
      }
      resolve(json.url);
    };

    xhr.onerror = () => {
      ElMessageBox.alert(`Image upload failed due to a XHR Transport error. Code: ${xhr.status}`, { type: 'warning' });
      resolve(undefined);
    };

    const formData = new FormData();
    formData.append('file', file);
    xhr.send(formData);
  });

/**
 * ByteMD `uploadImages` handler. Posts each file to the CMS image upload
 * endpoint and returns the inserted image descriptors.
 */
export const uploadImages = async (files: File[]): Promise<Array<{ url: string; alt?: string; title?: string }>> => {
  const results = await Promise.all(
    files.map(async (file) => {
      const url = await uploadOne(file);
      return url ? { url, alt: file.name, title: file.name } : undefined;
    }),
  );
  return results.filter((item): item is { url: string; alt: string; title: string } => Boolean(item));
};
