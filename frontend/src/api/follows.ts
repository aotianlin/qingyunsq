import { request } from './request';
import type { UserVO } from '@/types/user';

export async function follow(followeeId: number): Promise<void> {
  await request({ method: 'POST', url: `/follows/${followeeId}` });
}

export async function unfollow(followeeId: number): Promise<void> {
  await request({ method: 'DELETE', url: `/follows/${followeeId}` });
}

export async function isFollowing(targetId: number): Promise<boolean> {
  const res = await request<{ following: boolean }>({ method: 'GET', url: `/follows/check/${targetId}` });
  return res.data.following;
}

export async function getUserFollowers(userId: number, cursor?: number, limit = 20): Promise<UserVO[]> {
  const res = await request<UserVO[]>({ method: 'GET', url: `/follows/${userId}/followers`, params: { cursor, limit } });
  return res.data;
}

export async function getUserFollowing(userId: number, cursor?: number, limit = 20): Promise<UserVO[]> {
  const res = await request<UserVO[]>({ method: 'GET', url: `/follows/${userId}/following`, params: { cursor, limit } });
  return res.data;
}

export async function getFollowCounts(userId: number): Promise<{ followers: number; following: number }> {
  const res = await request<{ followers: number; following: number }>({ method: 'GET', url: `/follows/${userId}/counts` });
  return res.data;
}
