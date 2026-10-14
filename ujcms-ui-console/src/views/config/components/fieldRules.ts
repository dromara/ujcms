// 自定义字段的客户端规则（docs/spec/model-management/plan.md）：字段面板分组、新字段的初值、
// 字段的规范化（原先在属性面板里“选中即写入”的默认值，现在在装载与创建时一次性补齐，选中不再改动字段）、
// 逐字段校验（保存前对每个字段跑一遍，而不只是当前选中的那个）、可读的默认代码。与服务端 ModelFieldRules 同规。

export const CODE_PATTERN = /^[a-zA-Z0-9]+$/;
export const CODE_MAX_LENGTH = 50;
/** 字典类字段：选项来自字典类型，值与键（code + Key）双写 */
export const DICT_BACKED_TYPES: readonly string[] = ['radio', 'checkbox', 'select', 'multipleSelect'];
export const MULTIPLE_TYPES: readonly string[] = ['checkbox', 'multipleSelect'];
/** 列表中显示：只有文章列表与表单列表读取该开关 */
export const SHOW_IN_LIST_MODEL_TYPES: readonly string[] = ['article', 'form'];

export interface FieldGroup {
  key: string;
  types: string[];
}

/** 字段面板分组（标签键 model.fieldGroup.<key>，类型标签 model.fieldType.<type>） */
export const FIELD_GROUPS: readonly FieldGroup[] = [
  { key: 'text', types: ['text', 'textarea', 'richEditor'] },
  { key: 'number', types: ['number', 'slider', 'date', 'color'] },
  { key: 'choice', types: ['radio', 'checkbox', 'select', 'multipleSelect', 'switch'] },
  { key: 'media', types: ['imageUpload', 'videoUpload', 'audioUpload', 'fileUpload'] },
];

export const isDictBacked = (field: any): boolean => DICT_BACKED_TYPES.includes(field?.type);

/** 补齐字段类型所需的属性；就地修改并返回同一对象。幂等：已有值不覆盖 */
export function normalizeField(field: any): any {
  if (field.type === 'date' && !field.dateType) {
    field.dateType = 'date';
  }
  if (field.type === 'switch') {
    field.inactiveValue ??= '0';
    field.activeValue ??= '1';
  }
  if (['radio', 'checkbox'].includes(field.type) && !field.checkStyle) {
    field.checkStyle = 'default';
  }
  if (MULTIPLE_TYPES.includes(field.type)) {
    field.multiple = true;
    if (field.defaultValue == null) {
      field.defaultValue = [];
    }
  }
  if (!field.dataType) {
    field.dataType = ['number', 'slider'].includes(field.type) ? 'number' : 'string';
  }
  return field;
}

/** 可读的默认代码：类型名 + 序号，取同类型里最小的未占用序号（text1、image2） */
export function nextCode(type: string, fields: any[]): string {
  const base = type.replace(/Upload$|Editor$/, '');
  const codes = new Set(fields.map((item) => item.code));
  let n = 1;
  while (codes.has(`${base}${n}`)) {
    n += 1;
  }
  return `${base}${n}`;
}

/** 从面板类型创建一个新字段 */
export function createField(type: string, name: string, fields: any[]): any {
  return normalizeField({
    code: nextCode(type, fields),
    type,
    name,
    double: false,
    dataType: ['number', 'slider'].includes(type) ? 'number' : 'string',
    clob: type === 'richEditor',
  });
}

export type FieldErrors = Record<string, string>;

/**
 * 校验一个字段。返回按属性名索引的错误文案（空对象即通过）。
 * 重名、键名冲突要看全部字段：字典类字段 x 以 xKey 存选项键，另一个字段不能叫 xKey
 */
export function validateField(field: any, fields: any[], t: (key: string, values?: Record<string, unknown>) => string): FieldErrors {
  const errors: FieldErrors = {};
  const code: string = field.code ?? '';
  if (code === '') {
    errors.code = t('v.required');
  } else if (!CODE_PATTERN.test(code) || code.length > CODE_MAX_LENGTH) {
    errors.code = t('model.field.error.invalidCode');
  } else if (fields.some((item) => item !== field && item.code === code)) {
    errors.code = t('model.field.error.codeDuplicated');
  } else {
    const conflict = fields.find((item) => item !== field && ((isDictBacked(item) && `${item.code}Key` === code) || (isDictBacked(field) && item.code === `${code}Key`)));
    if (conflict != null) {
      errors.code = t('model.field.error.codeKeyConflict', { code: isDictBacked(field) ? code : conflict.code });
    }
  }
  if (!field.name || String(field.name).trim() === '') {
    errors.name = t('v.required');
  }
  if (isDictBacked(field) && field.dictTypeId == null) {
    errors.dictTypeId = t('v.required');
  }
  if (field.fileMaxSize != null && field.fileMaxSize !== '' && (Number(field.fileMaxSize) < 0 || Number(field.fileMaxSize) > 65535)) {
    errors.fileMaxSize = t('v.range', { min: 0, max: 65535 });
  }
  return errors;
}
