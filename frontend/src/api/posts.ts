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

export async function updatePost(id: number, data: {
  title?: string;
  content: string;
  topics?: string[];
  tags?: string[];
  attachments?: string;
}): Promise<PostVO> {
  const res = await request<PostVO>({ method: 'PUT', url: `/posts/${id}`, data });
  return res.data;
}

export async function toggleReaction(targetId: number, type: 'LIKE' | 'COLLECT'): Promise<boolean> {
  const res = await request<boolean>({
    method: 'POST',
    url: `/posts/${targetId}/reactions`,
    data: { targetType: 'POST', targetId, type },
  });
  return res.data;
}
