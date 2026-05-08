import React, { useEffect, useMemo, useRef, useState } from 'react';
import styles from './index.less';
import AIImg from '@/assets/img/ai.svg';
import { Button, Input, Popover, Select, Radio, Space } from 'antd';
import type { InputRef } from 'antd';
import i18n from '@/i18n/';
import Iconfont from '@/components/Iconfont';
import { AIType, IAiScopeEntity } from '@/typings/ai';
import connectionService from '@/service/connection';
import sqlService from '@/service/sql';
import { useConnectionStore } from '@/pages/main/store/connection';

export const enum SyncModelType {
  AUTO = 0,
  MANUAL = 1,
}

interface IBoundInfo {
  dataSourceId?: number;
  dataSourceName?: string;
  databaseName?: string;
  schemaName?: string;
}

interface ISuggestionItem extends IAiScopeEntity {
  id: string;
  subtitle?: string;
}

interface IProps {
  value?: string;
  result?: string;
  tables?: string[];
  scopeHint?: string;
  syncTableModel: number;
  selectedTables?: string[];
  aiType: AIType;
  disabled?: boolean;
  isStream?: boolean;
  boundInfo: IBoundInfo;
  onPressEnter: (value: string) => void;
  onSelectTableSyncModel: (model: number) => void;
  onSelectTables?: (tables: string[]) => void;
  onSelectScopeEntity?: (entity: IAiScopeEntity) => void;
  // onClickRemainBtn: Function;
  onCancelStream: () => void;
}

const ChatInput = (props: IProps) => {
  const [value, setValue] = useState(props.value);
  const [isComposing, setIsComposing] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [suggestions, setSuggestions] = useState<ISuggestionItem[]>([]);
  const inputRef = useRef<InputRef>(null);
  const requestIdRef = useRef(0);
  const connectionList = useConnectionStore((state) => state.connectionList);

  useEffect(() => {
    setValue(props.value);
  }, [props.value]);

  const queryToken = useMemo(() => {
    if (isComposing || !value) {
      return '';
    }
    const match = value.match(/([A-Za-z_][A-Za-z0-9_]*)$/);
    return match?.[1] || '';
  }, [isComposing, value]);

  const currentConnection = useMemo(() => {
    return connectionList?.find((item) => item.id === props.boundInfo.dataSourceId);
  }, [connectionList, props.boundInfo.dataSourceId]);

  const onPressEnter = (nextValue?: string) => {
    const currentValue = (nextValue ?? value ?? '').trim();
    if (!currentValue) {
      return;
    }
    props.onPressEnter && props.onPressEnter(currentValue);
  };

  const closeSuggestions = () => {
    setSuggestions([]);
    setActiveIndex(0);
  };

  useEffect(() => {
    if (!queryToken || !props.boundInfo.dataSourceId || props.disabled) {
      closeSuggestions();
      return;
    }

    const currentRequestId = ++requestIdRef.current;
    const timer = setTimeout(async () => {
      try {
        const [databaseList, tableSearchResult] = await Promise.all([
          currentConnection?.supportDatabase
            ? connectionService.getDatabaseList({
                dataSourceId: props.boundInfo.dataSourceId!,
                refresh: false,
              })
            : Promise.resolve([]),
          sqlService.searchGlobalObjects({
            keyword: queryToken,
            types: ['table'],
            pageNo: 1,
            pageSize: 100,
            refresh: false,
          }),
        ]);

        if (requestIdRef.current !== currentRequestId) {
          return;
        }

        const keyword = queryToken.toLowerCase();
        const databaseSuggestions: ISuggestionItem[] = (databaseList || [])
          .filter((item) => item?.name)
          .map((item) => item.name)
          .filter((name, index, arr) => arr.indexOf(name) === index)
          .filter((name) => name.toLowerCase().includes(keyword))
          .map((name) => ({
            id: `database-${name}`,
            entityType: 'database' as const,
            name,
            dataSourceId: props.boundInfo.dataSourceId,
            dataSourceAlias: props.boundInfo.dataSourceName,
            databaseName: name,
            currentDataSource: true,
            sourceType: 'INPUT_SUGGEST',
          }));

        const tableSuggestions: ISuggestionItem[] = (tableSearchResult?.data || [])
          .filter((item) => item?.dataSourceId === props.boundInfo.dataSourceId && item?.objectName)
          .map((item) => ({
            id: `table-${item.dataSourceId}-${item.databaseName}-${item.schemaName}-${item.objectName}`,
            entityType: 'table' as const,
            name: item.objectName,
            subtitle: [item.databaseName, item.schemaName].filter(Boolean).join(' / '),
            dataSourceId: item.dataSourceId,
            dataSourceAlias: item.dataSourceName,
            databaseName: item.databaseName,
            schemaName: item.schemaName,
            currentDataSource: true,
            sourceType: 'INPUT_SUGGEST',
          }))
          .filter((item, index, arr) => {
            return (
              arr.findIndex((candidate) => {
                return (
                  candidate.name === item.name &&
                  candidate.databaseName === item.databaseName &&
                  candidate.schemaName === item.schemaName
                );
              }) === index
            );
          });

        const nextSuggestions = databaseSuggestions
          .concat(tableSuggestions)
          .sort((a, b) => {
            const aName = a.name.toLowerCase();
            const bName = b.name.toLowerCase();
            const aPrefix = aName.startsWith(keyword) ? 0 : 1;
            const bPrefix = bName.startsWith(keyword) ? 0 : 1;
            if (aPrefix !== bPrefix) {
              return aPrefix - bPrefix;
            }

            const aCurrentDb = a.databaseName === props.boundInfo.databaseName ? 0 : 1;
            const bCurrentDb = b.databaseName === props.boundInfo.databaseName ? 0 : 1;
            if (aCurrentDb !== bCurrentDb) {
              return aCurrentDb - bCurrentDb;
            }

            const aType = a.entityType === 'table' ? 0 : 1;
            const bType = b.entityType === 'table' ? 0 : 1;
            if (aType !== bType) {
              return aType - bType;
            }
            return a.name.localeCompare(b.name);
          })
          .slice(0, 8);

        setSuggestions(nextSuggestions);
        setActiveIndex(0);
      } catch (_error) {
        if (requestIdRef.current === currentRequestId) {
          closeSuggestions();
        }
      }
    }, 180);

    return () => clearTimeout(timer);
  }, [
    currentConnection?.supportDatabase,
    props.boundInfo.dataSourceId,
    props.boundInfo.dataSourceName,
    props.boundInfo.databaseName,
    props.disabled,
    queryToken,
  ]);

  const insertSuggestion = (item: ISuggestionItem) => {
    const input = inputRef.current?.input;
    const selectionStart = input?.selectionStart ?? value?.length ?? 0;
    const selectionEnd = input?.selectionEnd ?? selectionStart;
    const currentValue = value || '';
    const prefix = currentValue.slice(0, selectionStart).replace(/[A-Za-z_][A-Za-z0-9_]*$/, '');
    const suffix = currentValue.slice(selectionEnd);
    const insertedText =
      item.entityType === 'table' && item.databaseName ? `${item.databaseName}.${item.name}` : item.name;
    const insertedValue = `${prefix}${insertedText} ${suffix}`;
    const nextCursor = prefix.length + insertedText.length + 1;

    setValue(insertedValue);
    props.onSelectScopeEntity?.(item);
    closeSuggestions();

    requestAnimationFrame(() => {
      inputRef.current?.focus();
      inputRef.current?.input?.setSelectionRange?.(nextCursor, nextCursor);
    });
  };

  const renderSelectTable = () => {
    const { tables, onSelectTableSyncModel, selectedTables, onSelectTables, syncTableModel } = props;
    const options = (tables || []).map((t) => ({ value: t, label: t }));
    return (
      <div className={styles.aiSelectedTable}>
        <Radio.Group
          onChange={(v) => onSelectTableSyncModel(v.target.value)}
          value={syncTableModel}
          style={{ marginBottom: '8px' }}
        >
          <Space direction="horizontal">
            <Radio value={SyncModelType.AUTO}>自动</Radio>
            <Radio value={SyncModelType.MANUAL}>手动</Radio>
          </Space>
        </Radio.Group>
        {syncTableModel === SyncModelType.AUTO ? (
          <span className={styles.aiSelectedTableTips}>{i18n('chat.input.syncTable.tips')}</span>
        ) : (
          <>
          <span className={styles.aiSelectedTableTips}>{i18n('chat.input.remain.tooltip')}</span>
          <Select
            showSearch
            mode="multiple"
            allowClear
            options={options}
            placeholder={i18n('chat.input.tableSelect.placeholder')}
            value={selectedTables}
            onChange={(v) => {
              onSelectTables && onSelectTables(v);
            }}
          />
          </>
        )}
      </div>
    );
  };

  const renderSuffix = () => {
    return (
      <div className={styles.suffixBlock}>
        {props.isStream ? (
          <Iconfont
            onClick={() => {
              props.onCancelStream && props.onCancelStream();
            }}
            code="&#xe652;"
            className={styles.stop}
          />
        ) : (
          <Button
            type="primary"
            className={styles.enter}
            disabled={props.disabled || !value?.trim()}
            onClick={() => {
              onPressEnter();
            }}
          >
            <Iconfont code="&#xe643;" className={styles.enterIcon} />
          </Button>
        )}
        {/* <Tooltip
          title={<span style={{ color: window._AppThemePack.colorText }}>{i18n('chat.input.syncTable.tempTips')}</span>}
          defaultOpen={!hasBubble}
          color={window._AppThemePack.colorBgBase}
          trigger={'contextMenu'}
          onOpenChange={() => {
            localStorage.setItem('syncTableBubble', 'true');
          }}
        >
        </Tooltip> */}
        <div className={styles.tableSelectBlock}>
          <Popover content={renderSelectTable()} placement="bottomLeft">
            <Iconfont code="&#xe618;" />
          </Popover>
        </div>
      </div>
    );
  };

  const renderSuggestions = () => {
    if (!suggestions.length) {
      return null;
    }

    return (
      <div className={styles.suggestionPanel}>
        {suggestions.map((item, index) => (
          <button
            key={item.id}
            type="button"
            className={`${styles.suggestionItem} ${index === activeIndex ? styles.suggestionItemActive : ''}`}
            onMouseDown={(event) => {
              event.preventDefault();
              insertSuggestion(item);
            }}
          >
            <div className={styles.suggestionMain}>
              <span className={styles.suggestionName}>{item.name}</span>
              {item.subtitle && <span className={styles.suggestionSubtitle}>{item.subtitle}</span>}
            </div>
            <span className={styles.suggestionType}>
              {item.entityType === 'database' ? i18n('chat.input.suggest.database') : i18n('chat.input.suggest.table')}
            </span>
          </button>
        ))}
      </div>
    );
  };

  return (
    <div className={styles.chatContainer}>
      <div className={styles.chatWrapper}>
        <img className={styles.chatAi} src={AIImg} />
        <div className={styles.chatEditorWrapper}>
          <Input
            ref={inputRef}
            className={styles.chatInput}
            bordered={false}
            value={value}
            disabled={props.disabled}
            placeholder={i18n('workspace.ai.input.placeholder')}
            onChange={(event) => {
              setValue(event.target.value);
            }}
            onBlur={() => {
              setTimeout(() => {
                closeSuggestions();
              }, 80);
            }}
            onCompositionStart={() => {
              setIsComposing(true);
              closeSuggestions();
            }}
            onCompositionEnd={(event) => {
              setIsComposing(false);
              setValue(event.currentTarget.value);
            }}
            onKeyDown={(event) => {
              if (suggestions.length) {
                if (event.key === 'ArrowDown') {
                  event.preventDefault();
                  setActiveIndex((prev) => (prev + 1) % suggestions.length);
                  return;
                }
                if (event.key === 'ArrowUp') {
                  event.preventDefault();
                  setActiveIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
                  return;
                }
                if (event.key === 'Enter' || event.key === 'Tab') {
                  event.preventDefault();
                  insertSuggestion(suggestions[activeIndex]);
                  return;
                }
                if (event.key === 'Escape') {
                  event.preventDefault();
                  closeSuggestions();
                  return;
                }
              }

              if (event.key === 'Enter' && !isComposing) {
                event.preventDefault();
                onPressEnter();
              }
            }}
          />
          {renderSuggestions()}
        </div>
        {renderSuffix()}
      </div>
      {props.scopeHint && <div className={styles.scopeHint}>{props.scopeHint}</div>}
    </div>
  );
};

export default ChatInput;
