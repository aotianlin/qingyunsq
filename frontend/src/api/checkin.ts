import { request } from './request';
import type {
  CheckinChallengeVO,
  CheckinRecordVO,
  CreateCheckinChallengeRequest,
  CreateCheckinRecordRequest,
  LeaderboardEntry,
} from '@/types/checkin';

export async function createChallenge(data: CreateCheckinChallengeRequest): Promise<CheckinChallengeVO> {
  const res = await request<CheckinChallengeVO>({ method: 'POST', url: '/checkin/challenges', data });
  return res.data;
}

export async function getChallenges(params: {
  spaceId?: number;
  cursor?: number;
  limit?: number;
}): Promise<CheckinChallengeVO[]> {
  const res = await request<CheckinChallengeVO[]>({ method: 'GET', url: '/checkin/challenges', params });
  return res.data;
}

export async function getChallengeById(id: number): Promise<CheckinChallengeVO> {
  const res = await request<CheckinChallengeVO>({ method: 'GET', url: `/checkin/challenges/${id}` });
  return res.data;
}

export async function updateChallenge(
  id: number,
  data: CreateCheckinChallengeRequest,
): Promise<CheckinChallengeVO> {
  const res = await request<CheckinChallengeVO>({ method: 'PUT', url: `/checkin/challenges/${id}`, data });
  return res.data;
}

export async function checkin(
  id: number,
  data: CreateCheckinRecordRequest,
): Promise<CheckinRecordVO> {
  const res = await request<CheckinRecordVO>({ method: 'POST', url: `/checkin/challenges/${id}/checkin`, data });
  return res.data;
}

export async function getRecords(
  id: number,
  cursor?: number,
  limit?: number,
): Promise<CheckinRecordVO[]> {
  const res = await request<CheckinRecordVO[]>({
    method: 'GET',
    url: `/checkin/challenges/${id}/records`,
    params: { cursor, limit },
  });
  return res.data;
}

export async function getLeaderboard(id: number): Promise<LeaderboardEntry[]> {
  const res = await request<LeaderboardEntry[]>({ method: 'GET', url: `/checkin/challenges/${id}/leaderboard` });
  return res.data;
}

export async function deleteChallenge(id: number): Promise<void> {
  await request({ method: 'DELETE', url: `/checkin/challenges/${id}` });
}

export async function shareCheckinRecord(recordId: number): Promise<{ postId: number }> {
  const res = await request<{ postId: number }>({ method: 'POST', url: `/checkin/records/${recordId}/share` });
  return res.data;
}
