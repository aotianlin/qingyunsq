<script setup lang="ts">
import type { Component } from 'vue';
import { NIcon } from 'naive-ui';
import {
  ChevronBackOutline,
  ChevronForwardOutline,
  CloseOutline,
} from '@vicons/ionicons5';

type NavItem = {
  key: string;
  label: string;
  icon: Component;
};

type QuickLink = {
  label: string;
  icon: Component;
  path: string;
};

defineProps<{
  sortOptions: readonly NavItem[];
  sort: string;
  quickLinks: readonly QuickLink[];
  collapsed?: boolean;
  mobile?: boolean;
  showGuide?: boolean;
  showMobileNav?: boolean;
}>();

const emit = defineEmits<{
  (event: 'switch-sort', value: string): void;
  (event: 'quick-link', path: string): void;
  (event: 'checkin'): void;
  (event: 'toggle-collapse'): void;
  (event: 'dismiss-guide'): void;
}>();
</script>

<template>
  <aside
    class="sidebar-left flex min-w-0 flex-col gap-3 md:sticky md:top-6 md:max-h-[calc(100vh-132px)] md:overflow-y-auto min-[1440px]:gap-[18px]"
    :class="{ 'is-collapsed': collapsed, 'is-mobile': mobile }"
  >
    <template v-if="mobile">
      <details v-if="showMobileNav" class="apple-card sidebar-mobile-section" open>
        <summary>广场导航</summary>
        <div class="sidebar-mobile-grid">
          <button
            v-for="item in sortOptions"
            :key="item.key"
            class="sidebar-item sidebar-action"
            :class="{ active: sort === item.key }"
            @click="emit('switch-sort', item.key)"
          >
            <n-icon size="19">
              <component :is="item.icon" />
            </n-icon>
            <span>{{ item.label }}</span>
          </button>
        </div>
      </details>

      <details class="apple-card sidebar-mobile-section">
        <summary>个人空间</summary>
        <div class="sidebar-mobile-grid">
          <button
            v-for="item in quickLinks"
            :key="item.label"
            class="sidebar-item sidebar-action"
            @click="emit('quick-link', item.path)"
          >
            <n-icon size="18">
              <component :is="item.icon" />
            </n-icon>
            <span>{{ item.label }}</span>
          </button>
        </div>
      </details>
    </template>

    <template v-else>
      <div class="sidebar-toggle-wrap">
        <button
          class="sidebar-toggle"
          :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
          :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="emit('toggle-collapse')"
        >
          <n-icon size="18">
            <ChevronForwardOutline v-if="collapsed" />
            <ChevronBackOutline v-else />
          </n-icon>
          <span class="sidebar-label">{{ collapsed ? '展开' : '收起' }}</span>
        </button>

        <div v-if="showGuide && !collapsed" class="sidebar-guide-bubble">
          <span>点击顶部箭头可收起侧边栏，获得更简洁浏览界面</span>
          <button aria-label="关闭侧边栏引导" @click="emit('dismiss-guide')">
            <n-icon size="14">
              <CloseOutline />
            </n-icon>
          </button>
        </div>
      </div>

      <section class="apple-card sidebar-card nav-card">
        <p class="rail-caption sidebar-label">广场分类</p>
        <button
          v-for="item in sortOptions"
          :key="item.key"
          class="sidebar-item sidebar-action"
          :class="{ active: sort === item.key }"
          :title="collapsed ? item.label : undefined"
          @click="emit('switch-sort', item.key)"
        >
          <span class="icon-shell">
            <n-icon size="20">
              <component :is="item.icon" />
            </n-icon>
          </span>
          <span class="sidebar-label">{{ item.label }}</span>
          <span v-if="collapsed" class="sidebar-tooltip">{{ item.label }}</span>
        </button>

        <div class="rail-divider" />
        <p class="rail-caption sidebar-label">个人空间</p>

        <button
          v-for="item in quickLinks"
          :key="item.label"
          class="sidebar-item sidebar-action compact"
          :title="collapsed ? item.label : undefined"
          @click="emit('quick-link', item.path)"
        >
          <span class="icon-shell">
            <n-icon size="18">
              <component :is="item.icon" />
            </n-icon>
          </span>
          <span class="sidebar-label">{{ item.label }}</span>
          <span v-if="collapsed" class="sidebar-tooltip">{{ item.label }}</span>
        </button>
      </section>
    </template>

    <div class="sidebar-stat-grid">
      <section class="apple-card sidebar-stat-card check-card" data-tooltip="连续打卡">
        <div class="panel-title-row">
          <h3>
            连续打卡
          </h3>
        </div>
        <div class="streak-large">
          <strong>7</strong>
          <span>天</span>
        </div>
        <p>已连续打卡，继续加油！</p>
        <div class="calendar-badge" aria-hidden="true">
          <span class="calendar-top" />
          <span class="calendar-grid" />
          <span class="calendar-dot" />
        </div>
        <div class="check-dots" aria-hidden="true">
          <span v-for="i in 7" :key="i" class="check-dot">✓</span>
          <span class="gift-icon">
            <i class="gift-bow left" />
            <i class="gift-bow right" />
            <i class="gift-lid" />
            <i class="gift-box" />
          </span>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.sidebar-left {
  scrollbar-width: none;
}

.sidebar-left::-webkit-scrollbar {
  display: none;
}

.sidebar-toggle-wrap {
  display: block;
  position: relative;
  z-index: 5;
}

.sidebar-toggle {
  width: 100%;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 16px;
  color: var(--cf-primary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
  transition:
    background 0.2s ease-out,
    box-shadow 0.2s ease-out,
    transform 0.2s ease-out;
}

.sidebar-toggle:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.76) !important;
  box-shadow: 0 12px 36px 0 rgba(31, 38, 135, 0.06);
}

.sidebar-guide-bubble {
  position: absolute;
  top: calc(100% + 10px);
  left: 6px;
  width: min(260px, calc(100vw - 48px));
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 12px 12px 14px;
  border-radius: 16px;
  color: var(--cf-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.sidebar-guide-bubble::before {
  content: '';
  position: absolute;
  top: -6px;
  left: 22px;
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: inherit;
  transform: rotate(45deg);
}

.sidebar-guide-bubble button {
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  color: var(--cf-text-muted);
  background: rgba(255, 255, 255, 0.42);
  cursor: pointer;
}

.sidebar-card,
.sidebar-stat-card,
.sidebar-mobile-section {
  padding: 16px;
  border-radius: 16px !important;
}

.sidebar-toggle,
.sidebar-guide-bubble,
.sidebar-card,
.sidebar-stat-card,
.sidebar-mobile-section,
.sidebar-tooltip,
.sidebar-left.is-collapsed .sidebar-stat-card::after,
.sidebar-left.is-collapsed .sidebar-stat-card::before {
  background: rgba(255, 255, 255, 0.65) !important;
  border: 1px solid rgba(255, 255, 255, 0.4);
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.04) !important;
  backdrop-filter: blur(20px) saturate(160%) !important;
  -webkit-backdrop-filter: blur(20px) saturate(160%) !important;
}

.sidebar-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.sidebar-action {
  position: relative;
  width: auto;
  min-height: 40px;
  margin-inline: 0;
  padding: 0 8px;
  border: 0;
  cursor: pointer;
  overflow: visible;
}

.sidebar-action :deep(.n-icon) {
  flex: 0 0 auto;
}

.icon-shell {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: inherit;
  background: transparent;
}

.sidebar-action.active .icon-shell,
.sidebar-action:hover .icon-shell {
  background: rgba(255, 255, 255, 0.48);
}

.sidebar-tooltip {
  position: absolute;
  left: calc(100% + 12px);
  top: 50%;
  z-index: 30;
  padding: 7px 10px;
  border-radius: 10px;
  color: var(--cf-text-primary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transform: translate(4px, -50%);
  transition:
    opacity 0.2s ease-out,
    transform 0.2s ease-out;
}

.sidebar-tooltip::before {
  content: '';
  position: absolute;
  left: -5px;
  top: 50%;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: inherit;
  transform: translateY(-50%) rotate(45deg);
}

.sidebar-left.is-collapsed .sidebar-action:hover .sidebar-tooltip {
  opacity: 1;
  transform: translate(0, -50%);
}

.sidebar-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-divider {
  height: 1px;
  margin: 10px 2px 7px;
  background: var(--cf-border);
}

.rail-caption {
  margin: 0 0 4px;
  padding: 0 8px;
  color: var(--cf-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.sidebar-stat-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.sidebar-stat-card {
  position: relative;
  min-height: 126px;
  min-width: 0;
  overflow: hidden;
}

.sidebar-stat-card h3 {
  margin: 0;
  color: var(--cf-text-primary);
  font-size: 15px;
  font-weight: 800;
}

.check-card p {
  margin: 6px 0 12px;
  color: var(--cf-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-title-row h3 {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.soft-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
}

.soft-icon.mint {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.streak-large {
  display: flex;
  align-items: flex-end;
  gap: 5px;
  color: var(--cf-text-primary);
  margin-top: 10px;
}

.streak-large strong {
  color: #111827;
  font-size: 30px;
  font-weight: 800;
  line-height: 1;
}

.streak-large span {
  padding-bottom: 3px;
  color: #111827;
  font-size: 14px;
  font-weight: 500;
}

.check-dots {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding-right: 2px;
}

.check-dot {
  width: 15px;
  height: 15px;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  background: #22c7b8;
  color: white;
  font-size: 11px;
}

.gift-icon {
  position: relative;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  display: block;
  margin-left: 1px;
  font-size: 0;
  filter: drop-shadow(0 5px 8px rgba(251, 146, 60, 0.2));
}

.gift-box {
  position: absolute;
  left: 4px;
  bottom: 2px;
  width: 17px;
  height: 14px;
  border-radius: 5px 5px 4px 4px;
  display: block;
  overflow: hidden;
  background: linear-gradient(180deg, #ffd76a 0%, #ffb238 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.55),
    0 1px 0 rgba(255, 255, 255, 0.55);
}

.gift-box::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 0;
  width: 3px;
  height: 100%;
  background: #f04438;
}

.gift-box::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  box-shadow: inset 0 0 0 1px rgba(120, 53, 15, 0.07);
}

.gift-lid {
  position: absolute;
  left: 3px;
  top: 8px;
  width: 19px;
  height: 5px;
  border-radius: 999px;
  display: block;
  overflow: hidden;
  background: linear-gradient(180deg, #ffe08a 0%, #ffc044 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.gift-lid::before {
  content: "";
  position: absolute;
  left: 8px;
  top: 0;
  width: 3px;
  height: 100%;
  background: #f04438;
}

.gift-bow {
  position: absolute;
  top: 3px;
  width: 8px;
  height: 7px;
  display: block;
  border: 2px solid #f04438;
  background: rgba(255, 255, 255, 0.36);
}

.gift-bow.left {
  left: 5px;
  border-radius: 9px 9px 3px 9px;
  transform: rotate(34deg);
}

.gift-bow.right {
  right: 5px;
  border-radius: 9px 9px 9px 3px;
  transform: rotate(-34deg);
}

.calendar-badge {
  position: absolute;
  right: 16px;
  top: 48px;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #f3f8fb;
  color: #b9c9d6;
}

.calendar-badge::before {
  content: '';
  position: absolute;
  width: 21px;
  height: 20px;
  border: 2px solid currentcolor;
  border-radius: 6px;
}

.calendar-top {
  position: absolute;
  top: 13px;
  width: 21px;
  height: 2px;
  border-radius: 999px;
  background: currentcolor;
}

.calendar-top::before,
.calendar-top::after {
  content: '';
  position: absolute;
  top: -7px;
  width: 3px;
  height: 8px;
  border-radius: 999px;
  background: currentcolor;
}

.calendar-top::before {
  left: 5px;
}

.calendar-top::after {
  right: 5px;
}

.calendar-grid {
  position: absolute;
  left: 13px;
  bottom: 12px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentcolor;
  box-shadow:
    7px 0 currentcolor,
    0 7px currentcolor,
    7px 7px currentcolor;
}

.calendar-dot {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 14px;
  height: 14px;
  border: 2px solid currentcolor;
  border-radius: 50%;
  background: #f3f8fb;
}

.calendar-dot::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 2px;
  width: 2px;
  height: 6px;
  border-radius: 999px;
  background: currentcolor;
  transform-origin: bottom;
  transform: rotate(35deg);
}

.sidebar-left.is-collapsed .sidebar-toggle {
  width: 48px;
  min-height: 40px;
}

.sidebar-left.is-collapsed .sidebar-card {
  padding: 10px;
  overflow: visible;
}

.sidebar-left.is-collapsed .sidebar-stat-grid {
  grid-template-columns: 1fr;
  gap: 10px;
}

.sidebar-left.is-collapsed .sidebar-action {
  justify-content: center;
  padding: 0;
  margin-inline: 0;
}

.sidebar-left.is-collapsed .sidebar-label,
.sidebar-left.is-collapsed .rail-caption,
.sidebar-left.is-collapsed .stat-copy,
.sidebar-left.is-collapsed .check-card .streak-large,
.sidebar-left.is-collapsed .check-card p,
.sidebar-left.is-collapsed .check-card .check-dots {
  display: none;
}

.sidebar-left.is-collapsed .rail-divider {
  margin-inline: 8px;
}

.sidebar-left.is-collapsed .sidebar-stat-card {
  min-height: 52px;
  display: grid;
  place-items: center;
  padding: 6px;
  overflow: visible;
  text-align: center;
  cursor: pointer;
}

.sidebar-left.is-collapsed .sidebar-stat-card::after {
  content: attr(data-tooltip);
  position: absolute;
  left: calc(100% + 12px);
  top: 50%;
  z-index: 30;
  padding: 7px 10px;
  border-radius: 10px;
  color: var(--cf-text-primary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transform: translate(4px, -50%);
  transition:
    opacity 0.2s ease-out,
    transform 0.2s ease-out;
}

.sidebar-left.is-collapsed .sidebar-stat-card::before {
  content: '';
  position: absolute;
  left: calc(100% + 7px);
  top: 50%;
  z-index: 31;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  opacity: 0;
  pointer-events: none;
  transform: translate(4px, -50%) rotate(45deg);
  transition:
    opacity 0.2s ease-out,
    transform 0.2s ease-out;
}

.sidebar-left.is-collapsed .sidebar-stat-card:hover::after,
.sidebar-left.is-collapsed .sidebar-stat-card:hover::before {
  opacity: 1;
  transform: translate(0, -50%) rotate(0);
}

.sidebar-left.is-collapsed .sidebar-stat-card:hover::before {
  transform: translate(0, -50%) rotate(45deg);
}

.sidebar-left.is-collapsed .check-card .panel-title-row {
  display: block;
  margin: 0;
}

.sidebar-left.is-collapsed .check-card h3 {
  display: block;
}

.sidebar-left.is-collapsed .check-card h3 {
  display: none;
}

.sidebar-left.is-collapsed .check-card .calendar-badge {
  position: static;
  width: 38px;
  height: 38px;
  margin: 0;
  display: grid;
}

.sidebar-left.is-collapsed .check-card .calendar-badge::before {
  width: 22px;
  height: 21px;
}

.sidebar-left.is-collapsed .check-card .calendar-top {
  top: 12px;
}

.sidebar-left.is-collapsed .check-card .calendar-grid,
.sidebar-left.is-collapsed .check-card .calendar-dot {
  display: none;
}

.sidebar-mobile-section {
  width: 100%;
}

.sidebar-mobile-section summary {
  cursor: pointer;
  color: var(--cf-text-primary);
  font-size: 15px;
  font-weight: 600;
  list-style: none;
}

.sidebar-mobile-section summary::-webkit-details-marker {
  display: none;
}

.sidebar-mobile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(136px, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.sidebar-left.is-mobile .sidebar-stat-grid {
  grid-template-columns: 1fr;
}

@media (min-width: 1440px) {
  .sidebar-stat-grid {
    grid-template-columns: 1fr;
  }

  .sidebar-left.is-collapsed .sidebar-stat-grid {
    grid-template-columns: 1fr;
  }

  .sidebar-stat-card {
    min-height: 112px;
  }

  .sidebar-left.is-collapsed .sidebar-stat-card {
    min-height: 52px;
  }
}
</style>
