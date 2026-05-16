<script setup lang="ts">
import { darkTheme, type GlobalThemeOverrides } from 'naive-ui';
import AppNotify from '@/components/AppNotify.vue';
import '@/assets/styles/theme.scss';

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#6366f1',
    primaryColorHover: '#4f46e5',
    primaryColorPressed: '#4338ca',
    primaryColorSuppl: '#4f46e5',
    bodyColor: '#0d1117',
    cardColor: '#161b22',
    popoverColor: '#1e2530',
    modalColor: '#161b22',
    textColor1: '#f0f6fc',
    textColor2: '#8b949e',
    textColor3: '#6e7681',
    dividerColor: 'rgba(255, 255, 255, 0.08)'
  },
  Card: {
    color: '#161b22',
    colorModal: '#161b22',
    borderColor: 'rgba(255, 255, 255, 0.08)',
    borderRadius: '12px'
  },
  Button: {
    borderRadiusMedium: '8px'
  }
};
</script>

<template>
  <NConfigProvider :theme="darkTheme" :theme-overrides="themeOverrides">
    <NMessageProvider>
      <NNotificationProvider>
        <AppNotify />
        <router-view />
      </NNotificationProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
