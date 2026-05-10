<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { NButton, NCard, NInput, NTag, NSpace, useMessage } from 'naive-ui';
import { getMyProfile, updateProfile } from '@/api/users';
import { useAuthStore } from '@/stores/auth';
import type { UserVO } from '@/types/user';

const message = useMessage();
const authStore = useAuthStore();

const user = ref<UserVO | null>(null);
const loading = ref(true);
const editing = ref(false);
const saving = ref(false);

const editForm = ref({
  nickname: '',
  bio: '',
  college: '',
  major: '',
  grade: '',
});

async function loadProfile() {
  loading.value = true;
  try {
    user.value = await getMyProfile();
    authStore.setUser(user.value);
  } catch {
    message.error('获取用户信息失败');
  } finally {
    loading.value = false;
  }
}

function startEdit() {
  if (!user.value) return;
  editForm.value = {
    nickname: user.value.nickname || '',
    bio: user.value.bio || '',
    college: user.value.college || '',
    major: user.value.major || '',
    grade: user.value.grade || '',
  };
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
}

async function saveProfile() {
  saving.value = true;
  try {
    const updated = await updateProfile(editForm.value);
    user.value = updated;
    authStore.setUser(updated);
    editing.value = false;
    message.success('保存成功');
  } catch {
    message.error('保存失败');
  } finally {
    saving.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <div class="profile-page">
    <NCard class="profile-card">
      <template v-if="loading">
        <p>加载中...</p>
      </template>
      <template v-else-if="user">
        <!-- 查看模式 -->
        <template v-if="!editing">
          <div class="profile-header">
            <div class="avatar">{{ user.nickname.charAt(0) }}</div>
            <div class="info">
              <h2>{{ user.nickname }}</h2>
              <p>{{ user.email }}</p>
              <p v-if="user.studentNo">学号: {{ user.studentNo }}</p>
              <p v-if="user.college || user.major">
                {{ user.college }} {{ user.major }} {{ user.grade }}
              </p>
            </div>
          </div>
          <div class="stats">
            <div class="stat-item">
              <span class="stat-value">{{ user.points }}</span>
              <span class="stat-label">积分</span>
            </div>
          </div>
          <div v-if="user.bio" class="bio">
            <p>{{ user.bio }}</p>
          </div>
          <NSpace class="actions">
            <NTag :type="user.role === 'TENANT_ADMIN' ? 'error' : 'info'">
              {{ user.role === 'USER' ? '普通用户' : '管理员' }}
            </NTag>
            <NButton size="small" @click="startEdit">编辑资料</NButton>
          </NSpace>
        </template>

        <!-- 编辑模式 -->
        <template v-else>
          <h3>编辑个人资料</h3>
          <div class="edit-form">
            <label>昵称</label>
            <NInput v-model:value="editForm.nickname" class="field" />
            <label>个人简介</label>
            <NInput v-model:value="editForm.bio" type="textarea" class="field" />
            <label>学院</label>
            <NInput v-model:value="editForm.college" class="field" />
            <label>专业</label>
            <NInput v-model:value="editForm.major" class="field" />
            <label>年级</label>
            <NInput v-model:value="editForm.grade" class="field" />
            <NSpace class="edit-actions">
              <NButton type="primary" :loading="saving" @click="saveProfile">保存</NButton>
              <NButton @click="cancelEdit">取消</NButton>
            </NSpace>
          </div>
        </template>
      </template>
    </NCard>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 600px;
  margin: 40px auto;
  padding: 0 16px;
}
.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
}
.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #18a058;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: bold;
}
.info h2 { margin: 0 0 4px; }
.info p { margin: 2px 0; color: #666; font-size: 14px; }
.stats {
  display: flex;
  gap: 32px;
  margin: 20px 0;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stat-value { font-size: 24px; font-weight: bold; color: #18a058; }
.stat-label { font-size: 12px; color: #999; }
.bio {
  margin: 16px 0;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 8px;
}
.actions { margin-top: 16px; }
.edit-form { max-width: 400px; }
.edit-form label { display: block; margin: 12px 0 4px; font-size: 14px; color: #666; }
.edit-form .field { margin-bottom: 4px; }
.edit-actions { margin-top: 20px; }
</style>
