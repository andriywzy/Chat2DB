import { DatabaseTypeCode } from '@/constants';
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import { addIntelliSenseField } from './field';
import i18n from '@/i18n';
import { compatibleDataBaseName } from '../database';
import sqlService from '@/service/sql';

export const resetSenseTable = () => {
  intelliSenseTable.dispose();
};

/** 当前库下的表 */
let intelliSenseTable = monaco.languages.registerCompletionItemProvider('sql', {
  provideCompletionItems: () => {
    return { suggestions: [] };
  },
});

let tableCache: Record<string, Array<{ name: string; comment: string }>> = {};

const checkTableContext = (text) => {
  const normalizedText = text.trim().toUpperCase();
  const tableKeywords = ['FROM', 'JOIN', 'INNER JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'UPDATE'];

  for (const keyword of tableKeywords) {
    if (normalizedText.endsWith(keyword)) {
      return true;
    }
  }

  return false;
};

const handleInsertText = (keyword: string, tableName: string, databaseCode: DatabaseTypeCode) => {
  if (/^[\"\`\[]/.test(keyword)) {
    return tableName;
  }

  return compatibleDataBaseName(tableName, databaseCode);
};

const createCacheKey = (dataSourceId?: number, databaseName?: string | null, schemaName?: string | null) => {
  return [dataSourceId, databaseName || '', schemaName || ''].join('::');
};

const extractExplicitDatabaseName = (text: string) => {
  const match = text.match(/([`"]?[A-Za-z0-9_$]+[`"]?)\.\s*$/);
  if (!match?.[1]) {
    return null;
  }

  return match[1].replace(/[`"]/g, '');
};

const getTableListByDatabase = async (
  dataSourceId?: number,
  targetDatabaseName?: string | null,
  schemaName?: string | null,
) => {
  if (!dataSourceId || !targetDatabaseName) {
    return [];
  }

  const cacheKey = createCacheKey(dataSourceId, targetDatabaseName, schemaName);
  if (!tableCache[cacheKey]) {
    tableCache[cacheKey] = await sqlService.getAllTableList({
      dataSourceId,
      databaseName: targetDatabaseName,
      schemaName,
    });
  }

  return tableCache[cacheKey] || [];
};

const registerIntelliSenseTable = (
  tableList: Array<{ name: string; comment: string }>,
  databaseCode: DatabaseTypeCode,
  dataSourceId?: number,
  databaseName?: string | null,
  schemaName?: string | null,
) => {
  monaco.editor.registerCommand('addFieldList', (_: any, ...args: any[]) => {
    addIntelliSenseField(args[0]);
    return;
  });

  resetSenseTable();
  tableCache = {};
  intelliSenseTable = monaco.languages.registerCompletionItemProvider('sql', {
    triggerCharacters: [' ', '.'],
    provideCompletionItems: async (model, position) => {
      const lineContentUntilPosition = model.getValueInRange({
        startLineNumber: position.lineNumber,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      });

      const isTableContext = checkTableContext(lineContentUntilPosition);
      // 获取触发提示的字符
      const match = lineContentUntilPosition.match(/\S+$/);
      const word = match ? match[0] : '';
      const explicitDatabaseName =
        databaseCode === DatabaseTypeCode.MYSQL ? extractExplicitDatabaseName(lineContentUntilPosition) : null;

      const explicitDatabaseTables =
        explicitDatabaseName && explicitDatabaseName !== databaseName
          ? await getTableListByDatabase(dataSourceId, explicitDatabaseName, schemaName)
          : [];

      const mergedTableList = [
        ...explicitDatabaseTables.map(currentTable => ({
          ...currentTable,
          databaseName: explicitDatabaseName,
        })),
        ...(tableList || []).map(currentTable => ({
          ...currentTable,
          databaseName,
        })),
      ].reduce<Array<{ name: string; comment: string; databaseName?: string | null }>>((acc, currentTable) => {
        if (!acc.some(item => item.name === currentTable.name && item.databaseName === currentTable.databaseName)) {
          acc.push(currentTable);
        }
        return acc;
      }, []);

      return {
        suggestions: mergedTableList.map((tableName) => ({
          label: {
            label: tableName.name,
            detail: tableName.databaseName ? `(${tableName.databaseName})` : null,
            description: i18n('sqlEditor.text.tableName'),
          },
          kind: monaco.languages.CompletionItemKind.Folder,
          insertText: handleInsertText(word, tableName.name, databaseCode),
          // range: monaco.Range.fromPositions(position),
          // documentation: tableName.comment,
          sortText:
            explicitDatabaseName && tableName.databaseName === explicitDatabaseName
              ? '00'
              : isTableContext
                ? '01'
                : '08',
          command: {
            id: 'addFieldList',
            title: 'addFieldList',
            arguments: [
              {
                tableName: tableName.name,
                dataSourceId,
                databaseName: tableName.databaseName || databaseName,
                schemaName,
              },
            ],
          },
        })),
      };
    },
  });
};

export { intelliSenseTable, registerIntelliSenseTable };
