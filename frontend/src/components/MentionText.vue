<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { parseMentions } from '@/utils/mention';

const props = defineProps<{
  text?: string;
}>();

const segments = computed(() => parseMentions(props.text ?? ''));
</script>

<template>
  <span class="mention-text-wrap">
    <template
      v-for="(segment, index) in segments"
      :key="`${index}-${segment.text}`"
    >
      <RouterLink
        v-if="segment.mention"
        class="mention-link"
        :to="{ path: '/search', query: { q: `@${segment.mention}` } }"
      >
        {{ segment.text }}
      </RouterLink>
      <template v-else>{{ segment.text }}</template>
    </template>
  </span>
</template>

<style scoped>
/* 保留内容中的换行符 */
.mention-text-wrap {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
