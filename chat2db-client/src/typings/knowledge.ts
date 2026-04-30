import { IPageParams, IPageResponse } from './common';

export type KnowledgeDocumentStatus = 'PROCESSING' | 'READY' | 'FAILED';

export interface IKnowledgeDocument {
  id: number;
  gmtCreate: string;
  gmtModified: string;
  userId: number;
  name: string;
  fileName: string;
  fileType: string;
  status: KnowledgeDocumentStatus;
  sentenceCount: number;
  wordCount: number;
  vectorCount: number;
  contentPreview?: string;
  errorMessage?: string;
}

export interface IKnowledgeDocumentPageParams extends IPageParams {
  status?: KnowledgeDocumentStatus;
}

export interface IKnowledgeDocumentPageResponse extends IPageResponse<IKnowledgeDocument> {}

export interface IKnowledgeSearchSource {
  id: number;
  documentId?: number;
  documentName?: string;
  fileType?: string;
  content: string;
  wordCount?: number;
  score?: number;
}

export interface IKnowledgeSearchContextResponse {
  knowledgeList: IKnowledgeSearchSource[];
}
