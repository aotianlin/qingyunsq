export interface SearchResult {
  type: string;
  id: number;
  title: string;
  description: string;
  author: {
    id: number;
    nickname: string;
    avatarUrl: string;
  } | null;
  createdAt: string;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  downloadCount: number;
  category: string;
  fileType: string;
  fileSize: number;
  memberCount: number;
  postCount: number;
}
