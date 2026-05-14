import { request } from './request';
import type { UserVO } from '@/types/user';

export async function getMyProfile(): Promise<UserVO> {
  const res = await request<UserVO>({ method: 'GET', url: '/users/me' });
  return res.data;
}

export async function updateProfile(data: {
  nickname?: string;
  avatarUrl?: string;
  bio?: string;
  college?: string;
  major?: string;
  grade?: string;
}): Promise<UserVO> {
  const res = await request<UserVO>({ method: 'PUT', url: '/users/me', data });
  return res.data;
}

export async function getUserById(id: number): Promise<UserVO> {
  const res = await request<UserVO>({ method: 'GET', url: `/users/${id}` });
  return res.data;
}

export async function getMuteSettings(): Promise<string[]> {
  const res = await request<string[]>({ method: 'GET', url: '/users/me/mute-settings' });
  return res.data;
}

export async function updateMuteSettings(mutedTypes: string[]): Promise<void> {
  await request({ method: 'PUT', url: '/users/me/mute-settings', data: { mutedTypes } });
}

export async function getTagSubscriptions(): Promise<string[]> {
  const res = await request<string[]>({ method: 'GET', url: '/users/me/tag-subscriptions' });
  return res.data;
}

export async function updateTagSubscriptions(tags: string[]): Promise<void> {
  await request({ method: 'PUT', url: '/users/me/tag-subscriptions', data: { tags } });
}
