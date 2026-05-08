import React, {
  useEffect,
  useMemo,
  useRef,
  useState,
  useImperativeHandle,
  ForwardedRef,
  forwardRef,
  createContext,
} from 'react';
import { Button, Spin, Drawer, Modal, Tag, message, Input, Empty } from 'antd';
import ChatInput, { SyncModelType } from './components/ChatInput';
import MonacoEditor, { IEditorOptions, IExportRefFunction, IRangeType } from '../MonacoEditor';
import { v4 as uuidv4 } from 'uuid';
import { IAiConfig, IBoundInfo } from '@/typings';
import Popularize from '@/components/Popularize';
import OperationLine from './components/OperationLine';
import { AIType, IAiScopeEntity } from '@/typings/ai';
import i18n from '@/i18n';
import configService from '@/service/config';
import styles from './index.less';
import { IAiSchemaSource } from '@/utils/aiStream';
import { DatabaseTypeCode } from '@/constants';
import { validateSqlScript } from './utils/sqlScriptValidation';
import connectionService from '@/service/connection';

// ----- hooks -----
import { useSaveEditorData } from './hooks/useSaveEditorData';
import { useAiChatSession } from './hooks/useAiChatSession';
import { useChat2dbAiAuth } from './hooks/useChat2dbAiAuth';

// ----- store -----
import { useSettingStore, fetchRemainingUse } from '@/store/setting';
import { useConnectionStore } from '@/pages/main/store/connection';

// ----- function -----
import { handelCreateConsole } from '@/pages/main/workspace/functions/shortcutKeyCreateConsole';

enum IPromptType {
  NL_2_SQL = 'NL_2_SQL',
  SQL_EXPLAIN = 'SQL_EXPLAIN',
  SQL_OPTIMIZER = 'SQL_OPTIMIZER',
  SQL_2_SQL = 'SQL_2_SQL',
  ChatRobot = 'ChatRobot',
}

export type IAppendValue = {
  text: any;
  range?: IRangeType;
};

interface IProps {
  /** 调用来源 */
  source?: 'workspace';
  isActive: boolean;
  /** 添加或修改的内容 */
  appendValue?: IAppendValue;
  defaultValue?: string;
  /** 是否开启AI输入 */
  hasAiChat: boolean;
  /** 是否可以开启SQL转到自然语言的相关ai操作 */
  hasAi2Lang?: boolean;
  /** 是否有 */
  hasSaveBtn?: boolean;
  value?: string;
  boundInfo: IBoundInfo;
  setBoundInfo: (params: IBoundInfo) => void;
  editorOptions?: IEditorOptions;
  onExecuteSQL: (sql: string) => void;
}

export interface IConsoleRef {
  editorRef: IExportRefFunction | undefined;
}

interface IIntelligentEditorContext {
  isActive: boolean;
  tableNameList: string[];
  setTableNameList: (tables: string[]) => void;
  selectedTables: string[];
  setSelectedTables: (tables: string[]) => void;
}

export const IntelligentEditorContext = createContext<IIntelligentEditorContext>({} as any);

interface IImportRunFilePayload {
  fileName: string;
  script: string;
}

interface IDatabaseSearchItem {
  key: string;
  dataSourceId: number;
  dataSourceName: string;
  databaseType: DatabaseTypeCode;
  databaseName: string;
  projectName?: string;
  environmentName?: string;
  supportSchema: boolean;
  supportDatabase: boolean;
}

interface IPendingImportRun {
  script: string;
  targetKey: string;
}

function ConsoleEditor(props: IProps, ref: ForwardedRef<IConsoleRef>) {
  const {
    hasAiChat = true,
    boundInfo,
    setBoundInfo,
    appendValue,
    hasSaveBtn = true,
    source,
    defaultValue,
    isActive,
  } = props;
  const uid = useMemo(() => uuidv4(), []);
  const editorRef = useRef<IExportRefFunction>();
  const importInputRef = useRef<HTMLInputElement>(null);
  const importRunInputRef = useRef<HTMLInputElement>(null);
  const scriptValidationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [selectedTables, setSelectedTables] = useState<string[]>([]);
  const [selectedDatabaseScopes, setSelectedDatabaseScopes] = useState<IAiScopeEntity[]>([]);
  const [selectedTableScopes, setSelectedTableScopes] = useState<IAiScopeEntity[]>([]);
  const [tableNameList, setTableNameList] = useState<string[]>([]);
  const [syncTableModel, setSyncTableModel] = useState<number>(SyncModelType.AUTO);
  const [scriptValidationEnabled, setScriptValidationEnabled] = useState(false);
  const [importRunModalOpen, setImportRunModalOpen] = useState(false);
  const [importRunFile, setImportRunFile] = useState<IImportRunFilePayload | null>(null);
  const [databaseSearchKeyword, setDatabaseSearchKeyword] = useState('');
  const [databaseSearchLoading, setDatabaseSearchLoading] = useState(false);
  const [databaseSearchError, setDatabaseSearchError] = useState('');
  const [databaseSearchList, setDatabaseSearchList] = useState<IDatabaseSearchItem[]>([]);
  const [selectedImportRunTargetKey, setSelectedImportRunTargetKey] = useState<string>();
  const [importRunExecuting, setImportRunExecuting] = useState(false);
  const [pendingImportRun, setPendingImportRun] = useState<IPendingImportRun | null>(null);
  const { aiConfig, remainingUse } = useSettingStore((state) => {
    return {
      aiConfig: state.aiConfig,
      remainingUse: state.remainingUse,
    };
  });
  const connectionList = useConnectionStore((state) => state.connectionList);

  // ---------------- new-code ----------------
  const { saveConsole } = useSaveEditorData({
    editorRef,
    isActive,
    boundInfo: props.boundInfo,
    source,
    defaultValue,
  });
  // ---------------- new-code ----------------

  /**
   * 当前选择的AI类型是Chat2DBAI
   */
  const isChat2DBAI = useMemo(() => aiConfig?.aiSqlSource === AIType.CHAT2DBAI, [aiConfig?.aiSqlSource]);

  const {
    closePopularizeModal,
    isFetchingAuth,
    modalProps,
    popularizeModal,
    requestLoginQrCode,
    showKeyLimitedPrompt,
  } = useChat2dbAiAuth({
    aiConfig,
  });

  const {
    aiContent,
    cancelChat,
    closeDrawer,
    isAiDrawerLoading,
    isAiDrawerOpen,
    isLoading,
    isStream,
    sqlSchemaSources,
    sqlValidationWarning,
    startChat,
  } = useAiChatSession({
    uid,
    isChat2DBAI,
    onNeedLogin: () => {
      requestLoginQrCode(true);
    },
    onKeyLimited: () => {
      showKeyLimitedPrompt(remainingUse);
    },
    onUsageRefresh: (apiKey?: string) => {
      fetchRemainingUse(apiKey);
    },
  });

  React.useEffect(() => {
    handleSelectTableSyncModel();
  }, []);

  useEffect(() => {
    return () => {
      if (scriptValidationTimerRef.current) {
        clearTimeout(scriptValidationTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (appendValue) {
      editorRef?.current?.setValue(appendValue.text, appendValue.range);
    }
  }, [appendValue]);

  useEffect(() => {
    setSelectedDatabaseScopes([]);
    setSelectedTableScopes([]);
  }, [boundInfo?.dataSourceId]);

  useEffect(() => {
    if (!pendingImportRun) {
      return;
    }
    const currentTargetKey = `${boundInfo.dataSourceId}-${boundInfo.databaseName || ''}-${boundInfo.schemaName || ''}`;
    if (currentTargetKey !== pendingImportRun.targetKey) {
      return;
    }
    props.onExecuteSQL?.(pendingImportRun.script);
    setPendingImportRun(null);
    setImportRunExecuting(false);
  }, [boundInfo.dataSourceId, boundInfo.databaseName, boundInfo.schemaName, pendingImportRun, props.onExecuteSQL]);

  useEffect(() => {
    setSelectedTableScopes((prev) => prev.filter((item) => selectedTables.includes(item.name)));
  }, [selectedTables]);

  useImperativeHandle(
    ref,
    () => ({
      editorRef: editorRef?.current,
    }),
    [editorRef?.current],
  );

  const handleAIChatInEditor = async (content: string, promptType: IPromptType, ext?: string) => {
    const _aiConfig = await configService.getAiSystemConfig({});
    handleAiChat(content, promptType, _aiConfig, ext);
  };

  const handleAiChat = async (content: string, promptType: IPromptType, _aiConfig?: IAiConfig, ext?: string) => {
    const { apiKey } = _aiConfig || aiConfig || {};
    if (!apiKey && isChat2DBAI) {
      requestLoginQrCode(true);
      return;
    }

    const isNL2SQL = promptType === IPromptType.NL_2_SQL;
    const databaseScopeText = selectedDatabaseScopes.length
      ? `\n\n参考库范围: ${selectedDatabaseScopes.map((item) => item.name).join(', ')}`
      : '';
    startChat({
      apiKey,
      boundInfo,
      content: `${content}${databaseScopeText}`,
      ext,
      mode: isNL2SQL ? 'editor' : 'drawer',
      promptType,
      responseMode: isNL2SQL ? 'SQL_ONLY' : 'RICH_TEXT',
      selectedTables,
      syncTableModel,
      onEditorChunk: (sql: string) => {
        editorRef?.current?.setValue(sql);
      },
      onEditorComplete: () => {
        editorRef?.current?.setValue('\n');
      },
    });
  };

  const executeSQL = (sql?: string) => {
    const sqlContent = sql || editorRef?.current?.getCurrentSelectContent() || editorRef?.current?.getAllContent();

    if (!sqlContent) {
      return;
    }
    props.onExecuteSQL && props.onExecuteSQL(sqlContent);
  };

  const isMysqlWorkspaceConsole = source === 'workspace' && boundInfo?.databaseType === DatabaseTypeCode.MYSQL;

  const applySqlScriptValidation = (sql: string) => {
    const issues = validateSqlScript(sql);
    if (issues.length) {
      editorRef.current?.setValidationDecorations(issues);
    } else {
      editorRef.current?.clearValidationDecorations();
    }
    return issues;
  };

  const handleEditorChange = (value: string) => {
    if (!scriptValidationEnabled) {
      return;
    }
    if (scriptValidationTimerRef.current) {
      clearTimeout(scriptValidationTimerRef.current);
    }
    scriptValidationTimerRef.current = setTimeout(() => {
      applySqlScriptValidation(value);
    }, 250);
  };

  const handleImportScript = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }

    const script = await file.text();
    editorRef.current?.setValue(script, 'cover');
    setScriptValidationEnabled(true);
    setTimeout(() => {
      const issues = applySqlScriptValidation(script);
      if (issues.length) {
        message.warning(`Imported with ${issues.length} possible SQL issue(s).`);
      } else {
        message.success('SQL script imported.');
      }
    }, 0);
  };

  const loadDatabaseSearchList = async () => {
    const availableConnections = (connectionList || []).filter((item) => item.supportDatabase);
    if (!availableConnections.length) {
      setDatabaseSearchList([]);
      return;
    }

    setDatabaseSearchLoading(true);
    setDatabaseSearchError('');
    setSelectedImportRunTargetKey(undefined);

    try {
      const databaseResults = await Promise.all(
        availableConnections.map(async (connection) => {
          const databases = await connectionService.getDatabaseList({
            dataSourceId: connection.id,
            refresh: false,
          });
          return (databases || [])
            .filter((item) => item?.name)
            .map((item) => {
              const environmentName = connection.environment?.shortName || connection.environment?.name;
              return {
                key: `${connection.id}-${item.name}`,
                dataSourceId: connection.id,
                dataSourceName: connection.alias,
                databaseType: connection.type,
                databaseName: item.name,
                projectName: connection.projectName,
                environmentName,
                supportSchema: connection.supportSchema,
                supportDatabase: connection.supportDatabase,
              } satisfies IDatabaseSearchItem;
            });
        }),
      );
      setDatabaseSearchList(databaseResults.flat());
    } catch (_error) {
      setDatabaseSearchList([]);
      setDatabaseSearchError(i18n('common.tips.importRun.databaseLoadFailed'));
    } finally {
      setDatabaseSearchLoading(false);
    }
  };

  const handleImportRunFile = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }

    try {
      const script = await file.text();
      if (!script.trim()) {
        message.warning(i18n('common.tips.importRun.emptyFile'));
        return;
      }
      setImportRunFile({
        fileName: file.name,
        script,
      });
      setDatabaseSearchKeyword('');
      setImportRunModalOpen(true);
      loadDatabaseSearchList();
    } catch (_error) {
      message.error(i18n('common.tips.importRun.readFailed'));
    }
  };

  const closeImportRunModal = () => {
    if (importRunExecuting) {
      return;
    }
    setImportRunModalOpen(false);
    setImportRunFile(null);
    setDatabaseSearchKeyword('');
    setSelectedImportRunTargetKey(undefined);
    setDatabaseSearchError('');
  };

  const selectedImportRunTarget = useMemo(() => {
    return databaseSearchList.find((item) => item.key === selectedImportRunTargetKey);
  }, [databaseSearchList, selectedImportRunTargetKey]);

  const filteredDatabaseSearchList = useMemo(() => {
    const keyword = databaseSearchKeyword.trim().toLowerCase();
    const scoredList = databaseSearchList
      .filter((item) => {
        if (!keyword) {
          return true;
        }
        return [
          item.databaseName,
          item.projectName,
          item.environmentName,
          item.dataSourceName,
        ]
          .filter(Boolean)
          .some((text) => text!.toLowerCase().includes(keyword));
      })
      .map((item) => {
        const normalizedName = item.databaseName.toLowerCase();
        let score = 0;
        if (keyword) {
          if (normalizedName === keyword) {
            score = 300;
          } else if (normalizedName.startsWith(keyword)) {
            score = 200;
          } else if (normalizedName.includes(keyword)) {
            score = 100;
          }
        }
        return {
          item,
          score,
        };
      })
      .sort((left, right) => {
        if (right.score !== left.score) {
          return right.score - left.score;
        }
        const leftCurrentDataSource = left.item.dataSourceId === boundInfo.dataSourceId ? 0 : 1;
        const rightCurrentDataSource = right.item.dataSourceId === boundInfo.dataSourceId ? 0 : 1;
        if (leftCurrentDataSource !== rightCurrentDataSource) {
          return leftCurrentDataSource - rightCurrentDataSource;
        }
        return (
          (left.item.projectName || '').localeCompare(right.item.projectName || '') ||
          (left.item.environmentName || '').localeCompare(right.item.environmentName || '') ||
          left.item.dataSourceName.localeCompare(right.item.dataSourceName) ||
          left.item.databaseName.localeCompare(right.item.databaseName)
        );
      });

    return scoredList.map((item) => item.item);
  }, [boundInfo.dataSourceId, databaseSearchKeyword, databaseSearchList]);

  const confirmImportRun = () => {
    if (!importRunFile || !selectedImportRunTarget) {
      return;
    }

    setImportRunExecuting(true);
    editorRef.current?.setValue(importRunFile.script, 'cover');
    setScriptValidationEnabled(true);
    setTimeout(() => {
      applySqlScriptValidation(importRunFile.script);
    }, 0);

    setPendingImportRun({
      script: importRunFile.script,
      targetKey: `${selectedImportRunTarget.dataSourceId}-${selectedImportRunTarget.databaseName}-`,
    });
    setBoundInfo({
      ...boundInfo,
      dataSourceId: selectedImportRunTarget.dataSourceId,
      dataSourceName: selectedImportRunTarget.dataSourceName,
      databaseType: selectedImportRunTarget.databaseType,
      databaseName: selectedImportRunTarget.databaseName,
      schemaName: undefined,
      supportDatabase: selectedImportRunTarget.supportDatabase,
      supportSchema: selectedImportRunTarget.supportSchema,
    });
    setImportRunModalOpen(false);
    setImportRunFile(null);
    setDatabaseSearchKeyword('');
    setSelectedImportRunTargetKey(undefined);
    setDatabaseSearchError('');
  };

  const handleExportScript = () => {
    const script = editorRef.current?.getAllContent() || '';
    const blob = new Blob([script], { type: 'text/sql;charset=utf-8' });
    const link = document.createElement('a');
    const objectUrl = URL.createObjectURL(blob);
    const dataSourceName = boundInfo?.dataSourceName || 'query';
    const databaseName = boundInfo?.databaseName || 'database';
    link.href = objectUrl;
    link.download = `${dataSourceName}-${databaseName}-${Date.now()}.sql`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(objectUrl);
  };

  const addAction = [
    {
      id: 'explainSQL',
      label: i18n('common.text.explainSQL'),
      action: (selectedText: string) => handleAIChatInEditor(selectedText, IPromptType.SQL_EXPLAIN),
    },
    {
      id: 'optimizeSQL',
      label: i18n('common.text.optimizeSQL'),
      action: (selectedText: string) => handleAIChatInEditor(selectedText, IPromptType.SQL_OPTIMIZER),
    },
    {
      id: 'changeSQL',
      label: i18n('common.text.conversionSQL'),
      action: (selectedText: string, ext?: string) => {
        handleAIChatInEditor(selectedText, IPromptType.SQL_2_SQL, ext);
      },
    },
  ];

  const handleSelectTableSyncModel = () => {
    const syncModel = localStorage.getItem('syncTableModel');
    if (syncModel === String(SyncModelType.AUTO) || syncModel === String(SyncModelType.MANUAL)) {
      setSyncTableModel(Number(syncModel));
      return;
    }

    setSyncTableModel(SyncModelType.AUTO);
    localStorage.setItem('syncTableModel', String(SyncModelType.AUTO));
  };

  // 注册快捷键
  const registerShortcutKey = (editor, monaco) => {
    // 保存
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      const value = editor?.getValue();
      saveConsole(value || '');
    });

    // 执行
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyR, () => {
      const value = editorRef.current?.getCurrentSelectContent();
      executeSQL(value);
    });

    // 执行
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.KeyL, () => {
      handelCreateConsole();
    });
  };

  const fallbackScopeSources = useMemo<IAiSchemaSource[]>(() => {
    return selectedTables.map((tableName) => ({
      tableName,
      currentDataSource: true,
      sourceType: 'SELECTED',
      dataSourceAlias: boundInfo?.dataSourceName,
      databaseName: boundInfo?.databaseName,
      schemaName: boundInfo?.schemaName,
    }));
  }, [boundInfo?.dataSourceName, boundInfo?.databaseName, boundInfo?.schemaName, selectedTables]);

  const displayedLocalScopes = useMemo<IAiScopeEntity[]>(() => {
    const fallbackTableScopes = selectedTables
      .filter((tableName) => !selectedTableScopes.some((item) => item.name === tableName))
      .map((tableName) => ({
        entityType: 'table' as const,
        name: tableName,
        dataSourceId: boundInfo?.dataSourceId,
        dataSourceAlias: boundInfo?.dataSourceName,
        databaseName: boundInfo?.databaseName,
        schemaName: boundInfo?.schemaName,
        currentDataSource: true,
        sourceType: 'SELECTED',
      }));

    return [...selectedDatabaseScopes, ...selectedTableScopes, ...fallbackTableScopes].reduce<IAiScopeEntity[]>(
      (acc, current) => {
        const exists = acc.some((item) => {
          return (
            item.entityType === current.entityType &&
            item.name === current.name &&
            item.databaseName === current.databaseName &&
            item.schemaName === current.schemaName
          );
        });
        if (!exists) {
          acc.push(current);
        }
        return acc;
      },
      [],
    );
  }, [
    boundInfo?.dataSourceId,
    boundInfo?.dataSourceName,
    boundInfo?.databaseName,
    boundInfo?.schemaName,
    selectedDatabaseScopes,
    selectedTableScopes,
    selectedTables,
  ]);

  const displayedSchemaSources = sqlSchemaSources.length ? sqlSchemaSources : fallbackScopeSources;
  const hasSchemaScope = hasAiChat && displayedSchemaSources.length > 0;
  const hasEntityScope = hasAiChat && displayedLocalScopes.length > 0;
  const hasTopConsolePanel = hasSchemaScope || hasEntityScope || isMysqlWorkspaceConsole;

  const renderScriptActions = () => {
    if (!isMysqlWorkspaceConsole) {
      return null;
    }
    return (
      <div className={styles.scriptActionGroup}>
        <input
          ref={importInputRef}
          type="file"
          accept=".sql,.txt,text/plain,text/sql,application/sql"
          className={styles.scriptFileInput}
          onChange={handleImportScript}
        />
        <input
          ref={importRunInputRef}
          type="file"
          accept=".sql,.txt,text/plain,text/sql,application/sql"
          className={styles.scriptFileInput}
          onChange={handleImportRunFile}
        />
        <Button size="small" className={styles.scriptActionButton} onClick={() => importRunInputRef.current?.click()}>
          import and run
        </Button>
        <Button size="small" className={styles.scriptActionButton} onClick={() => importInputRef.current?.click()}>
          import
        </Button>
        <Button size="small" className={styles.scriptActionButton} onClick={handleExportScript}>
          export
        </Button>
      </div>
    );
  };

  const renderSchemaSources = () => {
    if (!hasTopConsolePanel) {
      return null;
    }

    return (
      <div className={styles.schemaScopeBlock}>
        <div className={styles.schemaScopeHeader}>
          <div className={styles.schemaScopeHeaderInfo}>
            <span className={styles.schemaScopeTitle}>{i18n('chat.scope.sources')}</span>
            {sqlValidationWarning && <span className={styles.schemaScopeWarning}>{sqlValidationWarning}</span>}
          </div>
          {renderScriptActions()}
        </div>
        {!sqlSchemaSources.length && hasEntityScope && (
          <div className={styles.schemaScopeTags}>
            {displayedLocalScopes.map((scopeEntity, index) => {
              const scopeType = i18n('chat.scope.current');
              const entityTypeLabel =
                scopeEntity.entityType === 'database'
                  ? i18n('chat.input.suggest.database')
                  : i18n('chat.input.suggest.table');
              const dataSourceAlias = scopeEntity.dataSourceAlias ? `${scopeEntity.dataSourceAlias} / ` : '';
              const databaseName =
                scopeEntity.entityType === 'table' && scopeEntity.databaseName ? `${scopeEntity.databaseName}.` : '';
              const label =
                scopeEntity.entityType === 'database'
                  ? `${dataSourceAlias}${scopeEntity.name}`
                  : `${dataSourceAlias}${databaseName}${scopeEntity.name}`;
              return (
                <Tag
                  key={`${scopeEntity.entityType}-${scopeEntity.name}-${scopeEntity.databaseName || 'local'}-${index}`}
                  color="blue"
                  className={styles.schemaScopeTag}
                >
                  {scopeType} {entityTypeLabel}: {label}
                </Tag>
              );
            })}
          </div>
        )}
        {sqlSchemaSources.length > 0 && hasSchemaScope && (
          <div className={styles.schemaScopeTags}>
            {displayedSchemaSources.map((schemaSource, index) => {
              const scopeType = schemaSource.currentDataSource
                ? i18n('chat.scope.current')
                : i18n('chat.scope.projectHint');
              const dataSourceAlias = schemaSource.dataSourceAlias ? `${schemaSource.dataSourceAlias} / ` : '';
              const databaseName = schemaSource.databaseName ? `${schemaSource.databaseName}.` : '';
              const tableLabel = `${dataSourceAlias}${databaseName}${schemaSource.tableName}`;
              return (
                <Tag
                  key={`${schemaSource.tableName}-${schemaSource.dataSourceId || 'local'}-${index}`}
                  color={schemaSource.currentDataSource ? 'blue' : 'default'}
                  className={styles.schemaScopeTag}
                >
                  {scopeType}: {tableLabel}
                </Tag>
              );
            })}
          </div>
        )}
      </div>
    );
  };

  return (
    <IntelligentEditorContext.Provider
      value={{
        isActive,
        tableNameList,
        setTableNameList,
        selectedTables,
        setSelectedTables,
      }}
    >
      <div className={styles.console} ref={ref as any}>
        <Spin spinning={isLoading || isFetchingAuth} style={{ height: '100%' }}>
          {hasAiChat && (
            <ChatInput
              isStream={isStream}
              disabled={isLoading}
              aiType={aiConfig?.aiSqlSource}
              scopeHint={i18n('chat.input.scopeHint', boundInfo.databaseType || 'SQL')}
              tables={tableNameList}
              boundInfo={{
                dataSourceId: boundInfo.dataSourceId,
                dataSourceName: boundInfo.dataSourceName,
                databaseName: boundInfo.databaseName,
                schemaName: boundInfo.schemaName,
              }}
              onPressEnter={(value: string) => {
                handleAiChat(value, IPromptType.NL_2_SQL);
              }}
              selectedTables={selectedTables}
              onSelectTables={(tables: string[]) => {
                setSelectedTables(tables);
              }}
              onSelectScopeEntity={(entity) => {
                if (entity.entityType === 'database') {
                  setSelectedDatabaseScopes((prev) => {
                    const exists = prev.some((item) => item.name === entity.name && item.entityType === 'database');
                    if (exists) {
                      return prev;
                    }
                    return [...prev, entity];
                  });
                  return;
                }
                setSelectedTables((prev) => {
                  if (prev.includes(entity.name)) {
                    return prev;
                  }
                  return [...prev, entity.name];
                });
                setSelectedTableScopes((prev) => {
                  const exists = prev.some((item) => {
                    return (
                      item.name === entity.name &&
                      item.databaseName === entity.databaseName &&
                      item.schemaName === entity.schemaName
                    );
                  });
                  if (exists) {
                    return prev;
                  }
                  return [...prev, entity];
                });
              }}
              syncTableModel={syncTableModel}
              onSelectTableSyncModel={(model: number) => {
                setSyncTableModel(model);
                localStorage.setItem('syncTableModel', String(model));
              }}
              onCancelStream={() => {
                cancelChat();
              }}
            />
          )}
          {renderSchemaSources()}
          <MonacoEditor
            id={uid}
            defaultValue={defaultValue}
            ref={editorRef as any}
            className={
              hasAiChat
                ? hasSchemaScope || hasEntityScope
                  ? styles.consoleEditorWithChatAndScope
                  : hasTopConsolePanel
                    ? styles.consoleEditorWithChatAndToolbar
                    : styles.consoleEditorWithChat
                : hasTopConsolePanel
                  ? styles.consoleEditorWithToolbar
                  : styles.consoleEditor
            }
            addAction={addAction}
            options={props.editorOptions}
            shortcutKey={registerShortcutKey}
            onChange={handleEditorChange}
          />
          <Drawer
            open={isAiDrawerOpen}
            getContainer={false}
            mask={false}
            onClose={() => {
              closeDrawer();
            }}
          >
            <Spin spinning={isAiDrawerLoading} style={{ height: '100%' }}>
              <div className={styles.aiBlock}>{aiContent}</div>
            </Spin>
          </Drawer>
        </Spin>
        <OperationLine
          boundInfo={boundInfo}
          saveConsole={saveConsole}
          executeSQL={executeSQL}
          editorRef={editorRef}
          setBoundInfo={setBoundInfo}
          hasSaveBtn={hasSaveBtn}
        />
        <Modal
          open={popularizeModal}
          footer={false}
          onCancel={() => {
            closePopularizeModal();
          }}
        >
          <Popularize {...modalProps} />
        </Modal>
        <Modal
          title={i18n('common.title.importRun')}
          open={importRunModalOpen}
          onCancel={closeImportRunModal}
          onOk={confirmImportRun}
          okText={i18n('common.button.execute')}
          cancelText={i18n('common.button.cancel')}
          confirmLoading={importRunExecuting}
          okButtonProps={{
            disabled:
              !importRunFile || !selectedImportRunTarget || databaseSearchLoading || importRunExecuting,
          }}
        >
          <div className={styles.importRunModal}>
            <div className={styles.importRunSummary}>
              <span className={styles.importRunLabel}>{i18n('common.label.file')}</span>
              <span className={styles.importRunValue}>{importRunFile?.fileName}</span>
            </div>
            <Input
              allowClear
              value={databaseSearchKeyword}
              onChange={(event) => setDatabaseSearchKeyword(event.target.value)}
              placeholder={i18n('common.placeholder.searchDatabase')}
            />
            {databaseSearchError ? <div className={styles.importRunError}>{databaseSearchError}</div> : null}
            <div className={styles.importRunList}>
              {databaseSearchLoading ? (
                <div className={styles.importRunState}>
                  <Spin />
                </div>
              ) : !filteredDatabaseSearchList.length ? (
                <div className={styles.importRunState}>
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={i18n('common.text.noData')} />
                </div>
              ) : (
                filteredDatabaseSearchList.map((item) => {
                  const selected = item.key === selectedImportRunTargetKey;
                  return (
                    <button
                      key={item.key}
                      type="button"
                      className={`${styles.importRunItem} ${selected ? styles.importRunItemActive : ''}`}
                      onClick={() => setSelectedImportRunTargetKey(item.key)}
                    >
                      <div className={styles.importRunItemMain}>
                        <div className={styles.importRunItemName}>{item.databaseName}</div>
                        <div className={styles.importRunItemMeta}>
                          <span>{item.dataSourceName}</span>
                          {item.projectName ? <span>{item.projectName}</span> : null}
                          {item.environmentName ? <span>{item.environmentName}</span> : null}
                        </div>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
            {selectedImportRunTarget ? (
              <div className={styles.importRunTarget}>
                <div className={styles.importRunLabel}>{i18n('common.label.targetDatabase')}</div>
                <div className={styles.importRunTargetValue}>
                  {[
                    selectedImportRunTarget.dataSourceName,
                    selectedImportRunTarget.databaseName,
                  ].join(' / ')}
                </div>
              </div>
            ) : null}
          </div>
        </Modal>
      </div>
    </IntelligentEditorContext.Provider>
  );
}

export default forwardRef(ConsoleEditor);
