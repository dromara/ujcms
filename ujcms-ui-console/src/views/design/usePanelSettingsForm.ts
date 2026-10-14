import { computed, reactive, ref, shallowRef } from 'vue';
import i18n, { localizedLabel } from '@/i18n';
import { queryShapeSilent } from '@/api/composition';
import type { PanelVM, PanelVisibility } from '@/composables/useRegionDraft';
import { collectLinkProblems } from './fields/linkUrl';

/**
 * 面板设置表单模型（component-settings §3，自 PanelForm.vue 抽取）：PanelForm 对话框与构建器检查器
 * 的内联面板设置共用同一份逻辑——按组件（Shape v2）声明的 settings 结构渲染 schema 驱动表单，
 * 编辑内存值块，经 result() 回传 {enabled, visibility, settings} 由调用方保存（save-per-op）。
 * 未存值字段按声明缺省值回退展示（与渲染期 SettingsResolver 空值→缺省语义一致）；写入时空值删键
 * （空即「缺省」，false/0 是真值保留）。组件不可解析（404）时设置不可编辑，仅保留启用开关。
 * dirty 以「当前值块 vs 面板对象快照」判定：保存后调用方就地合并面板对象即自动回落为干净。
 */

// 组件设置结构缓存（按 Shape 名；失败不缓存，下次重试）——同一会话反复点选同型面板免重复请求
const shapeDefsCache = new Map<string, Promise<Record<string, any>>>();

/** 区域重载（主题重扫/切换后）时清缓存，检查器随后按最新声明渲染。 */
export const invalidateShapeDefsCache = (): void => {
  shapeDefsCache.clear();
};

const loadShapeDefs = (shape: string): Promise<Record<string, any>> => {
  let pending = shapeDefsCache.get(shape);
  if (pending == null) {
    pending = queryShapeSilent(shape).then((detail: any) => detail?.definition?.settings ?? {});
    pending.catch(() => shapeDefsCache.delete(shape));
    shapeDefsCache.set(shape, pending);
  }
  return pending;
};

export interface PanelFormValues {
  enabled: boolean;
  visibility: PanelVisibility;
  settings: Record<string, any>;
}

export interface PanelFormSection {
  label: string;
  fields: Array<[string, any]>;
}

/** 键序无关的稳定序列化（setSetting 删键/重加会改变键序，不能按 JSON.stringify 直接比对）。 */
const stableStringify = (value: any): string =>
  JSON.stringify(value, (_key, val) =>
    val != null && typeof val === 'object' && !Array.isArray(val)
      ? Object.keys(val)
          .sort()
          .reduce((acc: Record<string, any>, k) => {
            acc[k] = val[k];
            return acc;
          }, {})
      : val,
  );

export function usePanelSettingsForm() {
  const { t } = i18n.global;

  // 面板对象本身已是草稿树里的响应式代理，shallowRef 免二次代理
  const panel = shallowRef<PanelVM | null>(null);
  const values = ref<PanelFormValues>({ enabled: true, visibility: 'all', settings: {} });
  // 组件声明的设置结构（字段名 → 定义，声明序）；不可解析时保持空并置 shapeMissing
  const settingsDefs = ref<Record<string, any>>({});
  const shapeMissing = ref<boolean>(false);
  const loading = ref<boolean>(false);
  let loadSeq = 0;

  const snapshotOf = (p: PanelVM | null): PanelFormValues => ({
    enabled: p?.enabled ?? true,
    visibility: p?.visibility ?? 'all',
    settings: JSON.parse(JSON.stringify(p?.settings ?? {})),
  });

  /** 装载面板：值块深拷贝自面板对象，设置结构按 Shape 取（缓存）；并发装载以序号守卫。 */
  const load = async (p: PanelVM): Promise<void> => {
    panel.value = p;
    values.value = snapshotOf(p);
    settingsDefs.value = {};
    shapeMissing.value = false;
    loading.value = true;
    loadSeq += 1;
    const seq = loadSeq;
    try {
      const defs = await loadShapeDefs(p.shape);
      if (seq !== loadSeq) return;
      settingsDefs.value = defs;
    } catch {
      if (seq !== loadSeq) return;
      shapeMissing.value = true;
    } finally {
      if (seq === loadSeq) loading.value = false;
    }
  };

  /** 撤回未保存的修改（回到面板对象当前值）。 */
  const reset = (): void => {
    values.value = snapshotOf(panel.value);
  };

  const clear = (): void => {
    loadSeq += 1;
    panel.value = null;
    values.value = snapshotOf(null);
    settingsDefs.value = {};
    shapeMissing.value = false;
    loading.value = false;
  };

  const dirty = computed<boolean>(() => panel.value != null && stableStringify(values.value) !== stableStringify(snapshotOf(panel.value)));

  const groupLabel = (def: any): string => localizedLabel(def.group, '');
  const fieldLabel = (fieldName: string, def: any): string => localizedLabel(def.label, fieldName);
  const fieldHelp = (def: any): string | undefined => (def.help != null ? localizedLabel(def.help, def.description) : def.description);

  // 条件显示（shape.schema.json visibleWhen，component-settings D22）：按兄弟设置的当前生效值
  // （未存值回退声明缺省，与渲染期语义一致）判定；多键为「与」，数组期望值为「其一命中」。
  // 隐藏字段的已存值保留——切回即恢复，渲染期解析不受影响。
  const effectiveValue = (name: string): any => values.value.settings[name] ?? settingsDefs.value[name]?.default ?? null;
  const isFieldVisible = (def: any): boolean => {
    const cond = def.visibleWhen;
    if (cond == null || typeof cond !== 'object') {
      return true;
    }
    return Object.entries(cond).every(([dep, expected]) => {
      const actual = effectiveValue(dep);
      const candidates = Array.isArray(expected) ? expected : [expected];
      return candidates.some((item) => String(item) === String(actual));
    });
  };

  // 相邻同组字段归节（shape.schema.json Q1：扁平设置列表 + 可选 group 标签）；无组字段成无标题节
  const sections = computed<PanelFormSection[]>(() => {
    const result: PanelFormSection[] = [];
    Object.entries(settingsDefs.value)
      .filter(([, def]) => isFieldVisible(def))
      .forEach(([name, def]) => {
        const label = groupLabel(def);
        const last = result[result.length - 1];
        if (last != null && last.label === label) {
          last.fields.push([name, def]);
        } else {
          result.push({ label, fields: [[name, def]] });
        }
      });
    return result;
  });
  const hasSettings = computed<boolean>(() => Object.keys(settingsDefs.value).length > 0);

  const setEnabled = (enabled: boolean): void => {
    values.value.enabled = enabled;
  };
  const setVisibility = (visibility: PanelVisibility): void => {
    values.value.visibility = visibility;
  };

  // 空值（null/undefined/空串）删键：与渲染期「空即缺省」（SettingsResolver.isEmpty）一致
  const setSetting = (name: string, val: any): void => {
    const next = { ...values.value.settings };
    if (val === null || val === undefined || val === '') {
      delete next[name];
    } else {
      next[name] = val;
    }
    values.value.settings = next;
  };

  // 视频上传自动抽帧封面预填（约定式，窄口径）：仅当组件声明了名为 poster 的兄弟 media 字段
  // 且其当前值为空时静默写入 {url}——封面缩略图随即出现在表单里，即是反馈。资源库选片、嵌套
  // list/object 字段不触发（上传响应才带 image）。
  const handleVideoPoster = (fieldName: string, url: string): void => {
    const posterDef = settingsDefs.value.poster;
    if (fieldName === 'poster' || posterDef?.type !== 'media' || !url) {
      return;
    }
    const current = values.value.settings.poster;
    if (current == null || !current.url) {
      setSetting('poster', { url });
    }
  };

  /** 提交前拦截链接地址问题（ShapeFieldItem 已红字提示；此处阻止把必然 400 的值发给 RegionService）：返回已本地化的错误文案。 */
  const firstProblemMessage = (): string | null => {
    const problem = collectLinkProblems(settingsDefs.value, values.value.settings)[0];
    if (problem == null) return null;
    const fieldName = problem.path.split('.')[0] ?? '';
    const label = fieldLabel(fieldName, settingsDefs.value[fieldName] ?? {});
    return `${label}${t('punct.colon')}${t(problem.kind === 'empty' ? 'compositionField.link.urlRequired' : 'compositionField.link.urlInvalid')}`;
  };

  const result = (): PanelFormValues => ({
    enabled: values.value.enabled,
    visibility: values.value.visibility,
    settings: values.value.settings,
  });

  return reactive({
    panel,
    values,
    settingsDefs,
    shapeMissing,
    loading,
    dirty,
    hasSettings,
    sections,
    load,
    reset,
    clear,
    isFieldVisible,
    setEnabled,
    setVisibility,
    setSetting,
    handleVideoPoster,
    fieldLabel,
    fieldHelp,
    firstProblemMessage,
    result,
  });
}

export type PanelSettingsForm = ReturnType<typeof usePanelSettingsForm>;
