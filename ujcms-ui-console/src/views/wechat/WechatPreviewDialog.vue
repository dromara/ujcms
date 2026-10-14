<script setup lang="ts">
import { computed } from 'vue';

defineOptions({
  name: 'WechatPreviewDialog',
});
const props = defineProps({
  modelValue: { type: Boolean, required: true },
  title: { type: String, default: '' },
  author: { type: String, default: '' },
  digest: { type: String, default: '' },
  image: { type: String, default: '' },
  content: { type: String, default: '' },
  accountName: { type: String, default: '' },
});
defineEmits({ 'update:modelValue': null });

const escape = (text: string): string => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// 以微信文章页的基础样式包裹正文；正文来自后端（已按公众号排版补充内联样式），在禁用脚本的沙箱 iframe 中渲染。
// srcdoc 文档沿用父页面的基准地址，正文中的相对图片地址可正常显示。
// 对话框关闭时销毁内容（destroy-on-close），每次打开都新建 iframe：在隐藏（display:none）的 iframe 上更换 srcdoc
// 再显示，部分 Chromium 环境下画面保持空白
const srcdoc = computed(
  () => `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>
body{margin:0;padding:20px 16px 40px;font-family:-apple-system,BlinkMacSystemFont,"Helvetica Neue","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;font-size:17px;line-height:1.6;color:#333;background:#fff;word-wrap:break-word;-webkit-text-size-adjust:100%}
.rich_media_title{font-size:22px;line-height:1.4;font-weight:bold;margin:0 0 10px;color:#000}
.rich_media_meta{font-size:15px;color:#576b95;margin-bottom:22px}
.rich_media_meta .author{color:#7f7f7f;margin-right:10px}
.rich_media_content{overflow:hidden}
.rich_media_content img{max-width:100%!important;height:auto!important}
</style></head><body>
<h1 class="rich_media_title">${escape(props.title)}</h1>
<div class="rich_media_meta"><span class="author">${escape(props.author)}</span><span>${escape(props.accountName)}</span></div>
<div class="rich_media_content">${props.content}</div>
</body></html>`,
);
</script>

<template>
  <el-dialog
    :title="$t('wechatArticle.preview')"
    :model-value="modelValue"
    :width="460"
    append-to-body
    destroy-on-close
    @update:model-value="(event: boolean) => $emit('update:modelValue', event)"
  >
    <div class="mx-auto" style="width: 375px">
      <div class="mb-1 text-xs text-gray-secondary">{{ $t('wechatArticle.preview.card') }}</div>
      <div class="rounded border border-gray-200 p-3">
        <div v-if="image" class="mb-2 overflow-hidden rounded" style="aspect-ratio: 2.35 / 1"><img :src="image" class="h-full w-full object-cover" alt="" /></div>
        <div class="text-base font-bold">{{ title }}</div>
        <div v-if="digest" class="mt-1 text-sm text-gray-secondary">{{ digest }}</div>
      </div>
      <div v-if="digest" class="mt-1 text-xs text-gray-secondary">{{ $t('wechatArticle.preview.digestTip') }}</div>
      <iframe :srcdoc="srcdoc" sandbox="" class="mt-3 block w-full rounded border border-gray-200 bg-white" style="height: 560px" title="preview"></iframe>
    </div>
  </el-dialog>
</template>
