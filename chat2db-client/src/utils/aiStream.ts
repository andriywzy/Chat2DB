export interface IAiStreamPayload {
  content?: string;
  errorCode?: string;
  errorMessage?: string;
  responseMode?: string;
  schemaSources?: IAiSchemaSource[];
  validationWarning?: string;
}

export interface IAiSchemaSource {
  dataSourceId?: number;
  dataSourceAlias?: string;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
  currentDataSource?: boolean;
  sourceType?: string;
  score?: number;
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
