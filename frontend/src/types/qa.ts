export interface QaQuestionVO {
  id: number;
  postId: number;
  isSolved: boolean;
  acceptedCommentId: number | null;
  solvedAt: string | null;
}
