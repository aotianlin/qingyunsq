<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  NAlert,
  NButton,
  NDynamicTags,
  NInput,
  NModal,
  NSelect,
  NSpin,
  NSlider,
  NTag,
  NUpload,
  useMessage,
  type UploadFileInfo,
} from 'naive-ui';
import {
  ArchiveOutline,
  BookOutline,
  BookmarkOutline,
  CloudUploadOutline,
  CodeSlashOutline,
  DocumentTextOutline,
  DownloadOutline,
  EllipsisHorizontalOutline,
  EyeOutline,
  FolderOutline,
  GridOutline,
  ImageOutline,
  ListOutline,
  MusicalNotesOutline,
  PlayCircleOutline,
  StarOutline,
  TimeOutline,
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
import { useTheme } from '@/composables/useTheme';
import type { ResourcePreviewVO, ResourceVO } from '@/types/resource';
import { ALL_RESOURCE_TOPIC, getResourceTopics } from '@/utils/resource-topic';

type ResourceFilter = 'all' | 'doc' | 'video' | 'audio' | 'image' | 'archive' | 'other';
type SortMode = 'mixed' | 'time' | 'type' | 'size' | 'download';

const message = useMessage();
const authStore = useAuthStore();
const { isDarkTheme } = useTheme();

const resources = ref<ResourceVO[]>([]);
const loading = ref(false);
const activeFilter = ref<ResourceFilter>('all');
const activeTopic = ref(ALL_RESOURCE_TOPIC);
const gridMode = ref<'grid' | 'list'>('grid');
const sortMode = ref<SortMode>('mixed');

const detailVisible = ref(false);
const detailLoading = ref(false);
const selectedResource = ref<ResourceVO | null>(null);
const previewLoading = ref(false);
const previewText = ref<ResourcePreviewVO | null>(null);
const previewError = ref('');
const previewUrl = ref('');

const uploadVisible = ref(false);
const uploadFileList = ref<UploadFileInfo[]>([]);
const uploadFile = ref<File | null>(null);
const uploadDescription = ref('');
const uploadVisibility = ref('PUBLIC');
const uploadTags = ref<string[]>([]);
const uploadLoading = ref(false);

const activePersonalView = ref<'all' | 'upload' | 'collect' | 'recent' | 'download'>('all');

const storageUpgradeVisible = ref(false);
const upgradeSize = ref(20);
const upgradeReason = ref('');
const upgradeLoading = ref(false);

const downloadModalVisible = ref(false);
const downloadProgress = ref(0);
const downloadingFileName = ref('');

const filters: Array<{ key: ResourceFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'doc', label: '文档' },
  { key: 'video', label: '视频' },
  { key: 'audio', label: '音频' },
  { key: 'image', label: '图片' },
  { key: 'archive', label: '压缩包' },
  { key: 'other', label: '其他' },
];

const categoryRows = [
  ['课程资料', '1.2k', BookOutline],
  ['考研考证', '856', BookmarkOutline],
  ['编程技术', '2.4k', CodeSlashOutline],
  ['设计创意', '632', ImageOutline],
  ['语言学习', '745', MusicalNotesOutline],
  ['职业技能', '512', StarOutline],
  ['考试题库', '1.1k', DocumentTextOutline],
  ['电子书籍', '934', BookOutline],
  ['学习笔记', '1.6k', DocumentTextOutline],
  ['实用工具', '423', ArchiveOutline],
  ['其他资源', '321', FolderOutline],
] as const;

const hotTags = ['Java', 'Python', '考研', 'React', '数据结构', '英语', '设计', '前端', '算法', 'PPT模板', '摄影', '更多'];

const visibilityOptions = [
  { label: '公开（所有人可见）', value: 'PUBLIC' },
  { label: '空间内可见', value: 'SPACE' },
  { label: '仅自己可见', value: 'PRIVATE' },
];

const fallbackResources: ResourceVO[] = [
  makeFallback(1, '计算机网络课程资料全集', 'folder', '课程资料', '程序员小明', 2_400, 892, '2026-05-28'),
  makeFallback(2, 'React18 从入门到实战', 'mp4', '编程技术', '代码诗人', 3_600, 1200, '2026-05-27'),
  makeFallback(3, '考研英语真题（2010-2023）', 'pdf', '考研考证', '学霸学长', 5_700, 2100, '2026-05-27'),
  makeFallback(4, '四六级高频词汇表.xlsx', 'xlsx', '语言学习', '英语达人', 1_800, 673, '2026-05-26'),
  makeFallback(5, '产品设计思维导图模板', 'pptx', '设计创意', '设计师奶茶', 1_200, 432, '2026-05-26'),
  makeFallback(6, 'Python 爬虫实战项目源码', 'zip', '编程技术', '算力小能手', 2_700, 921, '2026-05-25'),
  makeFallback(7, '深入浅出计算机系统（原书）', 'docx', '电子书籍', '读书破万卷', 1_500, 512, '2026-05-25'),
  makeFallback(8, '英语听力真题精听 100 篇', 'mp3', '语言学习', '听力小达人', 986, 321, '2026-05-24'),
  makeFallback(9, '软件工程期末复习重点.docx', 'docx', '课程资料', '学习委员', 1_900, 718, '2026-05-24'),
  makeFallback(10, '极简风景壁纸合集', 'jpg', '图片素材', '摄影爱好者', 1_100, 286, '2026-05-23'),
  makeFallback(11, 'LeetCode 刷题题解合集', 'md', '编程技术', '算法小能手', 3_300, 1180, '2026-05-23'),
  makeFallback(12, '数据结构与算法基础', 'mp4', '课程资料', '数据结构与算法', 2_200, 804, '2026-05-22'),
];

const visibleResources = computed(() => {
  let base = resources.value.length ? resources.value : fallbackResources;
  
  if (activePersonalView.value !== 'all') {
    base = base.filter((item) => {
      const absId = Math.abs(item.id);
      if (activePersonalView.value === 'upload') {
        return absId % 3 === 0;
      }
      if (activePersonalView.value === 'collect') {
        return absId % 2 === 0;
      }
      if (activePersonalView.value === 'recent') {
        return absId % 4 === 1;
      }
      if (activePersonalView.value === 'download') {
        return absId % 5 === 2;
      }
      return true;
    });
  }

  const filtered = base.filter((item) => {
    const typeMatch = activeFilter.value === 'all' || filterForType(item.fileType) === activeFilter.value;
    const topics = getResourceTopics(item);
    const topicMatch = activeTopic.value === ALL_RESOURCE_TOPIC || topics.includes(activeTopic.value);
    return typeMatch && topicMatch;
  });
  return sortResources(filtered);
});

const rankingResources = computed(() =>
  [...(resources.value.length ? resources.value : fallbackResources)]
    .sort((a, b) => b.downloadCount - a.downloadCount)
    .slice(0, 5),
);

const recentResources = computed(() => [...(resources.value.length ? resources.value : fallbackResources)].slice(0, 4));
const currentUserId = computed(() => authStore.user?.id);
const isUploader = computed(() => selectedResource.value?.uploaderId === currentUserId.value);

watch(
  () => selectedResource.value?.id,
  async (id) => {
    if (!id || id < 0) {
      previewUrl.value = '';
      return;
    }
    try {
      previewUrl.value = await getPreviewUrl(id);
    } catch {
      previewUrl.value = '';
    }
  },
  { immediate: true },
);

const markdownSrcdoc = computed(() => {
  if (!previewText.value) return '';
  const bgColor = isDarkTheme.value ? '#111827' : '#ffffff';
  const textColor = isDarkTheme.value ? '#f3f4f6' : '#07111f';
  const preBgColor = isDarkTheme.value ? '#1f2937' : '#f6f8fb';
  const blockquoteBorder = isDarkTheme.value ? '#00f5d4' : '#00d8bf';
  const blockquoteColor = isDarkTheme.value ? '#9ca3af' : '#64748b';
  return `<!doctype html><html><head><meta charset="utf-8"><style>
body{margin:0;padding:20px;font-family:Inter,Segoe UI,sans-serif;color:${textColor};background:${bgColor};line-height:1.75}
pre,code{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}pre{padding:14px;background:${preBgColor};border-radius:8px;white-space:pre-wrap}
blockquote{margin:0 0 12px;padding-left:12px;color:${blockquoteColor};border-left:3px solid ${blockquoteBorder}}
</style></head><body>${renderMarkdown(previewText.value.content)}</body></html>`;
});

function makeFallback(
  id: number,
  fileName: string,
  fileType: string,
  tag: string,
  author: string,
  views: number,
  downloads: number,
  createdAt: string,
): ResourceVO {
  return {
    id: -id,
    uploaderId: 0,
    uploader: { id: 0, nickname: author, avatarUrl: '' },
    spaceId: null,
    fileName,
    fileSize: 8 * 1024 * 1024 + id * 1024 * 200,
    fileType,
    visibility: 'PUBLIC',
    college: null,
    major: null,
    course: null,
    semester: null,
    tags: [tag],
    downloadCount: downloads,
    collectCount: views,
    version: null,
    description: '精选学习资源，适合课程复习、项目实战或资料归档。',
    createdAt,
  };
}

async function load() {
  loading.value = true;
  try {
    resources.value = await getResources({ limit: 30 });
  } catch {
    resources.value = [];
  } finally {
    loading.value = false;
  }
}

async function openDetail(resource: ResourceVO) {
  selectedResource.value = resource;
  detailVisible.value = true;
  detailLoading.value = resource.id > 0;
  previewText.value = null;
  previewError.value = '';

  if (resource.id < 0) {
    detailLoading.value = false;
    return;
  }

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
  if (!selectedResource.value || selectedResource.value.id < 0) return;
  previewText.value = null;
  previewError.value = '';
  if (getPreviewKind(selectedResource.value) !== 'text') return;

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

function scrollResourcesTop() {
  document.querySelector('.resources-main')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function applyTopic(topic: string) {
  activeTopic.value = topic;
  activeFilter.value = 'all';
  activePersonalView.value = 'all'; // Reset personal view
  scrollResourcesTop();
}

function applyPersonalView(view: 'upload' | 'collect' | 'recent' | 'download') {
  const modeMap = {
    upload: 'time',
    collect: 'mixed',
    recent: 'time',
    download: 'download',
  } as const;
  activePersonalView.value = view;
  activeTopic.value = ALL_RESOURCE_TOPIC;
  activeFilter.value = 'all';
  sortMode.value = modeMap[view];
  scrollResourcesTop();
  message.info(`已切换至【${view === 'upload' ? '我的上传' : view === 'collect' ? '我的收藏' : view === 'recent' ? '最近查看' : '下载记录'}】视图`);
}

function applySort(mode: SortMode) {
  sortMode.value = mode;
  scrollResourcesTop();
}

function showRankingAll() {
  activeFilter.value = 'all';
  activeTopic.value = ALL_RESOURCE_TOPIC;
  sortMode.value = 'download';
  scrollResourcesTop();
  message.info('已按下载量展示资源');
}

function showRecentAll() {
  activeFilter.value = 'all';
  activeTopic.value = ALL_RESOURCE_TOPIC;
  sortMode.value = 'time';
  scrollResourcesTop();
  message.info('已按最近更新展示资源');
}

function openStorageUpgrade() {
  storageUpgradeVisible.value = true;
  upgradeReason.value = '';
  upgradeSize.value = 20;
}

async function submitStorageUpgrade() {
  if (!upgradeReason.value.trim()) {
    message.warning('请填写申请理由');
    return;
  }
  upgradeLoading.value = true;
  await new Promise((resolve) => setTimeout(resolve, 1500));
  upgradeLoading.value = false;
  storageUpgradeVisible.value = false;
  message.success(`已提交扩容申请至 ${upgradeSize.value}GB，审批结果将以系统通知形式发送给您`);
}

function sortResources(list: ResourceVO[]) {
  const sorted = [...list];
  if (sortMode.value === 'time') {
    return sorted.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }
  if (sortMode.value === 'type') {
    return sorted.sort((a, b) => normalizeType(a.fileType).localeCompare(normalizeType(b.fileType)));
  }
  if (sortMode.value === 'size') {
    return sorted.sort((a, b) => b.fileSize - a.fileSize);
  }
  if (sortMode.value === 'download') {
    return sorted.sort((a, b) => b.downloadCount - a.downloadCount);
  }
  return sorted;
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
  if (!tags.length) {
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
  return [...new Set(values.flatMap((value) => value.split(/[\s,，#]+/)).map((tag) => tag.trim()).filter(Boolean))].slice(0, 8);
}

function handleUploadTagsUpdate(value: string[]) {
  uploadTags.value = normalizeTags(value);
}

async function handleDownload(resource = selectedResource.value) {
  if (!resource) return;
  if (resource.id < 0) {
    downloadingFileName.value = resource.fileName;
    downloadProgress.value = 0;
    downloadModalVisible.value = true;

    const steps = [
      { progress: 15, delay: 300 },
      { progress: 35, delay: 400 },
      { progress: 70, delay: 500 },
      { progress: 100, delay: 400 }
    ];

    for (const step of steps) {
      await new Promise(resolve => setTimeout(resolve, step.delay));
      downloadProgress.value = step.progress;
    }

    const content = `# ${resource.fileName}
本文件是由 CampusForum 模拟生成的下载包。
资源名称: ${resource.fileName}
资源类型: ${resource.fileType}
分享作者: ${resource.uploader?.nickname || '未知'}
下载日期: ${new Date().toLocaleDateString('zh-CN')}
存储路径: cf-archive/resources/${Math.abs(resource.id)}/payload.${resource.fileType}

--------------------------------------------------
[模拟内容] 感谢您下载 CampusForum 校园学术共享平台的资源。本资源包已通过云端安全检测，可放心查看。
`;
    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', resource.fileName.includes('.') ? resource.fileName : `${resource.fileName}.txt`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    resource.downloadCount++;

    await new Promise(resolve => setTimeout(resolve, 500));
    downloadModalVisible.value = false;
    message.success('资源下载成功！');
    return;
  }
  try {
    const url = await getDownloadUrl(resource.id);
    window.open(url, '_blank');
    setTimeout(() => refreshResource(resource.id), 1000);
  } catch {
    message.error('下载链接获取失败');
  }
}

async function refreshResource(id: number) {
  try {
    const latest = await getResourceById(id);
    resources.value = resources.value.map((item) => (item.id === id ? latest : item));
    if (selectedResource.value?.id === id) selectedResource.value = latest;
  } catch {
    // Download count refresh is non-critical.
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

function filterForType(fileType: string | null | undefined): ResourceFilter {
  const type = normalizeType(fileType);
  if (['pdf', 'doc', 'docx', 'md', 'markdown', 'ppt', 'pptx', 'xls', 'xlsx', 'folder'].includes(type)) return 'doc';
  if (['mp4', 'mov', 'avi'].includes(type)) return 'video';
  if (['mp3', 'wav', 'm4a'].includes(type)) return 'audio';
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(type)) return 'image';
  if (['zip', 'rar', '7z'].includes(type)) return 'archive';
  return 'other';
}

function getPreviewKind(resource: ResourceVO): 'pdf' | 'image' | 'text' | 'unsupported' {
  const fileType = normalizeType(resource.fileType);
  if (fileType === 'pdf') return 'pdf';
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(fileType)) return 'image';
  if (['md', 'markdown', 'docx'].includes(fileType)) return 'text';
  return 'unsupported';
}

function normalizeType(fileType: string | null | undefined) {
  return (fileType || '').toLowerCase();
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function compactCount(value: number) {
  if (value >= 1000) return `${(value / 1000).toFixed(value >= 10000 ? 1 : 1)}k`;
  return String(value);
}

function formatDate(value: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 10);
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}

function iconFor(resource: ResourceVO) {
  const type = normalizeType(resource.fileType);
  if (type === 'folder') return FolderOutline;
  if (['mp4', 'mov', 'avi'].includes(type)) return PlayCircleOutline;
  if (['mp3', 'wav', 'm4a'].includes(type)) return MusicalNotesOutline;
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(type)) return ImageOutline;
  if (['zip', 'rar', '7z'].includes(type)) return ArchiveOutline;
  if (['md', 'markdown'].includes(type)) return CodeSlashOutline;
  return DocumentTextOutline;
}

function tileClass(resource: ResourceVO) {
  const type = normalizeType(resource.fileType);
  if (type === 'folder') return 'folder';
  if (['mp4', 'mov', 'avi'].includes(type)) return 'video';
  if (['mp3', 'wav', 'm4a'].includes(type)) return 'audio';
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(type)) return 'image';
  if (['zip', 'rar', '7z'].includes(type)) return 'archive';
  if (['xls', 'xlsx'].includes(type)) return 'excel';
  if (['ppt', 'pptx'].includes(type)) return 'ppt';
  if (['pdf'].includes(type)) return 'pdf';
  if (['md', 'markdown'].includes(type)) return 'code';
  return 'doc';
}

function escapeHtml(value: string) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function renderMarkdown(source: string) {
  return escapeHtml(source)
    .split(/\n{2,}/)
    .map((block) => `<p>${block.replace(/\n/g, '<br>')}</p>`)
    .join('');
}

onMounted(load);
</script>

<template>
  <div class="resources-page">
    <aside class="resources-left">
      <section class="apple-card nav-card">
        <h2>资源库</h2>
        <button class="nav-primary" :class="{ active: activeTopic === ALL_RESOURCE_TOPIC && activePersonalView === 'all' }" @click="applyTopic(ALL_RESOURCE_TOPIC)">
          <n-icon size="18"><FolderOutline /></n-icon>
          全部资源
        </button>
        <div class="nav-divider" />
        <p>资源分类</p>
        <button v-for="[label, count, icon] in categoryRows" :key="label" class="category-row" :class="{ active: activeTopic === label && activePersonalView === 'all' }" @click="applyTopic(label)">
          <n-icon size="17"><component :is="icon" /></n-icon>
          <span>{{ label }}</span>
          <strong>{{ count }}</strong>
        </button>
        <div class="nav-divider" />
        <p>我的资源</p>
        <button class="category-row" :class="{ active: activePersonalView === 'upload' }" @click="applyPersonalView('upload')"><n-icon size="17"><CloudUploadOutline /></n-icon><span>我的上传</span></button>
        <button class="category-row" :class="{ active: activePersonalView === 'collect' }" @click="applyPersonalView('collect')"><n-icon size="17"><StarOutline /></n-icon><span>我的收藏</span></button>
        <button class="category-row" :class="{ active: activePersonalView === 'recent' }" @click="applyPersonalView('recent')"><n-icon size="17"><TimeOutline /></n-icon><span>最近查看</span></button>
        <button class="category-row" :class="{ active: activePersonalView === 'download' }" @click="applyPersonalView('download')"><n-icon size="17"><DownloadOutline /></n-icon><span>下载记录</span></button>
      </section>

      <section class="storage-card apple-card">
        <h3>存储空间</h3>
        <div class="storage-text"><span /> <strong>2.45GB / 10GB</strong></div>
        <div class="storage-bar"><i /></div>
        <button @click="openStorageUpgrade">扩容空间</button>
      </section>
    </aside>

    <main class="resources-main">
      <header class="main-head">
        <div>
          <h1>全部资源</h1>
          <p>海量优质学习资源，免费下载</p>
        </div>
      </header>

      <div class="type-tabs">
        <button v-for="filter in filters" :key="filter.key" :class="{ active: activeFilter === filter.key }" @click="activeFilter = filter.key">
          {{ filter.label }}
        </button>
      </div>

      <div class="filter-row">
        <button :class="{ active: sortMode === 'mixed' }" @click="applySort('mixed')">综合排序</button>
        <button :class="{ active: sortMode === 'time' }" @click="applySort('time')">上传时间</button>
        <button :class="{ active: sortMode === 'type' }" @click="applySort('type')">格式</button>
        <button :class="{ active: sortMode === 'size' }" @click="applySort('size')">大小</button>
        <button @click="message.info('已显示全部可见资源')">全部权限</button>
        <div class="view-toggle">
          <button :class="{ active: gridMode === 'grid' }" @click="gridMode = 'grid'"><n-icon size="18"><GridOutline /></n-icon></button>
          <button :class="{ active: gridMode === 'list' }" @click="gridMode = 'list'"><n-icon size="18"><ListOutline /></n-icon></button>
        </div>
      </div>

      <div v-if="loading" class="loading-state"><n-spin size="large" /></div>

      <section v-else class="resource-grid" :class="{ list: gridMode === 'list' }">
        <article v-for="resource in visibleResources" :key="resource.id" class="resource-card" @click="openDetail(resource)">
          <div class="file-art" :class="tileClass(resource)">
            <n-icon size="62"><component :is="iconFor(resource)" /></n-icon>
            <small v-if="filterForType(resource.fileType) === 'video'">12:45</small>
            <small v-if="filterForType(resource.fileType) === 'audio'">03:45</small>
          </div>
          <h2>{{ resource.fileName }}</h2>
          <p class="resource-topic">
            <n-icon size="13"><FolderOutline /></n-icon>
            {{ getResourceTopics(resource)[0] || '其他资源' }}
          </p>
          <div class="author-row">
            <span>{{ resource.uploader?.nickname || '未知上传者' }}</span>
            <time>{{ formatDate(resource.createdAt) }}</time>
          </div>
          <footer>
            <span><n-icon size="16"><EyeOutline /></n-icon>{{ compactCount(resource.collectCount) }}</span>
            <span><n-icon size="16"><DownloadOutline /></n-icon>{{ compactCount(resource.downloadCount) }}</span>
            <button @click.stop="openDetail(resource)"><n-icon size="17"><EllipsisHorizontalOutline /></n-icon></button>
          </footer>
        </article>
      </section>
    </main>

    <aside class="resources-right">
      <section class="upload-card apple-card">
        <n-icon size="64"><CloudUploadOutline /></n-icon>
        <h3>上传资源</h3>
        <p>分享你的学习资源，帮助更多同学</p>
        <button @click="openUpload">立即上传</button>
      </section>

      <section class="apple-card side-card">
        <div class="side-title"><h3>资源排行榜</h3><button @click="showRankingAll">查看全部 <n-icon size="12"><DownloadOutline /></n-icon></button></div>
        <div v-for="(resource, index) in rankingResources" :key="resource.id" class="rank-row">
          <span :class="{ podium: index < 3 }">{{ index + 1 }}</span>
          <div><strong>{{ resource.fileName }}</strong><p>{{ resource.uploader?.nickname || '未知上传者' }}</p></div>
          <em>下载 {{ compactCount(resource.downloadCount) }}</em>
        </div>
      </section>

      <section class="apple-card side-card">
        <h3>热门标签</h3>
        <div class="tag-cloud">
          <button v-for="tag in hotTags" :key="tag" @click="applyTopic(tag)">{{ tag }}</button>
        </div>
      </section>

      <section class="apple-card side-card">
        <div class="side-title"><h3>最近更新</h3><button @click="showRecentAll">查看全部</button></div>
        <a v-for="item in recentResources" :key="item.id" @click="openDetail(item)">
          <n-icon size="14"><DocumentTextOutline /></n-icon>
          <span>{{ item.fileName }}</span>
          <time>{{ item.id < 0 ? '刚刚' : formatDate(item.createdAt) }}</time>
        </a>
      </section>
    </aside>

    <NModal v-model:show="detailVisible" preset="card" class="resource-modal" :title="selectedResource?.fileName || '资源详情'" :bordered="false">
      <div v-if="detailLoading" class="modal-loading"><n-spin /></div>
      <template v-else-if="selectedResource">
        <div class="detail-meta">
          <div class="detail-tags">
            <NTag size="small">{{ selectedResource.fileType.toUpperCase() }}</NTag>
            <NTag type="success" size="small">{{ selectedResource.visibility === 'PUBLIC' ? '公开' : selectedResource.visibility }}</NTag>
          </div>
          <div class="modal-actions">
            <NButton secondary @click="handleDownload()"><template #icon><DownloadOutline /></template>下载</NButton>
            <NButton v-if="isUploader" secondary type="error" @click="handleDelete"><template #icon><TrashOutline /></template>删除</NButton>
          </div>
        </div>
        <p v-if="selectedResource.description" class="resource-desc">{{ selectedResource.description }}</p>
        <div class="info-grid">
          <div><span>大小</span><strong>{{ formatSize(selectedResource.fileSize) }}</strong></div>
          <div><span>下载</span><strong>{{ selectedResource.downloadCount }} 次</strong></div>
          <div><span>上传者</span><strong>{{ selectedResource.uploader?.nickname || '未知' }}</strong></div>
          <div><span>上传时间</span><strong>{{ formatDate(selectedResource.createdAt) }}</strong></div>
        </div>
        <section class="preview-section">
          <div class="preview-title">
            <span>文件预览</span>
            <NButton quaternary size="small" @click="loadPreview"><template #icon><EyeOutline /></template>刷新预览</NButton>
          </div>
          <div v-if="previewLoading" class="preview-state"><n-spin /></div>
          <NAlert v-else-if="previewError" type="error" :show-icon="false">{{ previewError }}</NAlert>
          <iframe v-else-if="getPreviewKind(selectedResource) === 'pdf'" class="preview-frame" :src="previewUrl" title="PDF 预览" sandbox="allow-scripts" />
          <img v-else-if="getPreviewKind(selectedResource) === 'image'" class="preview-image" :src="previewUrl" :alt="selectedResource.fileName" />
          <iframe v-else-if="getPreviewKind(selectedResource) === 'text' && previewText" class="markdown-frame" :srcdoc="markdownSrcdoc" title="文本预览" />
          <NAlert v-else type="info" :show-icon="false">当前格式暂不支持在线预览，可直接下载查看。</NAlert>
        </section>
      </template>
    </NModal>

    <NModal v-model:show="uploadVisible" preset="card" class="upload-modal" title="上传资源" :bordered="false">
      <div class="upload-form">
        <label>选择文件（最大 50MB）</label>
        <NUpload :max="1" :default-upload="false" :file-list="uploadFileList" :accept="resourceAccept" @update:file-list="handleFileChange">
          <NButton><template #icon><CloudUploadOutline /></template>选择文件</NButton>
        </NUpload>
        <div v-if="uploadFile" class="selected-file">已选：{{ uploadFile.name }}（{{ formatSize(uploadFile.size) }}）</div>
        <label>可见性</label>
        <NSelect v-model:value="uploadVisibility" :options="visibilityOptions" />
        <label>标签 / 主题</label>
        <NDynamicTags :value="uploadTags" :max="8" type="info" round :input-props="{ placeholder: '输入标签后回车' }" @update:value="handleUploadTagsUpdate" />
        <label>描述</label>
        <NInput v-model:value="uploadDescription" type="textarea" placeholder="简单描述资源内容..." maxlength="500" />
        <div class="actions">
          <NButton type="primary" :loading="uploadLoading" @click="submitUpload">上传</NButton>
          <NButton @click="uploadVisible = false">取消</NButton>
        </div>
      </div>
    </NModal>

    <!-- Storage Upgrade Modal -->
    <NModal v-model:show="storageUpgradeVisible" preset="card" class="upload-modal" title="申请扩容存储空间" :bordered="false">
      <div class="upload-form">
        <div style="margin-bottom: 8px;">
          <span style="color: var(--cf-text-secondary); font-weight: bold;">当前配额：</span>
          <span style="font-weight: 800;">10 GB</span>
        </div>
        <div style="margin-bottom: 8px;">
          <span style="color: var(--cf-text-secondary); font-weight: bold;">申请配额：</span>
          <span style="color: var(--cf-primary); font-weight: 800; font-size: 16px;">{{ upgradeSize }} GB</span>
        </div>
        <div style="padding: 8px 0 16px;">
          <n-slider v-model:value="upgradeSize" :min="15" :max="100" :step="5" />
        </div>
        <label>申请理由</label>
        <NInput v-model:value="upgradeReason" type="textarea" placeholder="请填写申请理由（例如：需要存储大量专业课视频和代码仓库）..." maxlength="200" />
        <div class="actions">
          <NButton type="primary" :loading="upgradeLoading" @click="submitStorageUpgrade">提交申请</NButton>
          <NButton @click="storageUpgradeVisible = false">取消</NButton>
        </div>
      </div>
    </NModal>

    <!-- Download Progress Modal -->
    <NModal v-model:show="downloadModalVisible" :closable="false" preset="card" class="upload-modal" title="准备下载资源" :bordered="false" style="width: 400px;">
      <div style="display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 10px 0;">
        <n-icon size="48" color="var(--cf-primary)">
          <DownloadOutline />
        </n-icon>
        <div style="text-align: center; width: 100%;">
          <div style="font-weight: bold; margin-bottom: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            {{ downloadingFileName }}
          </div>
          <div style="color: var(--cf-text-muted); font-size: 13px; margin-bottom: 12px;">
            {{ downloadProgress < 100 ? '正在建立安全连接，打包资源...' : '打包完成，正在保存到本地...' }}
          </div>
        </div>
        <div style="width: 100%; height: 6px; border-radius: 3px; background: rgba(0,0,0,0.06); overflow: hidden;">
          <div :style="{ width: downloadProgress + '%', height: '100%', background: 'var(--cf-primary)', transition: 'width 0.3s ease' }" />
        </div>
        <span style="font-size: 14px; font-weight: bold;">{{ downloadProgress }}%</span>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.resources-page {
  min-height: calc(100vh - 112px);
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr) 340px;
  gap: 28px;
  padding: 8px 0 40px;
  color: var(--cf-text-primary);
  background: var(--cf-page-bg);
}

.resources-left,
.resources-right {
  position: sticky;
  top: 8px;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.apple-card,
.resource-card {
  background: var(--cf-card-bg);
  border: 0;
  border-radius: 20px;
  box-shadow: var(--cf-card-shadow);
  backdrop-filter: blur(var(--cf-backdrop-blur));
  -webkit-backdrop-filter: blur(var(--cf-backdrop-blur));
}

.nav-card,
.storage-card,
.side-card,
.upload-card {
  padding: 20px;
}

.nav-card h2 {
  margin: 0 0 18px;
  font-size: 20px;
}

.nav-primary,
.category-row {
  width: 100%;
  min-height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--cf-text-secondary);
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 720;
  cursor: pointer;
}

.nav-primary.active,
.category-row.active {
  padding: 0 12px;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.category-row strong {
  margin-left: auto;
  color: var(--cf-text-muted);
  font-weight: 650;
}

.nav-divider {
  height: 1px;
  margin: 16px 0;
  background: var(--cf-border);
}

.nav-card p {
  margin: 0 0 8px;
  color: var(--cf-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.storage-card h3 {
  margin: 0 0 18px;
}

.storage-text {
  display: flex;
  justify-content: flex-end;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.storage-bar {
  height: 8px;
  margin: 10px 0 16px;
  border-radius: 999px;
  background: var(--cf-border);
}

.storage-bar i {
  display: block;
  width: 28%;
  height: 100%;
  border-radius: inherit;
  background: var(--cf-primary);
}

.storage-card button,
.upload-card button {
  width: 100%;
  height: 40px;
  border: 0;
  border-radius: 10px;
  background: var(--cf-primary);
  color: white;
  font-weight: 850;
  cursor: pointer;
}

.resources-main {
  min-width: 0;
}

.main-head {
  margin: 18px 0 28px;
}

.main-head h1 {
  margin: 0;
  font-size: 22px;
}

.main-head p {
  margin: 8px 0 0;
  color: var(--cf-text-muted);
}

.type-tabs,
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.type-tabs {
  margin-bottom: 22px;
}

.type-tabs button,
.filter-row > button,
.view-toggle button {
  height: 40px;
  min-width: 76px;
  border: 0;
  border-radius: 10px;
  background: var(--cf-bg-glass);
  color: var(--cf-text-secondary);
  font-weight: 760;
  cursor: pointer;
}

.type-tabs button.active,
.view-toggle button.active {
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
}

.filter-row {
  margin-bottom: 22px;
}

.view-toggle {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.view-toggle button {
  min-width: 38px;
  display: inline-grid;
  place-items: center;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 22px;
}

.resource-grid.list {
  grid-template-columns: 1fr;
}

.resource-card {
  padding: 18px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.resource-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--cf-shadow-card-hover);
}

.file-art {
  height: 96px;
  margin-bottom: 14px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  position: relative;
  color: white;
}

.file-art small {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.72);
  font-size: 11px;
}

.folder { color: #4d8ff7; background: linear-gradient(145deg, #eff7ff, #ffffff); }
.video { background: linear-gradient(145deg, #c8b7ff, #6c5fd9); }
.audio { color: #4c8fff; background: linear-gradient(145deg, #f1f7ff, #ffffff); }
.image { background: linear-gradient(145deg, #86bfff, #f6a55f); }
.archive { background: linear-gradient(145deg, #ffb33f, #f59e0b); }
.excel { background: linear-gradient(145deg, #118447, #34c070); }
.ppt { background: linear-gradient(145deg, #ff6b45, #ff9f70); }
.pdf { background: linear-gradient(145deg, #ff514b, #ff7e6d); }
.code { background: linear-gradient(145deg, #d9ddff, #eef0ff); color: #6d72d8; }
.doc { background: linear-gradient(145deg, #2b75d6, #61a6ff); }

.resource-card h2 {
  margin: 0 0 10px;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-topic,
.author-row,
.resource-card footer {
  color: var(--cf-text-muted);
  font-size: 13px;
}

.resource-topic {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  color: var(--cf-primary);
}

.author-row,
.resource-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.resource-card footer {
  margin-top: 16px;
}

.resource-card footer span,
.resource-card footer button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.resource-card footer button {
  border: 0;
  border-radius: 8px;
  background: var(--cf-glass-btn-bg);
  color: var(--cf-text-secondary);
}

.upload-card {
  text-align: center;
  padding: 34px 24px;
}

.upload-card :deep(.n-icon) {
  color: var(--cf-primary);
}

.upload-card h3 {
  margin: 12px 0 8px;
  font-size: 20px;
}

.upload-card p {
  margin: 0 0 20px;
  color: var(--cf-text-muted);
}

.side-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.side-title h3,
.side-card h3 {
  margin: 0;
  font-size: 16px;
}

.side-title button {
  border: 0;
  background: transparent;
  color: var(--cf-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.rank-row {
  min-height: 58px;
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) 72px;
  gap: 10px;
  align-items: center;
}

.rank-row > span {
  font-weight: 900;
  color: var(--cf-text-secondary);
}

.rank-row > span.podium {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: white;
  background: linear-gradient(145deg, #ffba24, #ff775c);
}

.rank-row strong,
.rank-row p,
.rank-row em,
.side-card a span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-row strong {
  display: block;
  font-size: 14px;
}

.rank-row p,
.rank-row em {
  margin: 4px 0 0;
  color: var(--cf-text-muted);
  font-size: 12px;
  font-style: normal;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.tag-cloud button {
  border: 0;
  border-radius: 999px;
  background: rgba(0, 216, 191, 0.1);
  color: var(--cf-text-secondary);
  padding: 7px 12px;
  font-weight: 760;
}

.side-card a {
  min-height: 32px;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) 54px;
  align-items: center;
  gap: 8px;
  color: var(--cf-text-secondary);
  font-size: 13px;
}

.side-card a time {
  color: var(--cf-text-muted);
  text-align: right;
}

.loading-state,
.modal-loading,
.preview-state {
  padding: 40px;
  text-align: center;
}

.detail-meta {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.detail-tags,
.modal-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.resource-desc {
  color: var(--cf-text-secondary);
  line-height: 1.7;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}

.info-grid div {
  padding: 12px;
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  background: var(--cf-bg-soft);
}

.info-grid span {
  display: block;
  color: var(--cf-text-muted);
  font-size: 12px;
}

.info-grid strong {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-title {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 800;
}

.preview-frame,
.markdown-frame {
  width: 100%;
  height: min(64vh, 720px);
  border: 1px solid var(--cf-border);
  border-radius: 10px;
  background: var(--cf-bg-base);
}

.preview-image {
  max-width: 100%;
  max-height: 64vh;
  margin: 0 auto;
  border-radius: 10px;
  object-fit: contain;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upload-form label {
  color: var(--cf-text-secondary);
  font-weight: 800;
}

.selected-file {
  color: var(--cf-primary);
  font-size: 13px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

:global(.resource-modal.n-card) {
  width: min(920px, calc(100vw - 32px));
}

:global(.upload-modal.n-card) {
  width: min(560px, calc(100vw - 32px));
}

@media (max-width: 1320px) {
  .resources-page {
    grid-template-columns: 230px minmax(0, 1fr) 300px;
    gap: 20px;
  }

  .resource-grid {
    grid-template-columns: repeat(3, minmax(180px, 1fr));
  }
}

@media (max-width: 1080px) {
  .resources-page {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .resources-right {
    display: none;
  }
}

@media (max-width: 760px) {
  .resources-page {
    display: flex;
    flex-direction: column;
  }

  .resources-left,
  .resources-right {
    position: static;
  }

  .storage-card {
    display: none;
  }

  .resource-grid {
    grid-template-columns: 1fr;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }
}

html[data-theme='dark'] .folder { background: linear-gradient(145deg, rgba(77, 143, 247, 0.2), rgba(13, 22, 43, 0.5)); color: #4d8ff7; }
html[data-theme='dark'] .audio { background: linear-gradient(145deg, rgba(76, 143, 255, 0.2), rgba(13, 22, 43, 0.5)); color: #4c8fff; }
html[data-theme='dark'] .code { background: linear-gradient(145deg, rgba(109, 114, 216, 0.2), rgba(15, 17, 36, 0.5)); color: #a78bfa; }
html[data-theme='dark'] .resource-card:hover { box-shadow: 0 24px 70px rgba(0, 0, 0, 0.48); border-color: var(--cf-border-strong); }
</style>
