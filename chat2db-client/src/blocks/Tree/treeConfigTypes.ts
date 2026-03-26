import { TreeNodeType, OperationColumn } from '@/constants';
import { ITreeNode } from '@/typings';

export type ITreeConfig = Partial<{ [key in TreeNodeType]: ITreeConfigItem }>;

export interface ITreeConfigItem {
  icon?: string;
  getChildren?: (params: any, options?: any) => Promise<ITreeNode[]>;
  next?: TreeNodeType;
  operationColumn?: OperationColumn[];
}
