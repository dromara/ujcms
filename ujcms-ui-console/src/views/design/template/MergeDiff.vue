<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch, type PropType } from 'vue';
import { basicSetup } from 'codemirror';
import { EditorState, type Extension } from '@codemirror/state';
import { EditorView } from '@codemirror/view';
import { MergeView } from '@codemirror/merge';

// 并排差异视图（template-manager P3）：vue-codemirror 未封装 MergeView，这里手动挂载。
// 两侧均只读——a 为历史版本快照，b 为当前编辑缓冲（含未保存改动）；恢复走历史恢复端点，不做逐块回退。
defineOptions({
  name: 'MergeDiff',
});
const props = defineProps({
  a: { type: String, required: true },
  b: { type: String, required: true },
  extensions: { type: Array as PropType<Extension[]>, default: () => [] },
});

const container = ref<HTMLElement>();
let view: MergeView | undefined;

const create = () => {
  view?.destroy();
  if (container.value == null) {
    return;
  }
  const shared: Extension[] = [basicSetup, EditorView.editable.of(false), EditorState.readOnly.of(true), ...props.extensions];
  view = new MergeView({
    a: { doc: props.a, extensions: shared },
    b: { doc: props.b, extensions: shared },
    parent: container.value,
    highlightChanges: true,
    gutter: true,
    collapseUnchanged: { margin: 3, minSize: 6 },
  });
};
onMounted(create);
watch(
  () => [props.a, props.b, props.extensions] as const,
  () => create(),
);
onBeforeUnmount(() => view?.destroy());
</script>

<template>
  <div ref="container" class="merge-diff h-full min-h-0 overflow-auto border border-gray-300"></div>
</template>

<style scoped>
.merge-diff :deep(.cm-mergeView),
.merge-diff :deep(.cm-mergeViewEditors) {
  height: 100%;
}
.merge-diff :deep(.cm-editor) {
  height: 100%;
}
</style>
