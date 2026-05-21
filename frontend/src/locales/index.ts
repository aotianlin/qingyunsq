import { createI18n } from 'vue-i18n'
import type { Ref } from 'vue'
import zhCN from './zh-CN.json'
import enUS from './en-US.json'

const savedLocale = localStorage.getItem('locale')
const browserLocale = navigator.language.startsWith('en') ? 'en-US' : 'zh-CN'

export const i18n = createI18n({
  legacy: false,
  locale: savedLocale || browserLocale,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export function setLocale(locale: string) {
  (i18n.global.locale as unknown as Ref<string>).value = locale
  localStorage.setItem('locale', locale)
}

export const supportedLocales = [
  { code: 'zh-CN', name: '简体中文' },
  { code: 'en-US', name: 'English' },
]
