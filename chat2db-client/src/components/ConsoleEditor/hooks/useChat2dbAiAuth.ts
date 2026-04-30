import React, { useEffect, useRef, useState } from 'react';
import aiServer from '@/service/ai';
import { IAiConfig } from '@/typings';
import { IRemainingUse } from '@/typings/ai';
import { fetchRemainingUse, setAiConfig } from '@/store/setting';

interface IProps {
  aiConfig?: IAiConfig;
}

export const useChat2dbAiAuth = ({ aiConfig }: IProps) => {
  const aiFetchIntervalRef = useRef<any>();
  const [popularizeModal, setPopularizeModal] = useState(false);
  const [modalProps, setModalProps] = useState<Record<string, any>>({});
  const [isFetchingAuth, setIsFetchingAuth] = useState(false);

  useEffect(() => {
    return () => {
      if (aiFetchIntervalRef.current) {
        clearInterval(aiFetchIntervalRef.current);
      }
    };
  }, []);

  const closePopularizeModal = () => {
    if (aiFetchIntervalRef.current) {
      clearInterval(aiFetchIntervalRef.current);
    }
    setPopularizeModal(false);
  };

  const requestLoginQrCode = async (shouldPoll?: boolean) => {
    setIsFetchingAuth(true);
    try {
      const { wechatQrCodeUrl, token, tip } = await aiServer.getLoginQrCode({});
      setPopularizeModal(true);
      setModalProps({
        imageUrl: wechatQrCodeUrl,
        token,
        tip,
      });
      if (shouldPoll) {
        let pollCnt = 0;
        aiFetchIntervalRef.current = setInterval(async () => {
          const { apiKey } = (await aiServer.getLoginStatus({ token })) || {};
          pollCnt++;
          if (apiKey || pollCnt >= 60) {
            clearInterval(aiFetchIntervalRef.current);
          }
          if (apiKey) {
            setPopularizeModal(false);
            setAiConfig({
              ...(aiConfig || {}),
              apiKey,
            });
            fetchRemainingUse(apiKey);
          }
        }, 3000);
      }
    } finally {
      setIsFetchingAuth(false);
    }
  };

  const showKeyLimitedPrompt = (remainingUse?: IRemainingUse) => {
    setModalProps({
      imageUrl:
        'http://oss.sqlgpt.cn/static/chat2db-wechat.jpg?x-oss-process=image/auto-orient,1/resize,m_lfit,w_256/quality,Q_80/format,webp',
      tip: React.createElement(
        React.Fragment,
        null,
        remainingUse?.remainingUses === 0 ? React.createElement('p', null, 'Key次数用完或者过期') : null,
        React.createElement('p', null, '微信扫描二维码并关注公众号获得 AI 使用机会。'),
      ),
    });
    setPopularizeModal(true);
  };

  return {
    popularizeModal,
    modalProps,
    isFetchingAuth,
    closePopularizeModal,
    requestLoginQrCode,
    showKeyLimitedPrompt,
  };
};
