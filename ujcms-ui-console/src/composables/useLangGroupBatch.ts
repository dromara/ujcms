import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useCurrentSiteStore } from '@/stores/currentSiteStore';

/**
 * 批量新增译文弹窗（文章 / 栏目）共用的多目标站点状态（docs/spec/ai/article-translate.md §7 / §7.1）：
 * 所选目标站点按语言组顺序排列；逐站点并行请求预览，选择变化期间返回的过期结果丢弃；
 * 服务商选项取各所选站点的交集（大模型不分语言对、总在列，机器翻译引擎按语言对增减），
 * 已选服务商仍在交集内则保留，否则回落到全局“翻译服务商”（须在交集内），再否则不翻译。
 * 预览请求由调用方提供，返回体须含 providers（{code,name,mt}）与 defaultProvider
 */
export const useLangGroupBatch = (fetchPreview: (targetSiteId: string, channelId?: string) => Promise<any>) => {
  const { t } = useI18n();
  const currentSiteStore = useCurrentSiteStore();

  const groupSites = ref<any[]>([]);
  const targetSiteIds = ref<string[]>([]);
  const channelId = ref<string>();
  const previewing = ref(false);
  // 站点ID → 预览
  const previews = ref<Record<string, any>>({});
  // 空串为“不翻译”（仅新建栏目 / 仅创建草稿）
  const provider = ref<string>('');
  let sequence = 0;

  const targetSites = computed(() => groupSites.value.filter((site: any) => targetSiteIds.value.includes(site.id)));
  const previewRows = computed(() => targetSites.value.map((site: any) => ({ site, preview: previews.value[site.id] })).filter((row) => row.preview != null));
  const providerOptions = computed(() => {
    const rows = previewRows.value;
    const first = rows[0];
    if (first == null) {
      return [];
    }
    const rest = rows.slice(1);
    return (first.preview.providers ?? []).filter((item: any) => rest.every((row) => (row.preview.providers ?? []).some((other: any) => other.code === item.code)));
  });
  const llmOptions = computed(() => providerOptions.value.filter((item: any) => !item.mt));
  const mtOptions = computed(() => providerOptions.value.filter((item: any) => item.mt));
  const providerName = computed(() => providerOptions.value.find((item: any) => item.code === provider.value)?.name ?? provider.value);
  const translating = computed(() => provider.value !== '');
  // 翻译方向明示：当前站点（源）语言 → 各目标站点语言。结果都落在目标站点，本站不变
  const currentLang = computed(() => currentSiteStore.currentSite?.lang);
  const join = () => t('article.i18n.scope.join');
  const targetLangs = computed(() => targetSites.value.map((site: any) => site.lang).join(join()));
  const siteNames = (sites: any[]) => sites.map((site) => site.name).join(join());
  // 逐站点行：站点名（语言）：正文
  const siteLine = (site: any, text: string) => t('article.i18n.scope.siteLine', [site.name, site.lang, text]);

  const reset = () => {
    targetSiteIds.value = [];
    channelId.value = undefined;
    previews.value = {};
    provider.value = '';
    sequence++;
  };

  const fetchPreviews = async () => {
    const seq = ++sequence;
    const sites = targetSites.value;
    if (sites.length === 0) {
      previews.value = {};
      return;
    }
    previewing.value = true;
    try {
      const results = await Promise.all(sites.map((site: any) => fetchPreview(site.id, channelId.value)));
      if (seq !== sequence) {
        return;
      }
      const next: Record<string, any> = {};
      sites.forEach((site: any, index: number) => {
        next[site.id] = results[index];
      });
      previews.value = next;
      const options = providerOptions.value;
      const preferred = results.find((item) => item.defaultProvider != null)?.defaultProvider;
      if (provider.value === '' || !options.some((item: any) => item.code === provider.value)) {
        provider.value = options.some((item: any) => item.code === preferred) ? preferred : '';
      }
    } finally {
      if (seq === sequence) {
        previewing.value = false;
      }
    }
  };
  watch([targetSiteIds, channelId], () => {
    fetchPreviews();
  });

  return {
    groupSites,
    targetSiteIds,
    channelId,
    previewing,
    provider,
    targetSites,
    previewRows,
    providerOptions,
    llmOptions,
    mtOptions,
    providerName,
    translating,
    currentLang,
    targetLangs,
    siteNames,
    siteLine,
    reset,
  };
};
