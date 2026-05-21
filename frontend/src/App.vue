<script setup lang="ts">
import { computed } from 'vue';
import { darkTheme, type GlobalThemeOverrides } from 'naive-ui';
import AppNotify from '@/components/AppNotify.vue';
import ThemeToggle from '@/components/ThemeToggle.vue';
import { useTheme } from '@/composables/useTheme';
import '@/assets/styles/theme.scss';

const { isDarkTheme } = useTheme();

const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#00d8bf',
    primaryColorHover: '#00bfa8',
    primaryColorPressed: '#00a692',
    primaryColorSuppl: '#00d8bf',
    infoColor: '#38bdf8',
    successColor: '#10b981',
    warningColor: '#f59e0b',
    errorColor: '#dc2626',
    bodyColor: '#ffffff',
    cardColor: '#ffffff',
    modalColor: 'rgba(255, 255, 255, 0.72)',
    popoverColor: '#ffffff',
    tableColor: '#ffffff',
    borderColor: 'rgba(15, 23, 42, 0.1)',
    dividerColor: 'rgba(15, 23, 42, 0.1)',
    textColor1: '#07111f',
    textColor2: '#334155',
    textColor3: '#64748b',
    placeholderColor: '#94a3b8',
    inputColor: '#ffffff',
    codeColor: '#f6f8fb',
    actionColor: '#f6f8fb',
  },
  Card: {
    color: '#ffffff',
    colorModal: 'rgba(255, 255, 255, 0.72)',
    borderColor: 'rgba(15, 23, 42, 0.1)',
    borderRadius: '18px',
  },
  Button: {
    borderRadiusMedium: '10px',
    borderRadiusSmall: '10px',
    borderRadiusLarge: '12px',
  },
  Input: {
    borderRadius: '10px',
    color: '#ffffff',
    colorFocus: '#ffffff',
    colorFocusError: '#ffffff',
  },
  Layout: {
    color: 'transparent',
    siderColor: '#ffffff',
    headerColor: 'rgba(255, 255, 255, 0.78)',
  },
  Tabs: {
    tabColorSegment: '#f6f8fb',
  },
};

const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#00f5d4',
    primaryColorHover: '#4ef2a3',
    primaryColorPressed: '#00c7ad',
    primaryColorSuppl: '#00f5d4',
    infoColor: '#5ac8fa',
    successColor: '#4ef2a3',
    warningColor: '#ffd93d',
    errorColor: '#ff6b6b',
    bodyColor: '#050505',
    cardColor: '#0c0c0d',
    modalColor: 'rgba(12, 12, 13, 0.72)',
    popoverColor: '#0c0c0d',
    tableColor: '#0c0c0d',
    borderColor: 'rgba(255, 255, 255, 0.08)',
    dividerColor: 'rgba(255, 255, 255, 0.08)',
    textColor1: '#f8fafc',
    textColor2: 'rgba(248, 250, 252, 0.76)',
    textColor3: 'rgba(248, 250, 252, 0.52)',
    placeholderColor: 'rgba(248, 250, 252, 0.42)',
    inputColor: 'rgba(255, 255, 255, 0.045)',
    codeColor: 'rgba(255, 255, 255, 0.06)',
    actionColor: 'rgba(255, 255, 255, 0.06)',
  },
  Card: {
    color: 'rgba(255, 255, 255, 0.045)',
    colorModal: 'rgba(12, 12, 13, 0.72)',
    borderColor: 'rgba(255, 255, 255, 0.08)',
    borderRadius: '18px',
  },
  Button: {
    borderRadiusMedium: '10px',
    borderRadiusSmall: '10px',
    borderRadiusLarge: '12px',
  },
  Input: {
    borderRadius: '10px',
    color: 'rgba(255, 255, 255, 0.045)',
    colorFocus: 'rgba(255, 255, 255, 0.06)',
    colorFocusError: 'rgba(255, 255, 255, 0.06)',
  },
  Layout: {
    color: 'transparent',
    siderColor: '#050505',
    headerColor: 'rgba(5, 5, 5, 0.74)',
  },
  Tabs: {
    tabColorSegment: 'rgba(255, 255, 255, 0.06)',
  },
};

const naiveTheme = computed(() => (isDarkTheme.value ? darkTheme : undefined));
const themeOverrides = computed(() => (isDarkTheme.value ? darkThemeOverrides : lightThemeOverrides));
</script>

<template>
  <NConfigProvider
    :theme="naiveTheme"
    :theme-overrides="themeOverrides"
  >
    <NMessageProvider>
      <NNotificationProvider>
        <AppNotify />
        <router-view />
        <ThemeToggle />
      </NNotificationProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
