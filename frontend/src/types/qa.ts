export interface QaQuestionVO {
  id: number;
  postId: number;
  bountyPoints: number;
  isSolved: boolean;
  acceptedCommentId: number | null;
  solvedAt: string | null;
}
