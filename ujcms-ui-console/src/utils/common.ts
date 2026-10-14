import { toRaw } from 'vue';
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
  // 按当前位置排序，而非按 order 字段：移动后不重新取数，order 已是旧值；新建数据的 order 又常常相同（默认值）
  selected.sort((a, b) => list.indexOf(a) - list.indexOf(b));
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

/**
 * 移动带共享范围（scope）的列表：全局数据（scope = 2）与本站数据各自成组、组内移动，全局组在前（与服务端
 * `scope_desc,order,id` 的排序一致）。全局数据的顺序各站共用、改动需全局数据权限——整表一起提交，无该权限者
 * 连本站数据也排不了序，有该权限者则在本站页面上顺手改了各站共用的顺序。
 *
 * @returns 发生了移动的各组。调用方逐组提交其 ID 列表
 */
export const moveListByScope = (selected: any[], list: any[], type: 'top' | 'up' | 'down' | 'bottom'): any[][] => {
  const groups = [list.filter((item) => item.scope === 2), list.filter((item) => item.scope !== 2)];
  const moved: any[][] = [];
  groups.forEach((group) => {
    // 取组内自己的元素（而非 selected 的元素）：filter 出的是普通数组，其 indexOf 不再兼容“代理/原始对象”两种身份
    const chosen = group.filter((item) => selected.some((it) => toRaw(it) === toRaw(item)));
    if (chosen.length > 0) {
      moveList(chosen, group, type);
      moved.push(group);
    }
  });
  list.splice(0, list.length, ...groups[0], ...groups[1]);
  return moved;
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

/**
 * 对话框按 ID 读取它要处理的数据；读取失败（数据已被删除、不在数据权限之内等）时关闭对话框。
 * 拦截器已提示错误，而对话框里留着的还是上一次打开的那条数据：不关闭的话，接着保存就改到上一条数据上去了。
 * DialogForm 自带同一规则；不用 DialogForm、自行读取数据的对话框经此读取。
 */
export const loadOrClose = async <T>(load: () => Promise<T>, close: () => void): Promise<T> => {
  try {
    return await load();
  } catch (e) {
    close();
    throw e;
  }
};
