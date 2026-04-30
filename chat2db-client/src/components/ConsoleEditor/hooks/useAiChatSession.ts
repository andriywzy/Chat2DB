import { useEffect, useRef, useState } from 'react';
import { message } from 'antd';
import { formatParams } from '@/utils/url';
import connectToEventSource from '@/utils/eventSource';
import { IAiSchemaSource, isAiStreamDone, parseAiStreamMessage } from '@/utils/aiStream';
import { IBoundInfo } from '@/typings';
import { chatErrorForKey, chatErrorToLogin } from '@/constants/chat';

type IAiSessionMode = 'editor' | 'drawer';

interface IStartAiChatOptions {
  apiKey?: string;
  boundInfo: Pick<IBoundInfo, 'dataSourceId' | 'databaseName' | 'schemaName'>;
  content: string;
  ext?: string;
  mode: IAiSessionMode;
  promptType: string;
  responseMode: 'SQL_ONLY' | 'RICH_TEXT';
  selectedTables: string[];
  syncTableModel: number;
  onEditorChunk?: (sql: string) => void;
  onEditorComplete?: () => void;
}

interface IProps {
  isChat2DBAI: boolean;
  onNeedLogin: () => void;
  onKeyLimited: () => void;
  onUsageRefresh: (apiKey?: string) => void;
  uid: string;
}

export const useAiChatSession = ({ uid, isChat2DBAI, onNeedLogin, onKeyLimited, onUsageRefresh }: IProps) => {
  const chatResult = useRef('');
  const closeEventSource = useRef<any>();
  const [isLoading, setIsLoading] = useState(false);
  const [isAiDrawerOpen, setIsAiDrawerOpen] = useState(false);
  const [isAiDrawerLoading, setIsAiDrawerLoading] = useState(false);
  const [isStream, setIsStream] = useState(false);
  const [aiContent, setAiContent] = useState('');
  const [sqlSchemaSources, setSqlSchemaSources] = useState<IAiSchemaSource[]>([]);
  const [sqlValidationWarning, setSqlValidationWarning] = useState('');

  const cancelChat = () => {
    closeEventSource.current?.();
    setIsStream(false);
    setIsLoading(false);
    setIsAiDrawerLoading(false);
  };

  const closeDrawer = () => {
    cancelChat();
    setIsAiDrawerOpen(false);
    chatResult.current = '';
  };

  useEffect(() => {
    return () => {
      closeEventSource.current?.();
    };
  }, []);

  const startChat = ({
    apiKey,
    boundInfo,
    content,
    ext,
    mode,
      promptType,
      responseMode,
      selectedTables,
      syncTableModel,
      onEditorChunk,
      onEditorComplete,
  }: IStartAiChatOptions) => {
    closeEventSource.current?.();

    if (mode === 'editor') {
      setIsLoading(true);
    } else {
      setAiContent('');
      chatResult.current = '';
      setIsAiDrawerOpen(true);
      setIsAiDrawerLoading(true);
    }
    setSqlSchemaSources([]);
    setSqlValidationWarning('');

    const isNl2Sql = promptType === 'NL_2_SQL';
    const useManualTables = syncTableModel === 1 && selectedTables.length > 0;
    const params = formatParams({
      uid,
      message: content,
      promptType,
      dataSourceId: boundInfo.dataSourceId,
      databaseName: boundInfo.databaseName,
      schemaName: boundInfo.schemaName,
      tableNames: useManualTables ? selectedTables : null,
      refresh: isNl2Sql ? true : null,
      ext,
      responseMode,
    });

    const handleMessage = (_message: string) => {
      setIsLoading(false);
      setIsAiDrawerLoading(false);
      try {
        const isEOF = isAiStreamDone(_message);
        if (isEOF) {
          closeEventSource.current?.();
          setIsStream(false);
          if (isChat2DBAI) {
            onUsageRefresh(apiKey);
          }
          if (mode === 'editor') {
            onEditorComplete?.();
          } else {
            chatResult.current += '\n';
            setAiContent(chatResult.current);
            chatResult.current = '';
          }
          return;
        }

        const hasErrorToLogin = chatErrorToLogin.some((err) => _message.includes(err));
        const hasKeyLimitedOrExpired = chatErrorForKey.some((err) => _message.includes(err));

        if (hasKeyLimitedOrExpired) {
          closeEventSource.current?.();
          setIsLoading(false);
          setIsStream(false);
          onKeyLimited();
          return;
        }

        if (hasErrorToLogin) {
          closeEventSource.current?.();
          setIsLoading(false);
          setIsStream(false);
          onNeedLogin();
          onUsageRefresh(apiKey);
          return;
        }

        const payload = parseAiStreamMessage(_message);
        if (payload.schemaSources?.length) {
          setSqlSchemaSources(payload.schemaSources);
        }
        if (payload.validationWarning) {
          setSqlValidationWarning(payload.validationWarning);
          if (mode === 'editor') {
            message.warning(payload.validationWarning);
          }
        }
        if (payload.errorMessage) {
          closeEventSource.current?.();
          setIsStream(false);
          if (mode === 'drawer') {
            setAiContent(payload.errorMessage);
            setIsAiDrawerOpen(true);
          } else {
            message.error(payload.errorMessage);
          }
          return;
        }

        const nextContent = payload.content || '';
        if (!nextContent) {
          return;
        }
        if (mode === 'editor') {
          onEditorChunk?.(nextContent);
        } else {
          chatResult.current += nextContent;
          setAiContent(chatResult.current);
        }
      } catch (error) {
        cancelChat();
      }
    };

    const handleError = (error: any) => {
      console.error('Error:', error);
      cancelChat();
    };

    closeEventSource.current = connectToEventSource({
      url: `/api/ai/chat?${params}`,
      onOpen: () => {
        setIsStream(true);
      },
      onMessage: handleMessage,
      onError: handleError,
    });
  };

  return {
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
  };
};
