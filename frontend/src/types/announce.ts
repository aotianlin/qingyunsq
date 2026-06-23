import type { PostVO } from '@/types/post';

export interface OfficialAnnouncementItem {
  id: string;
  title: string;
  summary: string;
  description?: string;
  buttonText?: string;
  imageUrl?: string;
  badge?: string;
  meta?: string;
  link?: string;
}

export interface HotArticle {
  id: string;
  articleId?: number;
  postId?: number;
  title: string;
  heatLabel: string;
  hasArticle?: boolean;
  summary?: string;
  source?: string;
  publishedAt?: string;
}

export interface AiRecommendationItem {
  id: string;
  title: string;
  categoryLabel: string;
  viewCount: number;
  thumbnailUrl?: string;
  targetUrl: string;
  reason?: string;
}

export interface HotTopicItem {
  topic: string;
  heat: number;
  heatLabel?: string;
  targetUrl?: string;
}

export interface SidebarSummary {
  checkinStreakDays: number;
  checkedToday: boolean;
  points: number;
  nextRewardLabel?: string;
}

export interface PostDetailAuthorSummary {
  authorId: number;
  nickname: string;
  avatarUrl?: string;
  bio?: string;
  label?: string;
  postCount: number;
  followerCount: number;
  likeCount: number;
}

export interface PostDetailRecommendationItem {
  id: string;
  postId?: number;
  title: string;
  summary?: string;
  viewCount: number;
  publishedAt?: string;
  targetUrl: string;
}

export interface GetDailyHotArticlesRequest {
  date?: string;
  limit?: number;
}

export interface GetDailyHotArticlesResponse {
  items: HotArticle[];
  generatedAt: string;
}

export interface ReportHotArticleClickRequest {
  articleId: string;
  postId?: number;
  source?: string;
}

export interface ReportHotArticleClickResponse {
  accepted: boolean;
}

export interface GetOfficialAnnouncementsRequest {
  limit?: number;
  placement?: 'SQUARE' | 'HOME';
}

export interface GetOfficialAnnouncementsResponse {
  items: OfficialAnnouncementItem[];
  generatedAt: string;
}

export interface GetAiRecommendationsRequest {
  limit?: number;
}

export interface GetAiRecommendationsResponse {
  items: AiRecommendationItem[];
  generatedAt: string;
}

export interface GetHotTopicsRequest {
  limit?: number;
}

export interface GetHotTopicsResponse {
  items: HotTopicItem[];
  generatedAt: string;
}

export interface GetPostDetailSidebarRequest {
  postId: number;
  authorId: number;
  limit?: number;
}

export interface GetPostDetailSidebarResponse {
  author: PostDetailAuthorSummary;
  recommendations: PostDetailRecommendationItem[];
  generatedAt: string;
}

export type MockArticlePost = PostVO & {
  isMockArticle?: true;
};
