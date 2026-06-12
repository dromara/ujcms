import Cookies from 'js-cookie';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';

const UJCMS_LOCALE = 'ujcms-locale';
const UJCMS_SITE_ID = 'ujcms-site-id';

export const getCookieLocale = (): string | undefined => Cookies.get(UJCMS_LOCALE);
export const setCookieLocale = (local: string): void => {
  Cookies.set(UJCMS_LOCALE, local);
};

export const getSiteHeaders = (): any => {
  const siteId = useCurrentSiteStore().getCurrentSiteId();
  return siteId != null ? { [UJCMS_SITE_ID]: siteId } : {};
};

export const pageSizes = [10, 20, 50, 100, 200, 400, 800];
export const pageLayout = 'total, sizes, prev, pager, next, jumper';

export const toParams = (params: Record<string, any>): any => {
  const obj = { ...params };
  Object.keys(obj).forEach((key) => {
    if (obj[key] instanceof Array) {
      const [first] = obj[key];
      if (first instanceof Date) {
        key.split(',').forEach((item, index) => {
          obj[item] = obj[key][index];
        });
        delete obj[key];
        return;
      }
      obj[key] = obj[key].join(',');
    }
  });
  return obj;
};

export const resetParams = (params: Record<string, any>): void => {
  Object.keys(params).forEach((key) => {
    delete params[key];
  });
};

/**
 * 移动树形结构的列表，同一父节点下的子节点才能一起移动排序。
 */
export const moveTreeList = (selected: any[], data: any[], type: 'top' | 'up' | 'down' | 'bottom'): any[] => {
  const { parentId } = selected[0];
  const ids = selected
    .filter((item) => item.parentId === parentId)
    .sort((a, b) => a.order - b.order)
    .map((item) => item.id);
  const list = data.filter((item) => item.parentId === parentId).map((item) => item.id);
  const top = 0;
  const bottom = list.length;
  let up = list.indexOf(ids[0]);
  if (up <= top) {
    up = top + 1;
  }
  let down = list.indexOf(ids[ids.length - 1]);
  if (down >= bottom) {
    down = bottom - 1;
  }
  for (let i = 0, len = ids.length; i < len; i += 1) {
    const index = list.indexOf(ids[i]);
    const [deleted] = list.splice(index, 1);
    if (type === 'top') {
      list.splice(top + i, 0, deleted);
    } else if (type === 'bottom') {
      list.splice(bottom, 0, deleted);
    } else if (type === 'up') {
      list.splice(up - 1 + i, 0, deleted);
    } else if (type === 'down') {
      list.splice(down + 1, 0, deleted);
    }
  }
  return list;
};

/**
 * 移动普通列表
 */
export const moveList = (selected: any[], list: any[], type: 'top' | 'up' | 'down' | 'bottom'): any[] => {
  selected.sort((a, b) => a.order - b.order);
  const top = 0;
  const bottom = list.length;
  let up = list.indexOf(selected[0]);
  if (up <= top) up = top + 1;
  let down = list.indexOf(selected[selected.length - 1]);
  if (down >= bottom) down = bottom - 1;
  for (let i = 0, len = selected.length; i < len; i += 1) {
    const index = list.indexOf(selected[i]);
    const [deleted] = list.splice(index, 1);
    if (type === 'top') {
      list.splice(top + i, 0, deleted);
    } else if (type === 'bottom') {
      list.splice(bottom, 0, deleted);
    } else if (type === 'up') {
      list.splice(up - 1 + i, 0, deleted);
    } else if (type === 'down') {
      list.splice(down + 1, 0, deleted);
    }
  }
  return list;
};

export const isExternalPath = (path: string): boolean => {
  return /^(https?:|mailto:|tel:)/.test(path);
};

export const formatDuration = (duration?: number): string => {
  if (duration == null) {
    return '';
  }
  const hours = Math.floor(duration / 3600);
  const minutes = Math.floor((duration - hours * 3600) / 60);
  const seconds = duration - hours * 3600 - minutes * 60;
  let str = '';
  if (hours > 0) {
    str = hours + ':';
  }
  if (minutes < 10) {
    str += '0' + minutes + ':';
  } else {
    str += minutes + ':';
  }
  if (seconds < 10) {
    str += '0' + seconds;
  } else {
    str += seconds;
  }
  return str;
};
