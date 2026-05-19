<script setup lang="ts">
import { computed } from 'vue';
import { NIcon, NTooltip } from 'naive-ui';
import { MoonOutline, SunnyOutline } from '@vicons/ionicons5';
import { useTheme } from '@/composables/useTheme';

const { isDarkTheme, toggleTheme } = useTheme();

const toggleLabel = computed(() => (isDarkTheme.value ? '切换到亮色模式' : '切换到暗色模式'));
const toggleIcon = computed(() => (isDarkTheme.value ? SunnyOutline : MoonOutline));
</script>

<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <button
        class="theme-toggle"
        type="button"
        :title="toggleLabel"
        :aria-label="toggleLabel"
        @click="toggleTheme"
      >
        <n-icon
          class="theme-toggle-icon"
          size="18"
        >
          <component :is="toggleIcon" />
        </n-icon>
      </button>
    </template>
    <span>{{ toggleLabel }}</span>
  </n-tooltip>
</template>

<style scoped lang="scss">
.theme-toggle {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1000;
  width: 42px;
  height: 42px;
  border: 1px solid var(--cf-border-glass);
  border-radius: var(--cf-radius-pill);
  background: var(--cf-header-bg);
  color: var(--cf-text-primary);
  box-shadow: var(--cf-shadow-float);
  backdrop-filter: blur(18px) saturate(145%);
  -webkit-backdrop-filter: blur(18px) saturate(145%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.28s var(--cf-motion-ease), border-color 0.22s ease, background 0.22s ease, color 0.22s ease,
    box-shadow 0.28s var(--cf-motion-ease);
}

.theme-toggle::before {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: inherit;
  background: linear-gradient(180deg, var(--cf-surface-highlight), transparent);
  pointer-events: none;
}

.theme-toggle-icon {
  position: relative;
  z-index: 1;
  transition: transform 0.28s var(--cf-motion-ease);
}

.theme-toggle:hover {
  transform: translateY(-4px) scale(1.04);
  border-color: var(--cf-border-strong);
  color: var(--cf-primary);
  box-shadow: var(--cf-shadow-card-hover);
}

.theme-toggle:hover .theme-toggle-icon {
  transform: rotate(18deg) scale(1.08);
}

.theme-toggle:active {
  transform: translateY(0);
}

@media (max-width: 640px) {
  .theme-toggle {
    right: 16px;
    bottom: 16px;
  }
}
</style>
