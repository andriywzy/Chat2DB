import { DatabaseTypeCode } from '@/constants';

export interface IViewAllTableProps {
  className?: string;
  uniqueData: {
    dataSourceId: string;
    dataSourceName: string;
    databaseType: DatabaseTypeCode;
    databaseName?: string;
    schemaName?: string;
  };
}
