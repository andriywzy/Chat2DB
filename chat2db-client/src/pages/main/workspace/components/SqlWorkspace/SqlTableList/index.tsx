import React, { memo, useEffect, useRef, useState } from 'react';
import classnames from 'classnames';
import { Dropdown, Input, Pagination, Table } from 'antd';
import { OperationColumn, TreeNodeType, WorkspaceTabType } from '@/constants';
import { v4 as uuid } from 'uuid';

import i18n from '@/i18n';
import sqlServer from '@/service/sql';
import { IPageParams } from '@/typings';
import Iconfont from '@/components/Iconfont';
import MenuLabel from '@/components/MenuLabel';
import { getRightClickMenu } from '@/blocks/Tree/hooks/useGetRightClickMenu';
import { addWorkspaceTab } from '@/pages/main/workspace/store/console';
import { setCurrentWorkspaceGlobalExtend } from '@/pages/main/workspace/store/common';
import { IViewAllTableProps } from '../../ViewAllTable/types';
import styles from '../../ViewAllTable/index.less';

const { Search } = Input;

const SqlTableList = memo(({ uniqueData, className }: IViewAllTableProps) => {
  const pageSize = 1000;

  const [tableData, setTableData] = useState<any[] | null>(null);
  const [tableLoading, setTableLoading] = useState<boolean>(false);
  const tableBoxRef = useRef<HTMLDivElement>(null);
  const [allTableWidth, setAllTableWidth] = useState(0);
  const [allTableHeight, setAllTableHeight] = useState(0);
  const [activeId, setActiveId] = useState<string>('');
  const [tableDataTotal, setTableDataTotal] = useState(0);
  const [currentPageNo, setCurrentPageNo] = useState(1);
  const [openDropdown, setOpenDropdown] = useState<boolean | undefined>(undefined);
  const [dropdownItems, setDropdownItems] = useState<any[]>([]);

  useEffect(() => {
    getTable({ pageNo: 1, pageSize });
  }, []);

  useEffect(() => {
    if (openDropdown === false) {
      setOpenDropdown(undefined);
    }
  }, [openDropdown]);

  const getTable = (params: IPageParams) => {
    setCurrentPageNo(params.pageNo);
    setTableLoading(true);

    sqlServer
      .getTableList({
        ...(params || {}),
        ...uniqueData,
      } as any)
      .then((res: any) => {
        setTableDataTotal(res.total || 0);
        const data = (res.data || []).map((t: any) => ({
          uuid: uuid(),
          name: t.name,
          treeNodeType: TreeNodeType.TABLE,
          key: t.name,
          pinned: t.pinned,
          comment: t.comment,
          extraParams: {
            ...uniqueData,
            tableName: t.name,
          },
        }));
        setTableData(data);
      })
      .finally(() => {
        setTableLoading(false);
      });
  };

  useEffect(() => {
    if (!tableBoxRef.current) {
      return;
    }

    const resizeObserver = new ResizeObserver((entries) => {
      const { width, height } = entries[0].contentRect;
      setAllTableWidth(width);
      setAllTableHeight(height);
    });

    resizeObserver.observe(tableBoxRef.current);
    return () => resizeObserver.disconnect();
  }, []);

  const createTable = () => {
    addWorkspaceTab({
      id: uuid(),
      title: i18n('editTable.button.createTable'),
      type: WorkspaceTabType.CreateTable,
      uniqueData: {
        ...uniqueData,
      },
    });
  };

  const getDropdownsItems = (record: any) => {
    const rightClickMenu = getRightClickMenu({
      treeNodeData: record,
      loadData: () => {},
    });

    const nextDropdownItems: any = rightClickMenu.map((item) => ({
      key: item.key,
      type: item.type,
      onClick: () => {
        setOpenDropdown(false);
        item.onClick(record);
      },
      label: <MenuLabel icon={item.labelProps.icon} label={item.labelProps.label} />,
    }));

    const excludeList = [
      OperationColumn.OpenTable,
      OperationColumn.CreateConsole,
      OperationColumn.ViewDDL,
      OperationColumn.EditTable,
      OperationColumn.CopyName,
    ];

    return nextDropdownItems.filter((item) => excludeList.includes(item.type));
  };

  const renderCell = (text: string, record: any) => (
    <div className={classnames(styles.tableCell, { [styles.activeTableCell]: activeId === record.key })}>{text}</div>
  );

  return (
    <div className={classnames(styles.allTable, className)}>
      <div className={styles.headerBox}>
        <div className={styles.headerBoxLeft}>
          <Iconfont code="&#xe726;" box boxSize={24} onClick={createTable} />
          <Iconfont
            onClick={() => getTable({ pageNo: 1, pageSize, refresh: true } as any)}
            code="&#xe668;"
            box
            boxSize={24}
          />
        </div>
        <Search
          size="small"
          placeholder={i18n('common.text.search')}
          onSearch={(value) => getTable({ pageNo: 1, pageSize, searchKey: value } as any)}
          style={{ width: 180 }}
        />
      </div>
      <div className={styles.contentCenter}>
        <Dropdown
          open={openDropdown}
          menu={{ items: dropdownItems }}
          trigger={['contextMenu']}
          onOpenChange={(_open) => setOpenDropdown(_open)}
        >
          <div ref={tableBoxRef} className={styles.tableBox}>
            <Table
              loading={tableLoading}
              onRow={(row) => ({
                onClick: () => {
                  setActiveId(row.key);
                  setCurrentWorkspaceGlobalExtend({
                    code: 'viewDDL',
                    uniqueData: {
                      ...uniqueData,
                      tableName: row.name,
                    },
                  });
                },
                onContextMenu: (event) => {
                  event.preventDefault();
                  setActiveId(row.key);
                  setOpenDropdown(true);
                  setDropdownItems(getDropdownsItems(tableData?.find((t) => t.key === row.key)));
                },
              })}
              virtual
              scroll={{ x: allTableWidth - 10, y: allTableHeight - 25 }}
              columns={[
                { title: 'Table name', dataIndex: 'name', key: 'name', render: renderCell },
                { title: 'Comment', dataIndex: 'comment', key: 'comment', render: renderCell },
              ]}
              pagination={false}
              dataSource={tableData || []}
            />
          </div>
        </Dropdown>
      </div>
      <div className={styles.pagingBox}>
        <Pagination
          onChange={(pageNo) => getTable({ pageNo, pageSize })}
          showSizeChanger={false}
          current={currentPageNo}
          pageSize={pageSize}
          total={tableDataTotal}
        />
      </div>
    </div>
  );
});

export default SqlTableList;
