<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NCard, NButton, NTag, NSpace, NSpin, NEmpty, useMessage } from 'naive-ui';
import { getResourceById, getDownloadUrl, deleteResource } from '@/api/resources';
import { useAuthStore } from '@/stores/auth';
import type { ResourceVO } from '@/types/resource';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const resource = ref<ResourceVO | null>(null);
const loading = ref(true);
const currentUserId = authStore.user?.id;
const isUploader = () => resource.value?.uploaderId === currentUserId;

async function load() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    resource.value = await getResourceById(id);
  } catch {
    resource.value = null;
  }
  loading.value = false;
}

async function handleDownload() {
  if (!resource.value) return;
  try {
    const url = await getDownloadUrl(resource.value.id);
    window.open(url, '_blank');
    // 刷新以更新下载计数
    setTimeout(load, 1000);
  } catch (err) {
    message.error(err instanceof Error ? err.message : '下载链接获取失败');
  }
}

async function handleDelete() {
  if (!resource.value) return;
  try {
    await deleteResource(resource.value.id);
    message.success('资源已删除');
    router.replace('/resources');
  } catch {
    message.error('删除失败');
  }
}

function goBack() {
  router.push('/resources');
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

onMounted(load);
</script>

<template>
  <div class="detail-page">
    <template v-if="loading">
      <div class="loading">
        <NSpin />
      </div>
    </template>

    <template v-else-if="resource">
      <NCard class="resource-info">
        <div class="info-header">
          <div>
            <h2>{{ resource.fileName }}</h2>
            <NSpace>
              <NTag size="small">
                {{ resource.fileType.toUpperCase() }}
              </NTag>
              <NTag
                v-if="resource.visibility === 'PUBLIC'"
                type="success"
                size="small"
              >
                公开
              </NTag>
              <NTag
                v-else-if="resource.visibility === 'SPACE'"
                type="warning"
                size="small"
              >
                空间
              </NTag>
              <NTag
                v-else
                type="default"
                size="small"
              >
                私有
              </NTag>
            </NSpace>
          </div>
          <div class="header-actions">
            <NButton
              size="small"
              @click="goBack"
            >
              返回列表
            </NButton>
            <NButton
              v-if="isUploader()"
              type="error"
              size="small"
              @click="handleDelete"
            >
              删除
            </NButton>
          </div>
        </div>

        <p
          v-if="resource.description"
          class="resource-desc"
        >
          {{ resource.description }}
        </p>

        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">大小</span>
            <span>{{ formatSize(resource.fileSize) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">下载</span>
            <span>{{ resource.downloadCount }} 次</span>
          </div>
          <div class="info-item">
            <span class="info-label">上传者</span>
            <span>{{ resource.uploader?.nickname || '未知' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">时间</span>
            <span>{{ resource.createdAt?.split('T')[0] }}</span>
          </div>
        </div>

        <div
          v-if="resource.college || resource.major || resource.course || resource.tags?.length"
          class="info-tags"
        >
          <NTag
            v-if="resource.college"
            type="info"
            size="small"
          >
            {{ resource.college }}
          </NTag>
          <NTag
            v-if="resource.major"
            type="info"
            size="small"
          >
            {{ resource.major }}
          </NTag>
          <NTag
            v-if="resource.course"
            type="info"
            size="small"
          >
            {{ resource.course }}
          </NTag>
          <NTag
            v-for="t in resource.tags"
            :key="t"
            size="small"
          >
            {{ t }}
          </NTag>
        </div>

        <NButton
          type="primary"
          block
          class="download-btn"
          @click="handleDownload"
        >
          下载文件
        </NButton>
      </NCard>
    </template>

    <template v-else>
      <div class="empty">
        <NEmpty description="资源不存在" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 640px;
  margin: 40px auto;
  padding: 0 16px;
}
.loading { text-align: center; padding: 80px; }
.empty { margin: 80px 0; }
.info-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.info-header h2 { margin: 0 0 6px; font-size: 18px; }
.header-actions { display: flex; gap: 8px; }
.resource-desc { color: #666; margin-bottom: 16px; font-size: 14px; }
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
.info-item {
  display: flex;
  flex-direction: column;
  font-size: 14px;
}
.info-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 2px;
}
.info-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.download-btn { margin-top: 8px; }
</style>
