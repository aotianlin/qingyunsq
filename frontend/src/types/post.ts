export interface UserBrief {
  id: number;
  nickname: string;
  avatarUrl: string;
  email: string;
}

export interface PostVO {
  id: number;
  authorId: number;
  author: UserBrief | null;
  scope: string;
  spaceId: number | null;
  type: string;
  title: string | null;
  content: string;
  topics: string[];
  tags: string[];
  viewCount: number;
  likeCount: number;
  commentCount: number;
  isPinned: number;
  isEssence: number;
  status: number;
  liked: boolean;
  collected: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CommentVO {
  id: number;
  postId: number;
  parentId: number | null;
  replyToId: number | null;
  authorId: number;
  author: UserBrief | null;
  content: string;
  likeCount: number;
  replies: CommentVO[];
  createdAt: string;
}

export interface CreatePostRequest {
  scope?: string;
  spaceId?: number;
  type?: string;
  title?: string;
  content: string;
  topics?: string[];
  tags?: string[];
  bountyPoints?: number;
  quotePostId?: number;
}

export interface CreateCommentRequest {
  postId: number;
  parentId?: number;
  replyToId?: number;
  content: string;
}

export interface PostPageRequest {
  scope?: string;
  authorId?: number;
  sort?: string;
  cursor?: number;
  cursorId?: number;
  limit?: number;
}

export interface ReactionRequest {
  targetType: string;
  targetId: number;
  type: string;
}
