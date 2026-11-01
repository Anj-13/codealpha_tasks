import { postJson } from './client';
import type { ChatReply } from './types';

export function sendChatMessage(message: string): Promise<ChatReply> {
  return postJson<ChatReply>('/api/chat', { message });
}
