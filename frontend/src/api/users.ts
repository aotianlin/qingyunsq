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
