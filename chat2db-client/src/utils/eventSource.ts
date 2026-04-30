import { EventSourcePolyfill } from 'event-source-polyfill';

const connectToEventSource = (params: {
  url: string;
  uid?: string;
  onOpen: () => void;
  onMessage: (message: string) => void;
  onError: (error: unknown) => void;
}) => {
  const { url, uid, onOpen, onMessage, onError } = params;

  if (!url || !onMessage || !onError) {
    throw new Error('url, onMessage, and onError are required');
  }

  const DBHUB = localStorage.getItem('DBHUB');
  const p = {
    headers: {
      DBHUB,
      ...(uid ? { uid } : {}),
    },
  };
  const eventSource = new EventSourcePolyfill(`${window._BaseURL}${url}`, p);

  eventSource.onopen = () => {
    onOpen();
  };

  eventSource.onmessage = (event) => {
    onMessage(event.data);
  };

  eventSource.onerror = (error) => {
    onError(error);
    console.error('EventSourcePolyfill error:', error);
  };

  // 返回一个关闭 eventSource 的函数，以便在需要时调用它
  return () => {
    eventSource.close();
  };
};

export default connectToEventSource;
