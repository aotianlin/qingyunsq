import { request } from './request';
import type { CommentVO, CreateCommentRequest } from '@/types/post';

export async function createComment(data: CreateCommentRequest): Promise<CommentVO> {
  const res = await request<CommentVO>({
    method: 'POST',
    url: `/posts/${data.postId}/comments`,
    data,
  });
  return res.data;
}

export async function getComments(postId: number, cursor?: number, limit = 20, qaSort?: boolean): Promise<CommentVO[]> {
  const res = await request<CommentVO[]>({
    method: 'GET',
    url: `/posts/${postId}/comments`,
    params: { cursor, limit, ...(qaSort ? { qaSort: true } : {}) },
  });
  return res.data;
}

export async function deleteComment(id: number): Promise<void> {
  await request({ method: 'DELETE', url: `/comments/${id}` });
}

export async function toggleCommentReaction(id: number, type: 'LIKE'): Promise<boolean> {
  const res = await request<boolean>({
    method: 'POST',
    url: `/comments/${id}/reactions`,
    data: { targetType: 'COMMENT', targetId: id, type },
  });
  return res.data;
}

export async function updateComment(id: number, content: string): Promise<CommentVO> {
  const res = await request<CommentVO>({
    method: 'PUT',
    url: `/comments/${id}`,
    data: { content },
  });
  return res.data;
}
