<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NIcon } from 'naive-ui';
import {
  SparklesOutline, TimeOutline, ChatbubbleEllipsesOutline, ArrowForwardOutline,
  PeopleOutline, FlameOutline, RibbonOutline, FlashOutline, CheckmarkCircleOutline, HelpCircleOutline,
} from '@vicons/ionicons5';
import type { PostAiCard } from '@/types/ai';
import type { PostVO } from '@/types/post';

const props = defineProps<{
  postId: number | string;
  card: PostAiCard | null | undefined;
  post?: PostVO | null;
}>();

const emit = defineEmits<{
  (e: 'visible', postId: number): void;
  (e: 'hidden', postId: number): void;
}>();

const router = useRouter();
const sentinel = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | null = null;

const hasCard = computed(() => !!props.card && !!props.card.tldr);
const hasHotComment = computed(() => !!props.card?.hotCommentId && !!props.card?.hotCommentExcerpt);

/**
 * 价值类型 → 配色 / 图标。AI 返回的字符串映射到固定 6 类。
 */
const valueTypeMeta = computed(() => {
  const v = props.card?.valueType || '';
  const map: Record<string, { label: string; bg: string; fg: string }> = {
    提问: { label: '提问', bg: '#fef3c7', fg: '#b45309' },
    经验: { label: '经验', bg: '#dcfce7', fg: '#15803d' },
    资源: { label: '资源', bg: '#dbeafe', fg: '#1d4ed8' },
    吐槽: { label: '吐槽', bg: '#fce7f3', fg: '#be185d' },
    招募: { label: '招募', bg: '#ede9fe', fg: '#6d28d9' },
    讨论: { label: '讨论', bg: '#e0f2fe', fg: '#0369a1' },
  };
  return map[v] || (v ? { label: v, bg: '#e5e7eb', fg: '#374151' } : null);
});

/**
 * 不依赖 LLM 的状态徽章：精华 / 新帖 / 高热。从 post prop 派生。
 */
const statusBadges = computed(() => {
  const badges: { key: string; label: string; bg: string; fg: string }[] = [];
  if (!props.post) return badges;
  if (props.post.isEssence === 1) {
    badges.push({ key: 'essence', label: '精华', bg: '#fef3c7', fg: '#92400e' });
  }
  if (props.post.type === 'QA') {
    badges.push({ key: 'qa', label: '提问', bg: '#fed7aa', fg: '#9a3412' });
  }
  // 新帖：24 小时以内
  if (props.post.createdAt) {
    const created = new Date(props.post.createdAt).getTime();
    if (Date.now() - created < 24 * 3600 * 1000) {
      badges.push({ key: 'new', label: '新帖', bg: '#dbeafe', fg: '#1d4ed8' });
    }
  }
  // 热度：likeCount + commentCount*2 + viewCount/10 >= 30
  const score = (props.post.likeCount || 0) + (props.post.commentCount || 0) * 2 + (props.post.viewCount || 0) / 10;
  if (score >= 30) {
    badges.push({ key: 'hot', label: '热帖', bg: '#fee2e2', fg: '#b91c1c' });
  }
  return badges;
});

function jumpToHotComment(e: Event) {
  e.stopPropagation();
  const id = props.card?.hotCommentId;
  if (!id) return;
  router.push({ path: `/posts/${props.postId}`, hash: `#comment-${id}` });
}

onMounted(() => {
  if (!sentinel.value || typeof IntersectionObserver === 'undefined') return;
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const idNum = Number(props.postId);
        if (!Number.isFinite(idNum)) continue;
        if (entry.isIntersecting) emit('visible', idNum);
        else emit('hidden', idNum);
      }
    },
    { rootMargin: '100px 0px', threshold: 0 },
  );
  observer.observe(sentinel.value);
});

onUnmounted(() => {
  if (observer) { observer.disconnect(); observer = null; }
});
</script>

<template>
  <div ref="sentinel" class="post-ai-sentinel">
    <!-- 即使 card 没生成，只要 statusBadges 有内容，也展示徽章行 -->
    <div v-if="hasCard || statusBadges.length > 0" class="post-ai-card-line">
      <!-- Row 1: 状态徽章（精华/QA/新帖/热帖） + AI 价值类型 -->
      <div v-if="statusBadges.length > 0 || valueTypeMeta" class="badge-row">
        <span
          v-for="b in statusBadges"
          :key="b.key"
          class="status-badge"
          :style="{ background: b.bg, color: b.fg }"
        >
          <n-icon v-if="b.key === 'essence'" size="11"><RibbonOutline /></n-icon>
          <n-icon v-else-if="b.key === 'solved'" size="11"><CheckmarkCircleOutline /></n-icon>
          <n-icon v-else-if="b.key === 'qa'" size="11"><HelpCircleOutline /></n-icon>
          <n-icon v-else-if="b.key === 'hot'" size="11"><FlameOutline /></n-icon>
          <n-icon v-else-if="b.key === 'new'" size="11"><FlashOutline /></n-icon>
          {{ b.label }}
        </span>
        <span
          v-if="valueTypeMeta"
          class="status-badge value-type"
          :style="{ background: valueTypeMeta.bg, color: valueTypeMeta.fg }"
        >
          <n-icon size="11"><SparklesOutline /></n-icon>
          {{ valueTypeMeta.label }}
        </span>
        <span v-if="card?.readMinutes" class="meta-pill">
          <n-icon size="11"><TimeOutline /></n-icon>
          {{ card.readMinutes }} 分钟
        </span>
      </div>

      <!-- Row 2: TL;DR -->
      <div v-if="hasCard" class="tldr-row">
        <n-icon size="14" class="row-icon"><SparklesOutline /></n-icon>
        <span class="tldr-text">{{ card!.tldr }}</span>
      </div>

      <!-- Row 3: 适合谁读 + AI 提取关键词 -->
      <div v-if="card?.audience || (card?.highlights && card.highlights.length > 0)" class="meta-row">
        <span v-if="card?.audience" class="audience-badge">
          <n-icon size="12"><PeopleOutline /></n-icon>
          <span class="audience-label">适合</span>
          <span class="audience-text">{{ card.audience }}</span>
        </span>
        <span
          v-for="(kw, idx) in (card?.highlights || []).slice(0, 3)"
          :key="idx"
          class="highlight-chip"
        >
          #{{ kw }}
        </span>
      </div>

      <!-- Row 4: 热门评论（可点击跳转） -->
      <button
        v-if="hasHotComment"
        class="hot-comment-row"
        type="button"
        @click="jumpToHotComment"
      >
        <n-icon size="13" class="row-icon hot"><ChatbubbleEllipsesOutline /></n-icon>
        <span class="hot-label">热门评论</span>
        <span class="hot-text">{{ card!.hotCommentExcerpt }}</span>
        <n-icon size="11" class="jump-icon"><ArrowForwardOutline /></n-icon>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.post-ai-sentinel {
  min-height: 0;
}

.post-ai-card-line {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--cf-primary) 5%, transparent);
  border-left: 2px solid color-mix(in srgb, var(--cf-primary) 60%, transparent);
}

.badge-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  letter-spacing: 0.02em;
}

.status-badge.value-type {
  margin-left: auto;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--cf-text-tertiary);
}

.tldr-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--cf-text-primary);
  font-weight: 500;
}

.row-icon {
  color: var(--cf-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.row-icon.hot {
  color: #f59e0b;
}

.tldr-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.audience-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border-radius: 14px;
  background: color-mix(in srgb, #06b6d4 12%, transparent);
  color: #0e7490;
  font-size: 12px;
  border: 1px solid color-mix(in srgb, #06b6d4 30%, transparent);
}

.audience-label {
  font-weight: 600;
  font-size: 11px;
}

.audience-text {
  font-weight: 500;
}

.highlight-chip {
  padding: 2px 8px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--cf-primary) 10%, transparent);
  color: var(--cf-primary);
  font-size: 11px;
  font-weight: 500;
}

.hot-comment-row {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  background: transparent;
  border: none;
  padding: 4px 6px;
  margin: 2px -6px -2px -6px;
  border-radius: 6px;
  text-align: left;
  transition: background 0.15s ease;

  &:hover {
    background: color-mix(in srgb, #f59e0b 10%, transparent);

    .jump-icon {
      transform: translateX(2px);
      color: #f59e0b;
    }
  }
}

.hot-label {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  color: #b45309;
  background: color-mix(in srgb, #f59e0b 18%, transparent);
}

.hot-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--cf-text-primary);
  font-size: 12.5px;
}

.jump-icon {
  flex-shrink: 0;
  color: var(--cf-text-tertiary);
  transition: transform 0.15s ease, color 0.15s ease;
}
</style>
