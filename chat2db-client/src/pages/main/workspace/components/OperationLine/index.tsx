import React, { memo, useMemo, useState } from 'react';
import i18n from '@/i18n';
import styles from './index.less';
import { Dropdown, Input, type MenuProps } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';

// ----- components -----
import Iconfont from '@/components/Iconfont';
import FileUploadModal from '@/components/ImportConnection';

// ----- store -----
import { setMainPageActiveTab } from '@/pages/main/store/main';

interface IProps {
  searchValue: string;
  setSearchValue: (value: string) => void;
  getTreeData: (refresh?: boolean) => void;
  onCreateProject?: () => void;
}

const OperationLine = (props: IProps) => {
  const { searchValue, setSearchValue, getTreeData, onCreateProject } = props;
  const [isImportOpen, setIsImportOpen] = useState(false);

  const menuItems = useMemo<MenuProps['items']>(
    () => [
      {
        key: 'new-project',
        label: (
          <div className={styles.menuItemLabel}>
            <Iconfont code="&#xe63f;" />
            <span>{i18n('workspace.database.newProject')}</span>
          </div>
        ),
        onClick: () => {
          onCreateProject?.();
        },
      },
      {
        key: 'new-connection',
        label: (
          <div className={styles.menuItemLabel}>
            <Iconfont code="&#xe638;" />
            <span>{i18n('workspace.database.newConnection')}</span>
            <Iconfont className={styles.menuArrow} code="&#xe631;" />
          </div>
        ),
        onClick: () => {
          setMainPageActiveTab('connections');
        },
      },
      {
        key: 'import-connection',
        label: (
          <div className={styles.menuItemLabel}>
            <Iconfont code="&#xe66c;" />
            <span>{i18n('workspace.database.importConnection')}</span>
            <Iconfont className={styles.menuArrow} code="&#xe631;" />
          </div>
        ),
        onClick: () => {
          setIsImportOpen(true);
        },
      },
    ],
    [onCreateProject],
  );

  return (
    <>
      <div className={styles.operationLine}>
        <div className={styles.operationLineLeft}>
          <Dropdown
            menu={{ items: menuItems }}
            overlayClassName={styles.createMenuOverlay}
            trigger={['click']}
            placement="bottomLeft"
          >
            <div className={styles.iconButton}>
              <Iconfont code="&#xeb78;" />
            </div>
          </Dropdown>
          <div
            className={styles.iconButton}
            onClick={() => {
              getTreeData(true);
            }}
          >
            <Iconfont code="&#xe668;" />
          </div>
          <div className={styles.iconButton}>
            <ThunderboltOutlined />
          </div>
        </div>
        <div className={styles.searchBox}>
          <Input
            size="small"
            prefix={<Iconfont code="&#xe888;" />}
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            allowClear
            placeholder={i18n('common.text.search')}
            suffix={<span className={styles.searchShortcut}>⌘F</span>}
          />
        </div>
      </div>
      <FileUploadModal
        open={isImportOpen}
        onClose={() => {
          setIsImportOpen(false);
        }}
        onConfirm={() => {
          setIsImportOpen(false);
          getTreeData(true);
        }}
      />
    </>
  );
};

export default memo(OperationLine);
