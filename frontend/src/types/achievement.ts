export interface AchievementVO {
  id: number;
  code: string;
  name: string;
  description: string;
  iconUrl: string | null;
  awarded: boolean;
  awardedAt: string | null;
}
