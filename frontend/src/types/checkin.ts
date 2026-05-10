export interface UserBrief {
  id: number;
  nickname: string;
  avatarUrl: string;
}

export interface CheckinChallengeVO {
  id: number;
  spaceId: number | null;
  creatorId: number;
  creator: UserBrief | null;
  name: string;
  description: string;
  startDate: string;
  endDate: string;
  rule: string | null;
  memberCount: number;
  status: number;
  isMember: boolean;
  myTotalDays: number;
  myConsecutiveDays: number;
  createdAt: string;
}

export interface CheckinRecordVO {
  id: number;
  challengeId: number;
  userId: number;
  user: UserBrief | null;
  checkinDate: string;
  content: string;
  imageUrls: string[];
  createdAt: string;
}

export interface CreateCheckinChallengeRequest {
  name: string;
  description?: string;
  spaceId?: number;
  startDate: string;
  endDate: string;
  rule?: string;
}

export interface CreateCheckinRecordRequest {
  content?: string;
  imageUrls?: string[];
}

export interface LeaderboardEntry {
  userId: number;
  userName: string;
  avatarUrl: string;
  totalDays: number;
  currentStreak: number;
}
