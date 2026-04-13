/**
 * 将数组数据转换成树形数据。根据列表数据的 parentId 属性判断上下级关系。
 *
 * @param data 数组数据
 * @returns 树形数据
 */
export const toTree = (data: any[]): any[] => {
  const root: any[] = [];
  const tempMap = {};
  data.forEach((item: any) => {
    tempMap[item.id] = item;
  });
  data.forEach((item: any) => {
    const parent = tempMap[item.parentId];
    if (parent) {
      item.parent = parent;
      if (parent.children != null) {
        parent.children.push(item);
      } else {
        parent.children = [item];
      }
    } else {
      if (item.children == null) {
        item.children = [];
      }
      root.push(item);
    }
  });
  return root;
};

const doFlatTree = (data: any[], tree: any[], depth: number) => {
  tree.forEach((it) => {
    const item = { ...it, depth };
    data.push(item);
    if (item.children) {
      doFlatTree(data, item.children, depth + 1);
       
      item.children = undefined;
    }
  });
};

/**
 * 将树形数据按照树的结构转换成数组数据。使用树的 children 属性获取子节点。
 *
 * @param tree 树形对象
 * @returns  数组对象
 */
export const flatTree = (tree: any[]): any[] => {
  const data: any[] = [];
  doFlatTree(data, tree, 0);
  return data;
};

export const findTreeItem = (tree: any[], predicate: (value: any, index: number, obj: any[]) => unknown): any => {
  for (let i = 0, len = tree.length; i < len; i += 1) {
    let result;
    if (tree[i].children) {
      result = findTreeItem(tree[i].children, predicate);
      if (result) return result;
    }
    result = tree.find(predicate);
    if (result) return result;
  }
  return undefined;
};

export const sortTreeData = (data: any[]): any[] => flatTree(toTree(data));

const doDisableSubtree = (data: any[], disabledId: string | number, disabled: boolean): any[] => {
  data.forEach((item) => {
    if (disabled || item.id === disabledId) {
       
      item.disabled = true;
    }
    if (item.children) {
      doDisableSubtree(item.children, disabledId, item.disabled);
    }
  });
  return data;
};

export const disableSubtree = (data: any[], disabledId?: string | number): any[] => {
  if (!disabledId) return data;
  return doDisableSubtree(data, disabledId, false);
};

export const disableParentTree = (data: any[]): any[] => {
  data.forEach((item) => {
    if (item.children) {
      item.disabled = item.children.length > 0;
      disableParentTree(item.children);
    } else {
      item.disabled = false;
    }
  });
  return data;
};

export const disableTree = (data: any[], disabledIds?: (string | number)[]): any[] => {
  if (!disabledIds || disabledIds.length <= 0) return data;
  data.forEach((item) => {
    if (disabledIds.includes(item.id)) {
      item.disabled = true;
    }
    if (item.children) {
      disableTree(item.children, disabledIds);
    }
  });
  return data;
};

const doDisableTreeWithPermission = (data: any[], permissionIds: any[]): any[] => {
  data.forEach((item) => {
    if (!permissionIds.includes(item.id)) {
       
      item.disabled = true;
    }
    if (item.children) {
      doDisableTreeWithPermission(item.children, permissionIds);
    }
  });
  return data;
};

export const disableTreeWithPermission = (data: any[], permissionIds?: string[] | number[]): any[] => {
  if (!permissionIds) return data;
  return doDisableTreeWithPermission(data, permissionIds);
};

export const disablePermissionTree = (data: any[], permissions: readonly string[]): boolean => {
  let allDisabled = true;
  data.forEach((item) => {
    if (!(item.children && item.children.length > 0) && !permissions.includes(item.key) && !permissions.includes('*')) {
      item.disabled = true;
    } else {
      allDisabled = false;
    }
    if (item.children) {
      item.disabled = disablePermissionTree(item.children, permissions);
    }
  });
  return allDisabled;
};
