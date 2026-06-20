export interface UserVO {
  id: number;
  studentNo: string;
  email: string;
  nickname: string;
  avatarUrl: string;
  bio: string;
  college: string;
  major: string;
  grade: string;
  role: string;
  profileCoverUrl?: string;
  status: number;
  lastLoginAt: string;
  createdAt: string;
}
