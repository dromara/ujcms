<script setup lang="ts">
import { computed, ref, type PropType } from 'vue';
import { useElementSize } from '@vueuse/core';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import type { Viewport } from './types';

/**
 * 画布舞台（visual-builder §10）：预览 iframe 的宿主。iframe 元素经 defineExpose 暴露给 BuilderPage
 * 供 usePreviewBridge 做身份核对与 postMessage。
 * 拖拽期（dragging，P2 §7.2/§7.3）：盖透明屏蔽层（iframe 内拖拽由桥指针捕获，屏蔽层防止指针滑出
 * iframe 时误触控制台 UI，亦是 P3 调色板拖拽的指针轨道）+ 幽灵卡随桥流式坐标跟随（ghost 为 iframe
 * 视口坐标；幽灵卡挂在与 iframe 同框的包装层内，视口收窄居中时坐标系仍然对齐）。
 * 视口切换（P5 §10 / canvas-chrome D31）：三种预设同套圆角边框 + 舞台内边距 + 宽度说明（切换预设边框不跳变）。
 * desktop 撑满可用宽度但不低于 DESKTOP_MIN_WIDTH（两侧栏合计约 615px，窗口不足约 1900px 时画布会跌破主题的
 * 桌面断点 xl=1280 而渲染成移动端页头——桌面视口必须是真桌面，窄窗口改为横向滚动，canvas-scroll 为滚动容器、
 * 覆盖层留在舞台上不随滚动位移；说明显示实测宽度）；tablet/mobile 定宽居中——只约束 iframe 宽度（§1 无逐断点
 * 编辑），页面自身的响应式断点自然生效。
 * 装载态（loading）：桥握手前盖半透明遮罩；桥超时（degraded）盖卡片并引导结构视图 / 重新加载。
 */
defineOptions({
  name: 'CanvasStage',
});
const props = defineProps({
  src: { type: String, default: '' },
  // 桥握手中（iframe 装载 → ready）：遮罩 + 加载指示
  loading: { type: Boolean, default: false },
  // 桥超时降级：画布不可用，结构视图兜底（§10 降级矩阵）
  degraded: { type: Boolean, default: false },
  // 画布拖拽进行中（useBuilderDrag 状态机）
  dragging: { type: Boolean, default: false },
  // { x, y, label } | null —— 幽灵卡（iframe 视口坐标）
  ghost: { type: Object as PropType<{ x: number; y: number; label: string } | null>, default: null },
  viewport: { type: String as PropType<Viewport>, default: 'desktop' },
});
const emit = defineEmits<{ structure: []; retry: [] }>();

const iframe = ref<HTMLIFrameElement>();

// 视口宽度预设：平板 768（iPad 纵向 / md 断点）、手机 390（iPhone 12–15 逻辑宽度）
const VIEWPORT_WIDTHS: Record<string, number> = { tablet: 768, mobile: 390 };
// 桌面视口下限：Tailwind xl 断点（默认主题桌面导航在此展开），低于它预览会切成移动端布局
const DESKTOP_MIN_WIDTH = 1280;
// 舞台内边距（三种预设同值）：宽度/下限按 content-box 口径加回内边距——iframe 本身仍恰为 768/390，桌面下限仍为 1280
const STAGE_PAD_X = 12;
const framed = computed<boolean>(() => props.viewport in VIEWPORT_WIDTHS);
const frameWidth = computed<string>(() => (framed.value ? `${(VIEWPORT_WIDTHS[props.viewport] ?? 0) + 2 * STAGE_PAD_X}px` : '100%'));
// 两端皆为长度值（而非 undefined），min-width 亦可过渡
const frameMinWidth = computed<string>(() => (framed.value ? '0px' : `${DESKTOP_MIN_WIDTH + 2 * STAGE_PAD_X}px`));
// 桌面说明用实测 iframe 宽（v-show 隐藏时为 0 → 只显示设备名）
const frameWrap = ref<HTMLElement>();
const { width: measuredWidth } = useElementSize(frameWrap);
const frameWidthPx = computed<number>(() => (framed.value ? (VIEWPORT_WIDTHS[props.viewport] ?? 0) : Math.round(measuredWidth.value)));

defineExpose({ iframe });
</script>

<template>
  <div class="canvas-stage">
    <div v-if="src" class="canvas-scroll">
      <div class="canvas-device" :style="{ width: frameWidth, minWidth: frameMinWidth }">
        <div ref="frameWrap" class="canvas-frame-wrap">
          <iframe ref="iframe" :src="src" :title="$t('builder.canvas.frameTitle')" class="canvas-iframe"></iframe>
          <div v-if="dragging && ghost" class="canvas-ghost" :style="{ left: `${ghost.x + 14}px`, top: `${ghost.y + 12}px` }">{{ ghost.label }}</div>
          <transition name="el-fade-in">
            <div v-if="loading" class="canvas-loading">
              <el-icon class="is-loading text-2xl text-primary"><Loading /></el-icon>
              <span class="text-xs text-gray-secondary">{{ $t('builder.canvas.loading') }}</span>
            </div>
          </transition>
        </div>
        <div class="canvas-caption">
          {{ frameWidthPx > 0 ? $t('builder.viewport.caption', { device: $t(`builder.viewport.${viewport}`), width: frameWidthPx }) : $t(`builder.viewport.${viewport}`) }}
        </div>
      </div>
    </div>
    <el-empty v-else :description="$t('builder.canvas.empty')" class="my-auto" />
    <div v-if="dragging" class="canvas-shield"></div>
    <div v-if="degraded" class="canvas-degraded">
      <div class="w-[28rem] max-w-full rounded-lg border border-gray-200 bg-white p-5 shadow-lg">
        <div class="flex items-start gap-3">
          <el-icon class="mt-0.5 shrink-0 text-2xl text-warning"><WarningFilled /></el-icon>
          <div class="min-w-0">
            <div class="text-sm font-semibold text-gray-primary">{{ $t('builder.bridge.timeoutTitle') }}</div>
            <div class="mt-1 text-xs leading-5 text-gray-regular">{{ $t('builder.bridge.timeout') }}</div>
          </div>
        </div>
        <div class="mt-4 flex justify-end gap-2">
          <el-button @click="() => emit('retry')">{{ $t('builder.bridge.retry') }}</el-button>
          <el-button type="primary" @click="() => emit('structure')">{{ $t('builder.structureView') }}</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.canvas-stage {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  overflow: hidden;
  background-color: var(--el-fill-color-light);
  background-image: radial-gradient(var(--el-border-color-lighter) 1px, transparent 1px);
  background-size: 16px 16px;
}
/* 滚动容器：桌面视口撑到 DESKTOP_MIN_WIDTH 以下时横向滚动；纵向不滚（iframe 自己滚） */
.canvas-scroll {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  overflow: auto hidden;
}
/* 自动外边距居中：内容比容器宽时外边距归零、从左边缘起排，滚动可达（align-items: center 会两侧溢出） */
.canvas-device {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  max-width: 100%;
  padding: 12px 12px 6px;
  margin: 0 auto;
  transition:
    width 0.25s ease,
    min-width 0.25s ease;
}
.canvas-frame-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
  box-shadow:
    0 0 0 1px rgb(0 0 0 / 6%),
    var(--el-box-shadow);
}
.canvas-iframe {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background: #fff;
  border: 0;
}
.canvas-caption {
  margin-top: 6px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}
.canvas-loading {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  background: rgb(255 255 255 / 70%);
  backdrop-filter: blur(1px);
}
.canvas-shield {
  position: absolute;
  inset: 0;
  z-index: 10;
  overflow: hidden;
  cursor: grabbing;
}
.canvas-ghost {
  position: absolute;
  z-index: 20;
  max-width: 240px;
  padding: 4px 10px;
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-light);
}
.canvas-degraded {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgb(243 244 246 / 85%);
}
@media (prefers-reduced-motion: reduce) {
  .canvas-device {
    transition: none;
  }
}
</style>
