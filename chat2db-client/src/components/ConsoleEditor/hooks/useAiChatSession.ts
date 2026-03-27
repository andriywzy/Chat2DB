import { useEffect, useRef, useState } from 'react';
import { formatParams } from '@/utils/url';
import connectToEventSource from '@/utils/eventSource';
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

    const params = formatParams({
      message: content,
      promptType,
      dataSourceId: boundInfo.dataSourceId,
      databaseName: boundInfo.databaseName,
      schemaName: boundInfo.schemaName,
      tableNames: syncTableModel ? selectedTables : null,
      ext,
    });

    const handleMessage = (_message: string) => {
      setIsLoading(false);
      setIsAiDrawerLoading(false);
      try {
        const isEOF = _message === '[DONE]';
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

        const nextContent = JSON.parse(_message).content;
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
      uid,
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
    startChat,
  };
};
