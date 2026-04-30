import React, { memo, useMemo } from 'react';
import { Dropdown, Tooltip } from 'antd';
import { AimOutlined, MoreOutlined } from '@ant-design/icons';
import classnames from 'classnames';
import styles from './index.less';
import i18n from '@/i18n';

// ---- store ----
import { useConnectionStore } from '@/pages/main/store/connection';
import { setCurrentConnectionDetails } from '@/pages/main/workspace/store/common';
import { setMainPageActiveTab } from '@/pages/main/store/main';

// ----- components -----
import Iconfont from '@/components/Iconfont';

// ----- constants/typings -----
import { databaseMap } from '@/constants';

import { IConnectionListItem } from '@/typings/connection';

export default memo(() => {
  const { connectionList } = useConnectionStore((state) => {
    return {
      connectionList: state.connectionList,
    };
  });

  const renderConnectionLabel = (item: IConnectionListItem) => {
    return (
      <div className={classnames(styles.menuLabel)}>
        {/* <Tag className={styles.menuLabelTag} color={item.environment.color.toLocaleLowerCase()}>
          {item.environment.shortName}
        </Tag> */}
        <span className={styles.envTag} style={{ background: item.environment.color.toLocaleLowerCase() }} />
        <div className={styles.menuLabelIconBox}>
          <Iconfont className={classnames(styles.menuLabelIcon)} code={databaseMap[item.type]?.icon} />
        </div>
        <div className={styles.menuLabelTitle}>{item.alias}</div>
      </div>
    );
  };

  const connectionItems = useMemo(() => {
    return (
      connectionList?.map((item) => {
        return {
          key: item.id,
          label: renderConnectionLabel(item),
          onClick: () => {
            setCurrentConnectionDetails(item);
          },
        };
      }) || []
    );
  }, [connectionList]);

  return (
    <div className={styles.header}>
      <div className={styles.title}>{i18n('workspace.database.title')}</div>
      <div className={styles.actions}>
        <Tooltip title={i18n('connection.title.connections')} mouseEnterDelay={0.5}>
          <div className={styles.iconButton} onClick={() => setMainPageActiveTab('connections')}>
            <AimOutlined />
          </div>
        </Tooltip>
        <div className={styles.iconButton}>
          <Dropdown menu={{ items: connectionItems }} trigger={['click']} overlayClassName={styles.dropdownOverlay}>
            <MoreOutlined />
          </Dropdown>
        </div>
      </div>
    </div>
  );
});
