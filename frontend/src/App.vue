<script setup lang="ts">
import type { GlobalThemeOverrides } from 'naive-ui';
import AppNotify from '@/components/AppNotify.vue';
import '@/assets/styles/theme.scss';

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#0058be',
    primaryColorHover: '#004aa0',
    primaryColorPressed: '#003f87',
    primaryColorSuppl: '#0058be',
    infoColor: '#0058be',
    successColor: '#10b981',
    warningColor: '#f59e0b',
    errorColor: '#dc2626',
    bodyColor: '#faf9f6',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    tableColor: '#ffffff',
    borderColor: '#d9e2f2',
    dividerColor: '#ebe8e0',
    textColor1: '#0b1c30',
    textColor2: '#425466',
    textColor3: '#6b7a90',
    placeholderColor: '#8ca0bd',
    inputColor: '#ffffff',
    codeColor: '#f4f2ee',
    actionColor: '#f4f2ee',
  },
  Card: {
    color: '#ffffff',
    colorModal: '#ffffff',
    borderColor: '#d9e2f2',
    borderRadius: '20px',
  },
  Button: {
    borderRadiusMedium: '12px',
    borderRadiusSmall: '10px',
    borderRadiusLarge: '14px',
  },
  Input: {
    borderRadius: '12px',
    color: '#ffffff',
    colorFocus: '#ffffff',
    colorFocusError: '#ffffff',
  },
  Layout: {
    color: 'transparent',
    siderColor: '#ffffff',
    headerColor: 'rgba(250, 249, 246, 0.92)',
  },
  Tabs: {
    tabColorSegment: '#f4f2ee',
  },
};
</script>

<template>
  <NConfigProvider :theme-overrides="themeOverrides">
    <NMessageProvider>
      <NNotificationProvider>
        <AppNotify />
        <router-view />
      </NNotificationProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
