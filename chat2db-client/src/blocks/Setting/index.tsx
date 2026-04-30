import React, { ReactNode, useEffect, useState } from 'react';
import classnames from 'classnames';
import Iconfont from '@/components/Iconfont';
import { Modal, Tooltip } from 'antd';
import i18n from '@/i18n';
import BaseSetting from './BaseSetting';
import AISetting from './AiSetting';
import ProxySetting from './ProxySetting';
import About from './About';
import SsoSetting from './SsoSetting';
import styles from './index.less';
import { ILatestVersion } from '@/service/config';
import UpdateDetection, { IUpdateDetectionRef, UpdatedStatusEnum } from '@/blocks/Setting/UpdateDetection';
import { IRole } from '@/typings/user';

// ---- store -----
import { useSettingStore, getAiSystemConfig, setAiSystemConfig } from '@/store/setting';
import { useUserStore } from '@/store/user';

interface IProps {
  className?: string;
  render?: ReactNode;
  noLogin?: boolean; // 用于在没有登录的页面使用，不显示ai设置等需要登录的功能
  defaultArouse?: boolean; // 是否默认弹出
  defaultMenu?: number; // 默认选中的菜单
}
export interface IUpdateDetectionData extends ILatestVersion {
  updatedStatusEnum: UpdatedStatusEnum;
  needUpdate: boolean;
}

function Setting(props: IProps) {
  const { className, noLogin = false, defaultArouse, defaultMenu = 0 } = props;
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [currentMenu, setCurrentMenu] = useState<number>(defaultMenu);
  const [updateDetectionData, setUpdateDetectionData] = useState<IUpdateDetectionData | null>(null);
  const updateDetectionRef = React.useRef<IUpdateDetectionRef>(null);
  const aiConfig = useSettingStore((state) => state.aiConfig);
  const { userInfo } = useUserStore((state) => ({
    userInfo: state.curUser,
  }));

  useEffect(() => {
    if (defaultArouse) {
      showModal();
    }
  }, []);

  useEffect(() => {
    if (isModalVisible && !noLogin) {
      getAiSystemConfig();
    }
  }, [isModalVisible]);

  useEffect(() => {
    if (!noLogin) {
      getAiSystemConfig();
    }
  }, []);

  const showModal = (_currentMenu: number = 0) => {
    setCurrentMenu(_currentMenu);
    setIsModalVisible(true);
  };

  const handleOk = () => {
    setIsModalVisible(false);
  };

  const handleCancel = () => {
    setIsModalVisible(false);
  };

  function changeMenu(t: any) {
    setCurrentMenu(t);
  }

  const menusList = [
    {
      label: i18n('setting.nav.basic'),
      icon: '\ue795',
      body: <BaseSetting />,
      code: 'basic',
    },
    {
      label: i18n('setting.nav.customAi'),
      icon: '\ue646',
      body: <AISetting aiConfig={aiConfig} handleApplyAiConfig={setAiSystemConfig} />,
      code: 'ai',
      requiresLogin: true,
    },
    {
      label: i18n('setting.nav.sso'),
      icon: '\ue64b',
      body: <SsoSetting />,
      code: 'sso',
      requiresLogin: true,
      adminOnly: true,
    },
    {
      label: i18n('setting.nav.proxy'),
      icon: '\ue63f',
      body: <ProxySetting />,
      code: 'proxy',
    },
    {
      label: i18n('setting.nav.aboutUs'),
      icon: '\ue65c',
      rightSlot: updateDetectionData?.needUpdate && (
        <div className={classnames(styles.rightSlot, styles.rightSlotAbout)}>
          <Tooltip title={`发现新版本v${updateDetectionData?.version}`}>
            <Iconfont code="&#xe69c;" />
          </Tooltip>
        </div>
      ),
      body: <About updateDetectionRef={updateDetectionRef as any} updateDetectionData={updateDetectionData} />,
      code: 'about',
    },
  ];

  const visibleMenus = menusList.filter((menu) => {
    if (noLogin && (menu as any).requiresLogin) {
      return false;
    }
    if ((menu as any).adminOnly && userInfo?.roleCode === IRole.USER) {
      return false;
    }
    return true;
  });

  const safeCurrentMenu = currentMenu >= visibleMenus.length ? 0 : currentMenu;

  return (
    <>
      <Tooltip placement="right" title={i18n('setting.title.setting')}>
        <div
          className={classnames(className, styles.box)}
          onClick={() => {
            showModal();
          }}
        >
          {props.render ? props.render : <Iconfont className={styles.settingIcon} code="&#xe630;" />}
        </div>
      </Tooltip>

      <UpdateDetection
        setUpdateDetectionData={setUpdateDetectionData}
        updateDetectionData={updateDetectionData}
        openSettingModal={showModal}
        ref={updateDetectionRef}
      />
      <Modal
        open={isModalVisible}
        onOk={handleOk}
        onCancel={handleCancel}
        footer={false}
        width={800}
        maskClosable={false}
      >
        <div className={styles.modalBox}>
          <div className={styles.menus}>
            <div className={classnames(styles.menusTitle)}>{i18n('setting.title.setting')}</div>
            {visibleMenus.map((t, index) => {
              return (
                <div
                  key={index}
                  onClick={changeMenu.bind(null, index)}
                  className={classnames(styles.menuItem, {
                    [styles.activeMenu]: t.label === visibleMenus[safeCurrentMenu].label,
                  })}
                >
                  <Iconfont className={styles.prefixIcon} code={t.icon} />
                  {t.label}
                  {t.rightSlot}
                </div>
              );
            })}
          </div>
          <div className={styles.menuContent}>
            <div className={classnames(styles.menuContentTitle)}>{visibleMenus[safeCurrentMenu].label}</div>
            {visibleMenus[safeCurrentMenu].body}
          </div>
        </div>
      </Modal>
    </>
  );
}

export default Setting;
