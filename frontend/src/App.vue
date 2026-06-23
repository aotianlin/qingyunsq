<script setup lang="ts">
import { computed } from 'vue';
import { darkTheme, type GlobalThemeOverrides } from 'naive-ui';
import AppNotify from '@/components/AppNotify.vue';
import { useTheme } from '@/composables/useTheme';
import '@/assets/styles/theme.scss';

const { isDarkTheme } = useTheme();

const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#34D0BC',
    primaryColorHover: '#22BAA8',
    primaryColorPressed: '#159E90',
    primaryColorSuppl: '#34D0BC',
    infoColor: '#78BFE7',
    successColor: '#34D0BC',
    warningColor: '#F1B94E',
    errorColor: '#D96F72',
    bodyColor: '#F6F9FC',
    cardColor: 'rgba(255, 255, 255, 0.72)',
    modalColor: 'rgba(255, 255, 255, 0.72)',
    popoverColor: 'rgba(255, 255, 255, 0.86)',
    tableColor: 'rgba(255, 255, 255, 0.52)',
    borderColor: 'rgba(0, 0, 0, 0.05)',
    dividerColor: 'rgba(0, 0, 0, 0.05)',
    textColor1: '#1A1D24',
    textColor2: '#444854',
    textColor3: '#8A91A2',
    placeholderColor: '#8A91A2',
    inputColor: 'rgba(255, 255, 255, 0.58)',
    codeColor: '#F2F7FA',
    actionColor: 'rgba(52, 208, 188, 0.12)',
  },
  Card: {
    color: 'rgba(255, 255, 255, 0.72)',
    colorModal: 'rgba(255, 255, 255, 0.72)',
    borderColor: 'transparent',
    borderRadius: '20px',
  },
  Button: {
    borderRadiusMedium: '9px',
    borderRadiusSmall: '9px',
    borderRadiusLarge: '10px',
  },
  Input: {
    borderRadius: '10px',
    color: 'rgba(255, 255, 255, 0.58)',
    colorFocus: 'rgba(255, 255, 255, 0.78)',
    colorFocusError: 'rgba(255, 255, 255, 0.78)',
  },
  Layout: {
    color: 'transparent',
    siderColor: 'rgba(255, 255, 255, 0.72)',
    headerColor: 'rgba(255, 255, 255, 0.72)',
  },
  Tabs: {
    tabColorSegment: 'rgba(52, 208, 188, 0.12)',
  },
};

const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#5DDCCD',
    primaryColorHover: '#71E8D8',
    primaryColorPressed: '#34D0BC',
    primaryColorSuppl: '#5DDCCD',
    infoColor: '#9FBFE9',
    successColor: '#5DDCCD',
    warningColor: '#E8C46B',
    errorColor: '#E18A8C',
    bodyColor: '#0D1118',
    cardColor: 'rgba(22, 28, 38, 0.72)',
    modalColor: 'rgba(22, 28, 38, 0.78)',
    popoverColor: 'rgba(22, 28, 38, 0.9)',
    tableColor: 'rgba(22, 28, 38, 0.52)',
    borderColor: 'rgba(255, 255, 255, 0.06)',
    dividerColor: 'rgba(255, 255, 255, 0.06)',
    textColor1: '#f8fafc',
    textColor2: 'rgba(248, 250, 252, 0.76)',
    textColor3: 'rgba(248, 250, 252, 0.52)',
    placeholderColor: 'rgba(248, 250, 252, 0.42)',
    inputColor: 'rgba(255, 255, 255, 0.07)',
    codeColor: 'rgba(255, 255, 255, 0.06)',
    actionColor: 'rgba(255, 255, 255, 0.06)',
  },
  Card: {
    color: 'rgba(22, 28, 38, 0.72)',
    colorModal: 'rgba(22, 28, 38, 0.78)',
    borderColor: 'transparent',
    borderRadius: '20px',
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
      </NNotificationProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
