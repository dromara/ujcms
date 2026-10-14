<script setup lang="ts">
export interface BreadcrumbItem {
  label: string;
  href?: string;
}

/**
 * Breadcrumb chain — pages compute their item list and pass it in. Mirrors
 * the template-theme baseline's `首页 / {channel.name} / ...` shape.
 *
 * The last item is rendered as plain text (current page); intermediate items
 * are links. Renders nothing when items is empty.
 */
defineProps<{ items: BreadcrumbItem[] }>();
</script>

<template>
  <nav v-if="items.length > 0" aria-label="面包屑" class="mx-auto max-w-6xl px-4 py-3 text-sm text-cms-content-muted">
    <ol class="flex flex-wrap items-center gap-1">
      <li v-for="(item, index) in items" :key="`${item.label}-${index}`" class="flex items-center gap-1">
        <NuxtLink v-if="item.href && index < items.length - 1" :to="item.href" class="hover:text-cms-content">
          {{ item.label }}
        </NuxtLink>
        <span v-else :aria-current="index === items.length - 1 ? 'page' : undefined" :class="index === items.length - 1 ? 'text-cms-content' : ''">
          {{ item.label }}
        </span>
        <span v-if="index < items.length - 1" aria-hidden="true">/</span>
      </li>
    </ol>
  </nav>
</template>
