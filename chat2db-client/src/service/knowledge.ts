import createRequest from './base';
import { IKnowledgeDocument, IKnowledgeDocumentPageParams, IKnowledgeDocumentPageResponse } from '@/typings';

const getKnowledgeDocumentList = createRequest<IKnowledgeDocumentPageParams, IKnowledgeDocumentPageResponse>(
  '/api/ai/knowledge/document/list',
  { method: 'get' },
);

const getKnowledgeDocumentDetail = createRequest<{ id: number }, IKnowledgeDocument>(
  '/api/ai/knowledge/document/:id',
  { method: 'get' },
);

const deleteKnowledgeDocument = createRequest<{ id: number }, void>(
  '/api/ai/knowledge/document/:id',
  { method: 'delete' },
);

async function uploadKnowledgeDocument(payload: { name?: string; file: File }): Promise<IKnowledgeDocument> {
  const formData = new FormData();
  if (payload.name) {
    formData.append('name', payload.name);
  }
  formData.append('file', payload.file);

  const headers: Record<string, string> = {};
  const chat2db = localStorage.getItem('Chat2db');
  if (chat2db) {
    headers.Chat2db = chat2db;
  }

  const response = await fetch(`${window._BaseURL}/api/ai/knowledge/document/upload`, {
    method: 'POST',
    body: formData,
    credentials: 'include',
    headers,
  });

  const result = await response.json();
  if (!result?.success) {
    throw new Error(result?.errorMessage || 'Knowledge upload failed');
  }
  return result.data;
}

export default {
  getKnowledgeDocumentList,
  getKnowledgeDocumentDetail,
  deleteKnowledgeDocument,
  uploadKnowledgeDocument,
};
