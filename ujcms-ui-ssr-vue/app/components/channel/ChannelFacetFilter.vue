<script setup lang="ts">
import type { Channel, DictEntry, ModelField } from '#shared/types/api';

import { fetchDictList } from '~/api/dict';

const FACET_TYPES = new Set(['radio', 'checkbox', 'select', 'multipleSelect']);

/**
 * Channel facet-filter strip. Mirrors the template-theme block driven by
 * `{% for field in channel.articleModel.fieldList %}` — only renders chips
 * for `radio | checkbox | select | multipleSelect` fields with a backing
 * `dictTypeId`.
 *
 * URL contract: `?<fieldCode>Key=<dictValue>` (e.g. `?typeKey=1&languageKey=2`).
 * Selecting "全部<fieldName>" drops the param. Other already-set facet params
 * are preserved across chip clicks. The channel page translates these query
 * params into Spring's `Q_ArrayEQ_@articleExt-mainsJson$<code>Key` shape.
 */
const props = defineProps<{
  channel: Channel;
  /** Current request search params (already-flattened to first-string). */
  searchParams: Record<string, string>;
  /** Path the facet links resolve against (typically the channel page itself). */
  basePath: string;
}>();

const fields = computed(() => (props.channel.articleModel?.fieldList ?? []).filter((f) => f.type && FACET_TYPES.has(f.type) && f.dictTypeId));

const { data: dictGroups } = await useAsyncData(
  () => `channel-facets-${props.channel.id}`,
  () =>
    Promise.all(
      fields.value.map(async (field) => ({
        field: field as ModelField,
        dicts: field.dictTypeId ? await fetchDictList(field.dictTypeId) : ([] as DictEntry[]),
      })),
    ),
  { default: () => [] },
);

const buildHref = (paramName: string, value?: string): string => {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(props.searchParams)) {
    if (k === paramName || k === 'page') continue;
    if (v) sp.set(k, v);
  }
  if (value !== undefined) sp.set(paramName, value);
  const qs = sp.toString();
  return qs ? `${props.basePath}?${qs}` : props.basePath;
};

const chip = (active: boolean): string => (active ? 'rounded bg-slate-200 px-2 py-1 text-slate-700' : 'rounded px-2 py-1 text-cms-primary hover:bg-slate-100');
</script>

<template>
  <div v-if="dictGroups.length > 0">
    <template v-for="group in dictGroups" :key="group.field.code">
      <div v-if="group.dicts.length > 0" class="flex flex-wrap items-center gap-2 border-b border-slate-200 py-1 text-sm">
        <!-- "All" pill — active when no value selected. -->
        <NuxtLink :to="buildHref(`${group.field.code}Key`)" :class="chip(!searchParams[`${group.field.code}Key`])">全部{{ group.field.name ?? group.field.code }}</NuxtLink>
        <NuxtLink
          v-for="dict in group.dicts"
          :key="dict.id"
          :to="buildHref(`${group.field.code}Key`, dict.value)"
          :class="chip(searchParams[`${group.field.code}Key`] === dict.value)"
        >
          {{ dict.name ?? dict.value ?? '未命名' }}
        </NuxtLink>
      </div>
    </template>
  </div>
</template>
