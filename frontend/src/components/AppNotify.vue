<script setup lang="ts">
import { useWebSocket } from '@/composables/useWebSocket';
import { useNotification } from 'naive-ui';
import { useRoute } from 'vue-router';

const notif = useNotification();
const route = useRoute();

useWebSocket((event) => {
  if (route.meta.guest) return;
  notif.create({
    title: event.title || '新通知',
    content: event.content || '',
    duration: 5000,
  });
});
</script>

<template>
  <div />
</template>
