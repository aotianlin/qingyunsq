<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  NAlert,
  NButton,
  NDynamicTags,
  NEmpty,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NUpload,
  useMessage,
  type UploadFileInfo,
} from 'naive-ui';
import {
  CloudUploadOutline,
  DownloadOutline,
  EyeOutline,
  TrashOutline,
} from '@vicons/ionicons5';
import {
  deleteResource,
  getDownloadUrl,
  getPreviewUrl,
  getResourceById,
  getResourcePreviewText,
  getResources,
  resourceAccept,
  uploadResource,
} from '@/api/resources';
import { useAuthStore } from '@/stores/auth';
import type { ResourcePreviewVO, ResourceVO } from '@/types/resource';
import {
  ALL_RESOURCE_TOPIC,
  buildResourceTopicGroups,
  buildResourceTopicOptions,
  getResourceTopics,
} from '@/utils/resource-topic';

const message = useMessage();
const authStore = useAuthStore();

const resources = ref<ResourceVO[]>([]);
const loading = ref(false);
const collegeFilter = ref<string | undefined>(undefined);
const activeTopic = ref(ALL_RESOURCE_TOPIC);

const detailVisible = ref(false);
const detailLoading = ref(false);
const selectedResource = ref<ResourceVO | null>(null);
const previewLoading = ref(false);
const previewText = ref<ResourcePreviewVO | null>(null);
const previewError = ref('');

const uploadVisible = ref(false);
const uploadFileList = ref<UploadFileInfo[]>([]);
const uploadFile = ref<File | null>(null);
const uploadDescription = ref('');
const uploadVisibility = ref('PUBLIC');
const uploadTags = ref<string[]>([]);
const uploadLoading = ref(false);

const visibilityOptions = [
  { label: '公开（所有人可见）', value: 'PUBLIC' },
  { label: '空间内可见', value: 'SPACE' },
  { label: '仅自己可见', value: 'PRIVATE' },
];

const typeIcons: Record<string, string> = {
  pdf: '📄',
  doc: '📝',
  docx: '📝',
  md: '▣',
  markdown: '▣',
  ppt: '📊',
  pptx: '📊',
  xls: '📈',
  xlsx: '📈',
  zip: '📦',
  rar: '📦',
  '7z': '📦',
  jpg: '🖼',
  jpeg: '🖼',
  png: '🖼',
  gif: '🖼',
  webp: '🖼',
};

const previewUrl = computed(() => {
  return selectedResource.value ? getPreviewUrl(selectedResource.value.id) : '';
});

const markdownSrcdoc = computed(() => {
  if (!previewText.value) return '';
  const body = renderMarkdown(previewText.value.content);
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <style>
    body {
      margin: 0;
      padding: 20px;
      font-family: Inter, "Segoe UI", sans-serif;
      color: #07111f;
      background: #ffffff;
    }
    h1, h2, h3, h4, h5, h6 { margin: 0.8em 0 0.45em; }
    p { margin: 0 0 0.85em; line-height: 1.75; }
    pre, code { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
    pre {
      margin: 0 0 0.85em;
      padding: 14px;
      overflow: auto;
      white-space: pre-wrap;
      background: #f6f8fb;
      border-radius: 8px;
    }
    code {
      padding: 2px 5px;
      background: #f6f8fb;
      border-radius: 5px;
    }
    blockquote {
      margin: 0 0 0.85em;
      padding-left: 12px;
      color: #64748b;
      border-left: 3px solid #00d8bf;
    }
    ul { padding-left: 22px; }
  </style>
</head>
<body>${body}</body>
</html>`;
});

const currentUserId = computed(() => authStore.user?.id);
const isUploader = computed(() => selectedResource.value?.uploaderId === currentUserId.value);
const topicOptions = computed(() => buildResourceTopicOptions(resources.value));
const topicGroups = computed(() => buildResourceTopicGroups(resources.value, activeTopic.value));
const visibleResourceCount = computed(() => topicGroups.value.reduce((sum, group) => sum + group.count, 0));

async function load() {
  loading.value = true;
  try {
    resources.value = await getResources({
      college: collegeFilter.value || undefined,
      limit: 30,
    });
  } catch {
    resources.value = [];
  } finally {
    loading.value = false;
  }
}

async function openDetail(resource: ResourceVO) {
  selectedResource.value = resource;
  detailVisible.value = true;
  detailLoading.value = true;
  previewText.value = null;
  previewError.value = '';

  try {
    selectedResource.value = await getResourceById(resource.id);
    await loadPreview();
  } catch {
    message.error('资源详情加载失败');
  } finally {
    detailLoading.value = false;
  }
}

async function loadPreview() {
  if (!selectedResource.value) return;
  previewText.value = null;
  previewError.value = '';
  const kind = getPreviewKind(selectedResource.value);

  if (kind !== 'text') {
    return;
  }

  previewLoading.value = true;
  try {
    previewText.value = await getResourcePreviewText(selectedResource.value.id);
  } catch {
    previewError.value = '预览内容加载失败';
  } finally {
    previewLoading.value = false;
  }
}

function openUpload() {
  uploadFileList.value = [];
  uploadFile.value = null;
  uploadDescription.value = '';
  uploadVisibility.value = 'PUBLIC';
  uploadTags.value = [];
  uploadVisible.value = true;
}

function handleFileChange(fileList: UploadFileInfo[]) {
  uploadFileList.value = fileList;
  uploadFile.value = fileList[0]?.file || null;
}

async function submitUpload() {
  if (!uploadFile.value) {
    message.warning('请选择文件');
    return;
  }

  const tags = normalizeTags(uploadTags.value);
  if (tags.length === 0) {
    message.warning('请至少填写一个资源标签');
    return;
  }

  uploadLoading.value = true;
  try {
    const resource = await uploadResource(uploadFile.value, {
      visibility: uploadVisibility.value,
      tags,
      description: uploadDescription.value.trim() || undefined,
    });
    resources.value = [resource, ...resources.value.filter((item) => item.id !== resource.id)];
    uploadVisible.value = false;
    message.success('上传成功');
    await openDetail(resource);
  } catch {
    message.error('上传失败');
  } finally {
    uploadLoading.value = false;
  }
}

function normalizeTags(values: string[]) {
  return [...new Set(
    values
      .flatMap((value) => value.split(/[\s,，、;；#]+/))
      .map((tag) => tag.trim())
      .filter(Boolean),
  )].slice(0, 8);
}

function handleUploadTagsUpdate(value: string[]) {
  uploadTags.value = normalizeTags(value);
}

function selectTopic(topic: string) {
  activeTopic.value = topic;
}

function handleDownload(resource = selectedResource.value) {
  if (!resource) return;
  window.open(getDownloadUrl(resource.id), '_blank');
  setTimeout(() => refreshResource(resource.id), 1000);
}

async function refreshResource(id: number) {
  try {
    const latest = await getResourceById(id);
    resources.value = resources.value.map((item) => (item.id === id ? latest : item));
    if (selectedResource.value?.id === id) {
      selectedResource.value = latest;
    }
  } catch {
    // 下载后刷新失败不影响主流程，保持当前列表数据即可。
  }
}

async function handleDelete() {
  if (!selectedResource.value) return;
  try {
    await deleteResource(selectedResource.value.id);
    resources.value = resources.value.filter((item) => item.id !== selectedResource.value?.id);
    detailVisible.value = false;
    message.success('资源已删除');
  } catch {
    message.error('删除失败');
  }
}

function getPreviewKind(resource: ResourceVO): 'pdf' | 'image' | 'text' | 'unsupported' {
  const fileType = normalizeType(resource.fileType);
  if (fileType === 'pdf') return 'pdf';
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(fileType)) return 'image';
  if (['md', 'markdown', 'docx'].includes(fileType)) return 'text';
  return 'unsupported';
}

function normalizeType(fileType: string | null | undefined): string {
  return (fileType || '').toLowerCase();
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function formatDate(value: string): string {
  return value?.split('T')[0] || '';
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderInlineMarkdown(value: string): string {
  return escapeHtml(value)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>');
}

function renderMarkdown(source: string): string {
  const lines = source.replace(/\r\n/g, '\n').split('\n');
  const html: string[] = [];
  let inCodeBlock = false;
  let inList = false;

  // 预览只实现常用 Markdown 语法，并先转义原文，避免用户上传内容注入脚本。
  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      if (inList) {
        html.push('</ul>');
        inList = false;
      }
      html.push(inCodeBlock ? '</code></pre>' : '<pre><code>');
      inCodeBlock = !inCodeBlock;
      continue;
    }

    if (inCodeBlock) {
      html.push(`${escapeHtml(line)}\n`);
      continue;
    }

    if (!line.trim()) {
      if (inList) {
        html.push('</ul>');
        inList = false;
      }
      continue;
    }

    const heading = /^(#{1,6})\s+(.+)$/.exec(line);
    if (heading) {
      if (inList) {
        html.push('</ul>');
        inList = false;
      }
      const level = heading[1].length;
      html.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`);
      continue;
    }

    const listItem = /^\s*[-*]\s+(.+)$/.exec(line);
    if (listItem) {
      if (!inList) {
        html.push('<ul>');
        inList = true;
      }
      html.push(`<li>${renderInlineMarkdown(listItem[1])}</li>`);
      continue;
    }

    if (line.trim().startsWith('>')) {
      if (inList) {
        html.push('</ul>');
        inList = false;
      }
      html.push(`<blockquote>${renderInlineMarkdown(line.replace(/^\s*>\s?/, ''))}</blockquote>`);
      continue;
    }

    if (inList) {
      html.push('</ul>');
      inList = false;
    }
    html.push(`<p>${renderInlineMarkdown(line)}</p>`);
  }

  if (inCodeBlock) {
    html.push('</code></pre>');
  }
  if (inList) {
    html.push('</ul>');
  }

  return html.join('');
}

onMounted(load);
</script>

<template>
  <div class="resources-page">
    <div class="page-header">
      <div>
        <h2>资源分享</h2>
        <p>按标签与课程主题归档资料</p>
      </div>
      <NButton
        type="primary"
        @click="openUpload"
      >
        <template #icon>
          <CloudUploadOutline />
        </template>
        上传资源
      </NButton>
    </div>

    <section
      v-if="resources.length > 0"
      class="topic-panel"
    >
      <button
        class="topic-chip"
        :class="{ active: activeTopic === ALL_RESOURCE_TOPIC }"
        type="button"
        @click="selectTopic(ALL_RESOURCE_TOPIC)"
      >
        <span>全部主题</span>
        <strong>{{ resources.length }}</strong>
      </button>
      <button
        v-for="topic in topicOptions"
        :key="topic.label"
        class="topic-chip"
        :class="{ active: activeTopic === topic.label }"
        type="button"
        @click="selectTopic(topic.label)"
      >
        <span>{{ topic.label }}</span>
        <strong>{{ topic.count }}</strong>
      </button>
    </section>

    <div
      v-if="resources.length === 0 && !loading"
      class="empty"
    >
      <NEmpty description="暂无资源" />
    </div>

    <div
      v-else-if="visibleResourceCount === 0 && !loading"
      class="empty"
    >
      <NEmpty description="当前主题下暂无资源" />
    </div>

    <section
      v-for="group in topicGroups"
      :key="group.key"
      class="topic-section"
    >
      <div class="topic-section-header">
        <h3>{{ group.label }}</h3>
        <span>{{ group.count }} 份资源</span>
      </div>

      <button
        v-for="resource in group.items"
        :key="resource.id"
        class="resource-card"
        type="button"
        @click="openDetail(resource)"
      >
        <div class="card-row">
          <div class="file-icon">
            {{ typeIcons[normalizeType(resource.fileType)] || '📁' }}
          </div>
          <div class="card-body">
            <div class="file-name">
              {{ resource.fileName }}
            </div>
            <div class="file-meta">
              <span>{{ formatSize(resource.fileSize) }}</span>
              <span>{{ resource.fileType.toUpperCase() }}</span>
              <span>{{ resource.downloadCount }} 次下载</span>
              <span class="uploader-name">{{ resource.uploader?.nickname || '未知上传者' }}</span>
            </div>
            <div class="file-tags">
              <NTag
                v-for="tag in getResourceTopics(resource)"
                :key="`${resource.id}-${tag}`"
                size="tiny"
                type="info"
              >
                {{ tag }}
              </NTag>
            </div>
          </div>
        </div>
      </button>
    </section>

    <div
      v-if="loading"
      class="loading"
    >
      <NSpin />
    </div>

    <NModal
      v-model:show="detailVisible"
      preset="card"
      class="resource-modal"
      :title="selectedResource?.fileName || '资源详情'"
      :bordered="false"
    >
      <div
        v-if="detailLoading"
        class="modal-loading"
      >
        <NSpin />
      </div>
      <template v-else-if="selectedResource">
        <div class="detail-meta">
          <div class="detail-tags">
            <NTag size="small">
              {{ selectedResource.fileType.toUpperCase() }}
            </NTag>
            <NTag
              v-if="selectedResource.visibility === 'PUBLIC'"
              type="success"
              size="small"
            >
              公开
            </NTag>
            <NTag
              v-else-if="selectedResource.visibility === 'SPACE'"
              type="warning"
              size="small"
            >
              空间
            </NTag>
            <NTag
              v-else
              size="small"
            >
              私有
            </NTag>
          </div>
          <NSpace>
            <NButton
              secondary
              @click="handleDownload()"
            >
              <template #icon>
                <DownloadOutline />
              </template>
              下载
            </NButton>
            <NButton
              v-if="isUploader"
              secondary
              type="error"
              @click="handleDelete"
            >
              <template #icon>
                <TrashOutline />
              </template>
              删除
            </NButton>
          </NSpace>
        </div>

        <p
          v-if="selectedResource.description"
          class="resource-desc"
        >
          {{ selectedResource.description }}
        </p>

        <div class="info-grid">
          <div class="info-item">
            <span>大小</span>
            <strong>{{ formatSize(selectedResource.fileSize) }}</strong>
          </div>
          <div class="info-item">
            <span>下载</span>
            <strong>{{ selectedResource.downloadCount }} 次</strong>
          </div>
          <div class="info-item">
            <span>上传者</span>
            <strong>{{ selectedResource.uploader?.nickname || '未知' }}</strong>
          </div>
          <div class="info-item">
            <span>上传时间</span>
            <strong>{{ formatDate(selectedResource.createdAt) }}</strong>
          </div>
        </div>

        <div
          v-if="selectedResource.tags?.length"
          class="info-tags"
        >
          <NTag
            v-for="tag in selectedResource.tags"
            :key="tag"
            size="small"
            type="info"
          >
            {{ tag }}
          </NTag>
        </div>

        <section class="preview-section">
          <div class="preview-title">
            <span>文件预览</span>
            <NButton
              quaternary
              size="small"
              @click="loadPreview"
            >
              <template #icon>
                <EyeOutline />
              </template>
              刷新预览
            </NButton>
          </div>

          <div
            v-if="previewLoading"
            class="preview-state"
          >
            <NSpin />
          </div>
          <NAlert
            v-else-if="previewError"
            type="error"
            :show-icon="false"
          >
            {{ previewError }}
          </NAlert>
          <iframe
            v-else-if="getPreviewKind(selectedResource) === 'pdf'"
            class="preview-frame"
            :src="previewUrl"
            title="PDF 预览"
          />
          <img
            v-else-if="getPreviewKind(selectedResource) === 'image'"
            class="preview-image"
            :src="previewUrl"
            :alt="selectedResource.fileName"
          />
          <div
            v-else-if="getPreviewKind(selectedResource) === 'text' && previewText"
            class="text-preview"
          >
            <iframe
              v-if="['md', 'markdown'].includes(normalizeType(selectedResource.fileType))"
              class="markdown-frame"
              :srcdoc="markdownSrcdoc"
              title="Markdown 预览"
            />
            <pre
              v-else
              class="docx-preview"
            >{{ previewText.content || '未解析到可预览文本' }}</pre>
          </div>
          <NAlert
            v-else
            type="info"
            :show-icon="false"
          >
            当前格式暂不支持在线预览，可直接下载查看。
          </NAlert>
        </section>
      </template>
    </NModal>

    <NModal
      v-model:show="uploadVisible"
      preset="card"
      class="upload-modal"
      title="上传资源"
      :bordered="false"
    >
      <div class="upload-form">
        <label>选择文件（最大 50MB）</label>
        <NUpload
          :max="1"
          :default-upload="false"
          :file-list="uploadFileList"
          :accept="resourceAccept"
          @update:file-list="handleFileChange"
        >
          <NButton>
            <template #icon>
              <CloudUploadOutline />
            </template>
            选择文件
          </NButton>
        </NUpload>
        <div
          v-if="uploadFile"
          class="selected-file"
        >
          已选：{{ uploadFile.name }}（{{ formatSize(uploadFile.size) }}）
        </div>

        <label>可见性</label>
        <NSelect
          v-model:value="uploadVisibility"
          :options="visibilityOptions"
        />

        <label>标签 / 主题</label>
        <NDynamicTags
          :value="uploadTags"
          :max="8"
          type="info"
          round
          class="upload-tags"
          :input-props="{ placeholder: '输入标签后回车' }"
          @update:value="handleUploadTagsUpdate"
        />
        <div class="tag-hint">
          最多 8 个标签；粘贴空格、逗号或 # 分隔的文本会自动拆分。
        </div>

        <label>描述</label>
        <NInput
          v-model:value="uploadDescription"
          type="textarea"
          placeholder="简单描述资源内容..."
          maxlength="500"
        />

        <div class="actions">
          <NButton
            type="primary"
            :loading="uploadLoading"
            @click="submitUpload"
          >
            上传
          </NButton>
          <NButton @click="uploadVisible = false">
            取消
          </NButton>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.resources-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.page-header h2 {
  margin: 0;
}

.page-header p {
  margin: 4px 0 0;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.topic-panel {
  display: flex;
  gap: 10px;
  margin-bottom: 22px;
  padding-bottom: 2px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.topic-chip {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 12px;
  color: var(--cf-text-secondary);
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 999px;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.topic-chip:hover,
.topic-chip.active {
  color: var(--cf-primary-hover);
  background: color-mix(in srgb, var(--cf-primary) 12%, transparent);
  border-color: color-mix(in srgb, var(--cf-primary) 46%, transparent);
}

.topic-chip span {
  max-width: 140px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-chip strong {
  min-width: 20px;
  padding: 1px 6px;
  color: var(--cf-text-primary);
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  background: rgba(255, 255, 255, 0.36);
  border-radius: 999px;
}

:global(html[data-theme='dark']) .topic-chip {
  background: rgba(12, 12, 13, 0.26);
  border-color: rgba(255, 255, 255, 0.1);
}

:global(html[data-theme='dark']) .topic-chip strong {
  background: rgba(255, 255, 255, 0.08);
}

.empty {
  margin: 80px 0;
}

.topic-section {
  margin-bottom: 24px;
}

.topic-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.topic-section-header h3 {
  margin: 0;
  overflow: hidden;
  font-size: 17px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-section-header span {
  flex: 0 0 auto;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.resource-card {
  display: block;
  width: 100%;
  margin: 0 0 12px;
  padding: 24px 28px;
  text-align: left;
  color: var(--cf-text-primary);
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 18px;
  cursor: pointer;
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.3);
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.resource-card:hover {
  background: rgba(255, 255, 255, 0.32);
  border-color: rgba(0, 216, 191, 0.36);
  transform: translateY(-2px);
}

:global(html[data-theme='dark']) .resource-card {
  background: rgba(12, 12, 13, 0.3);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

:global(html[data-theme='dark']) .resource-card:hover {
  background: rgba(12, 12, 13, 0.42);
}

.card-row {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.file-icon {
  flex: 0 0 40px;
  width: 40px;
  font-size: 32px;
  line-height: 1;
  text-align: center;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.file-name {
  margin-bottom: 8px;
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--cf-text-muted);
  font-size: 13px;
}

.uploader-name {
  margin-left: auto;
}

.file-tags,
.info-tags,
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.loading,
.modal-loading,
.preview-state {
  padding: 24px;
  text-align: center;
}

.detail-meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.resource-desc {
  margin: 0 0 16px;
  color: var(--cf-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.info-item {
  min-width: 0;
  padding: 12px;
  background: color-mix(in srgb, var(--cf-bg-soft) 42%, transparent);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
}

.info-item span {
  display: block;
  margin-bottom: 4px;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.info-item strong {
  display: block;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-section {
  margin-top: 18px;
}

.preview-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  font-weight: 700;
}

.preview-frame {
  width: 100%;
  height: min(64vh, 720px);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  background: #fff;
}

.preview-image {
  display: block;
  max-width: 100%;
  max-height: 64vh;
  margin: 0 auto;
  border-radius: 10px;
  object-fit: contain;
}

.text-preview {
  max-height: 64vh;
  overflow: auto;
  padding: 18px;
  color: var(--cf-text-secondary);
  background: color-mix(in srgb, var(--cf-bg-elevated) 48%, transparent);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
}

.docx-preview {
  margin: 0;
  padding: 14px;
  overflow: auto;
  color: var(--cf-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  line-height: 1.7;
  white-space: pre-wrap;
  background: color-mix(in srgb, var(--cf-bg-muted) 76%, transparent);
  border-radius: 8px;
}

.markdown-frame {
  width: 100%;
  min-height: 420px;
  border: 0;
  border-radius: 10px;
  background: #fff;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upload-form label {
  margin-bottom: -6px;
  color: var(--cf-text-secondary);
  font-size: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-grid > div {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.selected-file {
  color: var(--cf-primary-hover);
  font-size: 13px;
}

.upload-tags {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
}

:global(html[data-theme='dark']) .upload-tags {
  background: rgba(12, 12, 13, 0.26);
  border-color: rgba(255, 255, 255, 0.1);
}

.tag-hint {
  margin-top: -6px;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 8px;
}

:global(.resource-modal.n-card),
:global(.upload-modal.n-card) {
  width: min(920px, calc(100vw - 32px));
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(15, 23, 42, 0.12);
  box-shadow: 0 32px 90px rgba(15, 23, 42, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.34);
  backdrop-filter: blur(6px);
}

:global(html[data-theme='dark'] .resource-modal.n-card),
:global(html[data-theme='dark'] .upload-modal.n-card) {
  background: rgba(12, 12, 13, 0.68);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 32px 90px rgba(0, 0, 0, 0.38), inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

:global(.upload-modal.n-card) {
  width: min(560px, calc(100vw - 32px));
}

@media (max-width: 720px) {
  .resources-page {
    padding: 16px 12px;
  }

  .topic-panel {
    margin-right: -12px;
    margin-left: -12px;
    padding: 0 12px 4px;
  }

  .topic-chip span {
    max-width: 112px;
  }

  .topic-section-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .topic-section-header h3 {
    white-space: normal;
  }

  .resource-card {
    padding: 18px;
  }

  .file-meta {
    gap: 8px;
  }

  .uploader-name {
    margin-left: 0;
  }

  .detail-meta,
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .info-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .preview-frame {
    height: 58vh;
  }
}
</style>
