import { request } from './request';
import type { SpaceVO, SpaceMemberVO, CreateSpaceRequest, UpdateSpaceRequest } from '@/types/space';
import type { PostVO } from '@/types/post';

export async function createSpace(data: CreateSpaceRequest): Promise<SpaceVO> {
  const res = await request<SpaceVO>({ method: 'POST', url: '/spaces', data });
  return res.data;
}

export async function getSpaces(params: {
  category?: string;
  cursor?: number;
  limit?: number;
}): Promise<SpaceVO[]> {
  const res = await request<SpaceVO[]>({ method: 'GET', url: '/spaces', params });
  return res.data;
}

export async function getSpaceById(id: number): Promise<SpaceVO> {
  const res = await request<SpaceVO>({ method: 'GET', url: `/spaces/${id}` });
  return res.data;
}

export async function updateSpace(id: number, data: UpdateSpaceRequest): Promise<SpaceVO> {
  const res = await request<SpaceVO>({ method: 'PUT', url: `/spaces/${id}`, data });
  return res.data;
}

export async function joinSpace(id: number): Promise<SpaceVO> {
  const res = await request<SpaceVO>({ method: 'POST', url: `/spaces/${id}/join` });
  return res.data;
}

export async function leaveSpace(id: number): Promise<void> {
  await request({ method: 'POST', url: `/spaces/${id}/leave` });
}

export async function getSpaceMembers(
  id: number,
  cursor?: number,
  limit?: number,
): Promise<SpaceMemberVO[]> {
  const res = await request<SpaceMemberVO[]>({
    method: 'GET',
    url: `/spaces/${id}/members`,
    params: { cursor, limit },
  });
  return res.data;
}

export async function getSpacePosts(
  id: number,
  cursor?: number,
  limit?: number,
): Promise<PostVO[]> {
  const res = await request<PostVO[]>({
    method: 'GET',
    url: `/spaces/${id}/posts`,
    params: { cursor, limit },
  });
  return res.data;
}

export async function handleMember(
  spaceId: number,
  userId: number,
  action: 'approve' | 'remove',
): Promise<void> {
  await request({
    method: 'PUT',
    url: `/spaces/${spaceId}/members/${userId}`,
    params: { action },
  });
}

export async function dismissSpace(id: number): Promise<void> {
  await request({ method: 'DELETE', url: `/spaces/${id}` });
}
