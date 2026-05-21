export interface MessageVO {
  id: number
  senderId: number
  receiverId: number
  sender: {
    id: number
    nickname: string
    avatarUrl?: string
  } | null
  receiver: {
    id: number
    nickname: string
    avatarUrl?: string
  } | null
  content: string
  imageUrl?: string
  isRead: boolean
  createdAt: string
}
