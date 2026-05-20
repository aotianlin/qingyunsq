<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NButton, NInput, NSelect, NCard, NUpload, useMessage, type UploadFileInfo } from 'naive-ui';
import { resourceAccept, uploadResource } from '@/api/resources';

const router = useRouter();
const route = useRoute();
const message = useMessage();

const file = ref<File | null>(null);
const description = ref('');
const visibility = ref('PUBLIC');
const college = ref('');
const major = ref('');
const course = ref('');
const loading = ref(false);
const spaceId = ref<number | undefined>();
const spaceName = ref('');

const visibilityOptions = [
  { label: '公开（所有人可见）', value: 'PUBLIC' },
  { label: '空间内可见', value: 'SPACE' },
  { label: '仅自己可见', value: 'PRIVATE' },
];

function handleFileChange(fileList: UploadFileInfo[]) {
  if (fileList.length > 0) {
    file.value = fileList[0].file || null;
  }
}

onMounted(() => {
  const spaceIdParam = Number(route.query.spaceId);
  if (Number.isFinite(spaceIdParam) && spaceIdParam > 0) {
    spaceId.value = spaceIdParam;
    spaceName.value = String(route.query.spaceName || '当前学习圈');
    visibility.value = 'SPACE';
  }
});

async function submit() {
  if (!file.value) {
    message.warning('请选择文件');
    return;
  }
  loading.value = true;
  try {
    const resource = await uploadResource(file.value, {
      visibility: visibility.value,
      college: college.value.trim() || undefined,
      major: major.value.trim() || undefined,
      course: course.value.trim() || undefined,
      description: description.value.trim() || undefined,
      spaceId: spaceId.value,
    });
    message.success('上传成功');
    router.push(`/resources/${resource.id}`);
  } catch {
    message.error('上传失败');
  }
  loading.value = false;
}

function cancel() {
  router.back();
}
</script>

<template>
  <div class="upload-page">
    <NCard :title="spaceId ? `上传到 ${spaceName}` : '上传资源'">
      <div class="form">
        <label>选择文件（最大 50MB）</label>
        <NUpload
          :max="1"
          :accept="resourceAccept"
          @update:file-list="handleFileChange"
        >
          <NButton>选择文件</NButton>
        </NUpload>
        <div
          v-if="file"
          class="selected-file"
        >
          已选: {{ file.name }} ({{ (file.size / 1024 / 1024).toFixed(1) }} MB)
        </div>

        <label>可见性</label>
        <NSelect
          v-model:value="visibility"
          :options="visibilityOptions"
        />

        <label>学院</label>
        <NInput
          v-model:value="college"
          placeholder="例如：计算机学院"
          maxlength="64"
        />

        <label>专业</label>
        <NInput
          v-model:value="major"
          placeholder="例如：软件工程"
          maxlength="64"
        />

        <label>课程</label>
        <NInput
          v-model:value="course"
          placeholder="例如：Java程序设计"
          maxlength="128"
        />

        <label>描述</label>
        <NInput
          v-model:value="description"
          type="textarea"
          placeholder="简单描述资源内容..."
          maxlength="500"
        />

        <div class="actions">
          <NButton
            type="primary"
            :loading="loading"
            @click="submit"
          >
            上传
          </NButton>
          <NButton @click="cancel">
            取消
          </NButton>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.upload-page {
  max-width: 520px;
  margin: 40px auto;
  padding: 0 16px;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.form label {
  font-size: 14px;
  color: #666;
  margin-bottom: -8px;
}
.selected-file {
  font-size: 13px;
  color: #18a058;
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
</style>
