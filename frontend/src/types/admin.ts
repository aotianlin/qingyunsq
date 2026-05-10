export interface DashboardVO {
  userCount: number;
  postCount: number;
  spaceCount: number;
  commentCount: number;
  todayPostCount: number;
  todayUserCount: number;
}

export interface AuditLogVO {
  id: number;
  operatorId: number;
  operatorName: string;
  action: string;
  targetType: string;
  targetId: number;
  detail: string;
  ipAddress: string;
  createdAt: string;
}
