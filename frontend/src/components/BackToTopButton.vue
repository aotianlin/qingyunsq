<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch, type PropType } from 'vue';
import { NIcon } from 'naive-ui';
import { ArrowUpOutline } from '@vicons/ionicons5';

const props = defineProps({
  target: {
    type: Object as PropType<HTMLElement | null>,
    default: null,
  },
});

const visible = ref(false);
let activeTarget: HTMLElement | Window | null = null;

function currentScrollTop(target: HTMLElement | Window | null) {
  if (target instanceof HTMLElement) {
    return target.scrollTop;
  }
  if (!target || target === window) {
    return window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
  }
  return 0;
}

function updateVisible() {
  // 用户只要离开顶部就展示按钮，避免长列表里还要继续下滑到阈值后才出现。
  visible.value = currentScrollTop(activeTarget) > 0;
}

function bindScrollTarget(target: HTMLElement | null) {
  unbindScrollTarget();
  activeTarget = target || window;
  activeTarget.addEventListener('scroll', updateVisible, { passive: true });
  updateVisible();
}

function unbindScrollTarget() {
  if (!activeTarget) return;
  activeTarget.removeEventListener('scroll', updateVisible);
  activeTarget = null;
}

function scrollToTop() {
  if (activeTarget instanceof HTMLElement) {
    activeTarget.scrollTo({ top: 0, behavior: 'smooth' });
    return;
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

onMounted(() => {
  bindScrollTarget(props.target);
});

onUnmounted(() => {
  unbindScrollTarget();
});

watch(
  () => props.target,
  (target) => {
    bindScrollTarget(target);
  },
);
</script>

<template>
  <Teleport to="body">
    <Transition name="back-to-top">
      <button
        v-if="visible"
        class="back-to-top-btn"
        type="button"
        title="回到顶部"
        aria-label="回到顶部"
        @click="scrollToTop"
      >
        <n-icon size="22">
          <ArrowUpOutline />
        </n-icon>
      </button>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.back-to-top-btn {
  position: fixed;
  left: 50%;
  bottom: calc(28px + env(safe-area-inset-bottom));
  z-index: 80;
  width: 46px;
  height: 46px;
  border: 1px solid color-mix(in srgb, var(--cf-primary) 32%, var(--cf-border-glass));
  border-radius: 14px;
  background:
    linear-gradient(180deg, var(--cf-surface-highlight), transparent 58%),
    color-mix(in srgb, var(--cf-bg-readable) 92%, transparent);
  color: var(--cf-primary);
  box-shadow: var(--cf-shadow-soft);
  backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(138%);
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur)) saturate(138%);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transform: translateX(-50%);
  transition: transform 0.22s var(--cf-motion-ease), border-color 0.22s ease, background 0.22s ease, box-shadow 0.22s ease;
}

.back-to-top-btn:hover {
  transform: translate(-50%, -2px);
  border-color: var(--cf-border-strong);
  background: var(--cf-bg-readable);
  box-shadow: var(--cf-shadow-card);
}

.back-to-top-enter-active,
.back-to-top-leave-active {
  transition: opacity 0.18s ease, transform 0.18s var(--cf-motion-ease);
}

.back-to-top-enter-from,
.back-to-top-leave-to {
  opacity: 0;
  transform: translate(-50%, 10px) scale(0.96);
}

@media (max-width: 720px) {
  .back-to-top-btn {
    bottom: calc(20px + env(safe-area-inset-bottom));
    width: 44px;
    height: 44px;
  }
}
</style>
