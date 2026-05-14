import { request } from './request';
import type { PostVO, CreatePostRequest, PostPageRequest } from '@/types/post';

export async function createPost(data: CreatePostRequest): Promise<PostVO> {
  const res = await request<PostVO>({ method: 'POST', url: '/posts', data });
  return res.data;
}

export async function getPosts(params: PostPageRequest): Promise<PostVO[]> {
  const res = await request<PostVO[]>({ method: 'GET', url: '/posts', params });
  return res.data;
}

export async function getPostById(id: number): Promise<PostVO> {
  const res = await request<PostVO>({ method: 'GET', url: `/posts/${id}` });
  return res.data;
}

export async function deletePost(id: number): Promise<void> {
  await request({ method: 'DELETE', url: `/posts/${id}` });
}

export async function toggleReaction(targetId: number, type: 'LIKE' | 'COLLECT', targetType: 'POST' | 'COMMENT' = 'POST'): Promise<boolean> {
  // 对于 COMMENT reaction，URL 中的 postId 仅作为路由占位，后端以 data.targetId 为准
  const urlId = targetType === 'POST' ? targetId : 0;
  const res = await request<boolean>({
    method: 'POST',
    url: `/posts/${urlId}/reactions`,
    data: { targetType, targetId, type },
  });
  return res.data;
}
