export interface IAiStreamPayload {
  content?: string;
}

export const AI_STREAM_DONE = '[DONE]';

export function isAiStreamDone(message: string) {
  return message === AI_STREAM_DONE;
}

export function parseAiStreamMessage(message: string): IAiStreamPayload {
  if (!message) {
    return {};
  }

  try {
    return JSON.parse(message);
  } catch (_error) {
    return {
      content: message,
    };
  }
}
