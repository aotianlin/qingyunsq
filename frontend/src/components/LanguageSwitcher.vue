<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale, supportedLocales } from '@/locales'

const { locale } = useI18n()

function switchLanguage(code: string) {
  setLocale(code)
}
</script>

<template>
  <n-dropdown
    :options="supportedLocales.map(l => ({ label: l.name, key: l.code }))"
    @select="switchLanguage"
  >
    <n-button quaternary size="small">
      {{ supportedLocales.find(l => l.code === locale)?.name || '语言' }}
    </n-button>
  </n-dropdown>
</template>
