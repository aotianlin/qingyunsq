<script setup lang="ts">
import { NIcon } from 'naive-ui';
import {
  RefreshOutline,
  ChevronForwardOutline,
  FlameOutline,
  SparklesOutline,
  TrendingUpOutline,
} from '@vicons/ionicons5';
import type { AiRecommendationItem, HotArticle, HotTopicItem } from '@/types/announce';

defineProps<{
  guideItems: readonly (readonly [string, string])[];
  aiRecommendations: readonly AiRecommendationItem[];
  hotPosts: readonly HotArticle[];
  visibleHotTopics: readonly HotTopicItem[];
  topicExpanded: boolean;
  compact?: boolean;
}>();

const emit = defineEmits<{
  (event: 'open-ai'): void;
  (event: 'show-trending'): void;
  (event: 'checkin'): void;
  (event: 'toggle-topics'): void;
  (event: 'open-topic', topic: string): void;
  (event: 'open-hot-post', item: HotArticle): void;
  (event: 'open-ai-recommendation', item: AiRecommendationItem): void;
}>();

function rankClass(index: number) {
  if (index === 0) return 'rank-one';
  if (index === 1) return 'rank-two';
  if (index === 2) return 'rank-three';
  return 'rank-muted';
}

function topicHeat(item: HotTopicItem) {
  return item.heatLabel || item.heat;
}
</script>

<template>
  <aside
    class="sidebar-right flex min-w-0 flex-col gap-3 md:max-h-[calc(100vh-128px)] md:overflow-y-auto min-[1440px]:sticky min-[1440px]:top-6 min-[1440px]:gap-[16px]"
    :class="{ compact }"
  >
    <section class="apple-card ai-recommend-card">
      <div class="panel-title-row ai-title-row">
        <h3>
          <span class="soft-icon mint">
            <n-icon size="17">
              <SparklesOutline />
            </n-icon>
          </span>
          AI 今日推荐
        </h3>
        <button @click="emit('open-ai')">
          <n-icon size="13">
            <RefreshOutline />
          </n-icon>
          换一换
        </button>
      </div>
      <ul class="ai-list">
        <li v-for="item in aiRecommendations" :key="item.id">
          <button type="button" @click="emit('open-ai-recommendation', item)">
            <img v-if="item.thumbnailUrl" :src="item.thumbnailUrl" alt="" />
            <span v-else class="ai-thumb-fallback">
              <n-icon size="18">
                <SparklesOutline />
              </n-icon>
            </span>
            <span class="ai-item-copy">
              <strong>{{ item.title }}</strong>
              <em>{{ item.categoryLabel }} · {{ item.viewCount }} 人在看</em>
            </span>
          </button>
        </li>
      </ul>
      <div class="robot-orb compact-robot" aria-hidden="true">
        <div class="mini-robot">
          <span class="robot-head"><i /><i /></span>
          <span class="robot-body" />
        </div>
      </div>
    </section>

    <div class="ambient-insight-stack" aria-label="广场热点洞察">
      <div class="ambient-base-glow" aria-hidden="true" />
      <div class="ambient-mesh-beam" aria-hidden="true" />
      <div class="ambient-gap-glow" aria-hidden="true" />

      <section class="apple-card side-panel hot-posts-panel ambient-card">
        <div class="card-sheen" aria-hidden="true" />
        <div class="panel-title-row">
          <h3>
            <span class="soft-icon coral">
              <n-icon size="17">
                <TrendingUpOutline />
              </n-icon>
            </span>
            今日热门
          </h3>
          <button @click="emit('show-trending')">
            查看全部
            <n-icon size="12">
              <ChevronForwardOutline />
            </n-icon>
          </button>
        </div>
        <ol class="hot-post-list">
          <li v-for="(item, index) in hotPosts" :key="item.id">
            <strong class="rank-id" :class="rankClass(index)">{{ index + 1 }}</strong>
            <button class="hot-title" type="button" @click="emit('open-hot-post', item)">
              {{ item.title }}
            </button>
            <em class="heat-score">{{ item.heatLabel }}</em>
          </li>
        </ol>
        <div class="content-top-glow hot-glow" aria-hidden="true" />
      </section>

      <section class="apple-card side-panel topics-panel ambient-card">
        <div class="card-sheen" aria-hidden="true" />
        <div class="panel-title-row">
          <h3>
            <span class="soft-icon coral">
              <n-icon size="17">
                <FlameOutline />
              </n-icon>
            </span>
            热门话题
          </h3>
          <button @click="emit('toggle-topics')">
            {{ topicExpanded ? '收起' : '查看全部' }}
            <n-icon size="12">
              <ChevronForwardOutline />
            </n-icon>
          </button>
        </div>

        <div class="topic-flow">
          <a v-for="item in visibleHotTopics" :key="item.topic" @click="emit('open-topic', item.topic)">
            <span class="topic-label">
              <span class="topic-hash">#</span>
              <span class="topic-name">{{ item.topic }}</span>
            </span>
            <strong>{{ topicHeat(item) }}</strong>
          </a>
        </div>
        <div class="content-top-glow topic-glow" aria-hidden="true" />
      </section>
    </div>
  </aside>
</template>

<style scoped>
.sidebar-right {
  scrollbar-width: none;
}

.sidebar-right::-webkit-scrollbar {
  display: none;
}

.side-panel {
  position: relative;
  z-index: 1;
  overflow: hidden;
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, 0.4) !important;
  border-radius: 16px !important;
  background: rgba(255, 255, 255, 0.65) !important;
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.04) !important;
  backdrop-filter: blur(20px) saturate(160%) !important;
  -webkit-backdrop-filter: blur(20px) saturate(160%) !important;
  transition:
    background 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.side-panel:hover {
  background: rgba(255, 255, 255, 0.76) !important;
  box-shadow: 0 12px 36px 0 rgba(31, 38, 135, 0.06) !important;
}

.ambient-insight-stack {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: visible;
}

.ambient-base-glow,
.ambient-mesh-beam,
.ambient-gap-glow {
  position: absolute;
  pointer-events: none;
}

.ambient-base-glow {
  inset: -24px -20px -22px -18px;
  z-index: 0;
  border-radius: 34px;
  background: linear-gradient(135deg, rgba(240, 249, 255, 0.5), rgba(240, 253, 244, 0.5));
}

.ambient-mesh-beam {
  top: 8%;
  right: -18px;
  width: 180px;
  height: 450px;
  border-radius: 999px;
  background: linear-gradient(180deg, #00c4a7, #0ea5e9 52%, #3b82f6);
  opacity: 0.08;
  filter: blur(80px);
  mix-blend-mode: screen;
  z-index: 1;
}

.ambient-gap-glow {
  top: 48%;
  right: 22px;
  width: 100px;
  height: 100px;
  border-radius: 999px;
  background: #38bdf8;
  opacity: 0.08;
  filter: blur(60px);
  mix-blend-mode: screen;
  z-index: 1;
}

.ambient-card > *:not(.card-sheen):not(.content-top-glow) {
  position: relative;
  z-index: 2;
}

.card-sheen {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.4), transparent 62%);
  pointer-events: none;
}

.content-top-glow {
  position: absolute;
  right: 54px;
  width: 142px;
  height: 78%;
  border-radius: 999px;
  background: linear-gradient(
    180deg,
    rgba(0, 196, 167, 0.22),
    rgba(14, 165, 233, 0.2) 54%,
    rgba(59, 130, 246, 0.16)
  );
  filter: blur(42px);
  mix-blend-mode: screen;
  opacity: 0.8;
  pointer-events: none;
  z-index: 8;
}

.hot-glow {
  top: 18%;
}

.topic-glow {
  top: 10%;
  right: 68px;
}

.ai-recommend-card {
  position: relative;
  min-height: 238px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.4) !important;
  border-radius: 16px !important;
  background: rgba(255, 255, 255, 0.65) !important;
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.04) !important;
  backdrop-filter: blur(20px) saturate(160%) !important;
  -webkit-backdrop-filter: blur(20px) saturate(160%) !important;
  transition:
    background 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.ai-recommend-card:hover {
  background: rgba(255, 255, 255, 0.76) !important;
  box-shadow: 0 12px 36px 0 rgba(31, 38, 135, 0.06) !important;
}

.ai-recommend-card h3,
.side-panel h3 {
  margin: 0;
  color: var(--cf-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.ai-recommend-card h3,
.panel-title-row h3,
.panel-title-row button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ai-title-row {
  position: relative;
  z-index: 2;
  margin-bottom: 10px;
}

.ai-list {
  position: relative;
  z-index: 2;
  max-width: calc(100% - 38px);
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.ai-list button {
  width: 100%;
  min-width: 0;
  border: 0;
  border-radius: 12px;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  padding: 4px;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.ai-list button:hover {
  background: var(--cf-primary-soft);
  transform: translateY(-2px);
}

.ai-list img,
.ai-thumb-fallback {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  object-fit: cover;
  box-shadow: var(--cf-shadow-card);
}

.ai-thumb-fallback {
  display: grid;
  place-items: center;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.ai-item-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.ai-item-copy strong {
  color: var(--cf-text-primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  text-wrap: pretty;
  white-space: normal;
  word-break: break-word;
}

.ai-item-copy em {
  color: var(--cf-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-style: normal;
}

.robot-orb {
  position: absolute;
  right: 8px;
  bottom: 12px;
  width: 62px;
  height: 62px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(232, 251, 249, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5), var(--cf-shadow-card);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.mini-robot {
  position: relative;
  width: 72px;
  height: 88px;
  pointer-events: none;
  transform: scale(0.66);
}

.robot-head {
  position: absolute;
  inset: 0 8px auto;
  height: 44px;
  border-radius: 21px;
  background: linear-gradient(145deg, #fff, #d6f4f7);
  box-shadow: var(--cf-shadow-card);
}

.robot-head::before,
.robot-head::after {
  content: '';
  position: absolute;
  top: 17px;
  width: 8px;
  height: 16px;
  border-radius: 999px;
  background: #cfeef2;
}

.robot-head::before {
  left: -6px;
}

.robot-head::after {
  right: -6px;
}

.robot-head i:first-child {
  position: absolute;
  left: 13px;
  top: 15px;
  width: 34px;
  height: 18px;
  border-radius: 999px;
  background: #102636;
}

.robot-head i:first-child::before,
.robot-head i:first-child::after {
  content: '';
  position: absolute;
  top: 7px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--cf-primary);
}

.robot-head i:first-child::before {
  left: 9px;
}

.robot-head i:first-child::after {
  right: 9px;
}

.robot-body {
  position: absolute;
  left: 22px;
  top: 49px;
  width: 28px;
  height: 34px;
  border-radius: 14px;
  background: linear-gradient(145deg, #fff, #c9eef3);
  box-shadow: var(--cf-shadow-card);
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.panel-title-row button {
  border: 0;
  color: #86868b;
  background: transparent;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
}

.panel-title-row button:hover {
  color: var(--cf-primary);
}

.soft-icon {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
}

.soft-icon.mint {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.soft-icon.coral {
  color: #ff5a52;
  background: rgba(255, 90, 82, 0.1);
}

.hot-post-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hot-posts-panel li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.rank-id {
  flex: 0 0 18px;
  color: #8a8f98;
  font-size: 13px;
  font-weight: 800;
  line-height: 1;
  text-align: left;
}

.rank-one {
  color: #ff5a52;
}

.rank-two {
  color: #ff8a00;
}

.rank-three {
  color: #f6b51e;
}

.rank-muted {
  color: #667085;
}

.hot-title {
  flex: 1 1 auto;
  min-width: 0;
  border: 0;
  padding: 0;
  color: #1d1d1f;
  background: transparent;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: color 160ms ease, text-decoration-color 160ms ease;
}

.hot-title:hover {
  color: #007d80;
  text-decoration: underline;
  text-decoration-color: rgba(0, 125, 128, 0.28);
  text-underline-offset: 3px;
}

.heat-score {
  flex: 0 0 auto;
  color: #86868b;
  font-size: 12px;
  font-weight: 500;
  font-style: normal;
  white-space: nowrap;
}

.topic-flow {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.topics-panel a {
  width: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-radius: 10px;
  padding: 9px 12px;
  background: rgba(248, 250, 252, 0.8);
  cursor: pointer;
  transition:
    background 160ms ease,
    transform 160ms ease,
    color 160ms ease;
}

.topics-panel a:hover {
  background: #f1f5f9;
  transform: translateY(-1px);
}

.topic-label {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.topic-hash {
  flex: 0 0 auto;
  color: #00c4a7;
  font-size: 13px;
  font-weight: 700;
}

.topic-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1d1d1f;
  font-size: 13px;
  font-weight: 500;
  transition: color 160ms ease;
}

.topics-panel a:hover .topic-name {
  color: #007d80;
}

.topics-panel a strong {
  color: #86868b;
  font-size: 12px;
  font-weight: 500;
}

.sidebar-right.compact {
  max-height: min(76vh, 720px);
  overflow-y: auto;
}

.sidebar-right.compact .ai-recommend-card {
  min-height: 238px;
}

.sidebar-right.compact .ai-list {
  max-width: 100%;
}

.sidebar-right.compact .robot-orb {
  width: 62px;
  height: 62px;
}

.sidebar-right.compact .mini-robot {
  transform: scale(0.68);
}
</style>
