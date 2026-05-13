import { request } from './request';
import type { AchievementVO } from '@/types/achievement';

export async function getUserAchievements(userId: number): Promise<AchievementVO[]> {
  const res = await request<AchievementVO[]>({ method: 'GET', url: '/achievements', params: { userId } });
  return res.data;
}
