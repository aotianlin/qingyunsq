import type { UserBrief } from './post';

export interface NotificationVO {
  id: number;
  receiverId: number;
  senderId: number;
  sender: UserBrief | null;
  type: string;
  title: string;
  content: string;
  redirectUrl: string;
  isRead: boolean;
  createdAt: string;
}
