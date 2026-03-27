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
import { Spin, Drawer, Modal } from 'antd';
import ChatInput, { SyncModelType } from './components/ChatInput';
import MonacoEditor, { IEditorOptions, IExportRefFunction, IRangeType } from '../MonacoEditor';
import { v4 as uuidv4 } from 'uuid';
import { IAiConfig, IBoundInfo } from '@/typings';
import Popularize from '@/components/Popularize';
import OperationLine from './components/OperationLine';
import { AIType } from '@/typings/ai';
import i18n from '@/i18n';
import configService from '@/service/config';
import styles from './index.less';

// ----- hooks -----
import { useSaveEditorData } from './hooks/useSaveEditorData';
import { useAiChatSession } from './hooks/useAiChatSession';
import { useChat2dbAiAuth } from './hooks/useChat2dbAiAuth';

// ----- store -----
import { useSettingStore, fetchRemainingUse } from '@/store/setting';

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
  const [selectedTables, setSelectedTables] = useState<string[]>([]);
  const [tableNameList, setTableNameList] = useState<string[]>([]);
  const [syncTableModel, setSyncTableModel] = useState<number>(0);
  const { aiConfig, hasWhite, remainingUse } = useSettingStore((state) => {
    return {
      aiConfig: state.aiConfig,
      hasWhite: state.hasWhite,
      remainingUse: state.remainingUse,
    };
  });

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

  const { aiContent, cancelChat, closeDrawer, isAiDrawerLoading, isAiDrawerOpen, isLoading, isStream, startChat } =
    useAiChatSession({
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
  }, [hasWhite]);

  useEffect(() => {
    if (appendValue) {
      editorRef?.current?.setValue(appendValue.text, appendValue.range);
    }
  }, [appendValue]);

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
    startChat({
      apiKey,
      boundInfo,
      content,
      ext,
      mode: isNL2SQL ? 'editor' : 'drawer',
      promptType,
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
    const hasAiAccess = hasWhite;
    if (syncModel !== null) {
      setSyncTableModel(Number(syncModel));
      return;
    }

    setSyncTableModel(hasAiAccess ? SyncModelType.AUTO : SyncModelType.MANUAL);
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
              tables={tableNameList}
              onPressEnter={(value: string) => {
                handleAiChat(value, IPromptType.NL_2_SQL);
              }}
              selectedTables={selectedTables}
              onSelectTables={(tables: string[]) => {
                setSelectedTables(tables);
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
          <MonacoEditor
            id={uid}
            defaultValue={defaultValue}
            ref={editorRef as any}
            className={hasAiChat ? styles.consoleEditorWithChat : styles.consoleEditor}
            addAction={addAction}
            options={props.editorOptions}
            shortcutKey={registerShortcutKey}
            isActive={isActive}
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
      </div>
    </IntelligentEditorContext.Provider>
  );
}

export default forwardRef(ConsoleEditor);
