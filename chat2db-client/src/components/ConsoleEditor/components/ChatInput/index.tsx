import React, { useState } from 'react';
import styles from './index.less';
import AIImg from '@/assets/img/ai.svg';
import { Button, Popover, Select, Radio, Space } from 'antd';
import i18n from '@/i18n/';
import Iconfont from '@/components/Iconfont';
import { AIType } from '@/typings/ai';
import SingleFileMonacoEditor, { ISingleFileMonacoEditorRefFunction } from '@/components/SingleFileMonacoEditor';

export const enum SyncModelType {
  AUTO = 0,
  MANUAL = 1,
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
  onPressEnter: (value: string) => void;
  onSelectTableSyncModel: (model: number) => void;
  onSelectTables?: (tables: string[]) => void;
  // onClickRemainBtn: Function;
  onCancelStream: () => void;
}

const ChatInput = (props: IProps) => {
  const [value, setValue] = useState(props.value);
  const editorRef = React.useRef<ISingleFileMonacoEditorRefFunction>(null);

  const onPressEnter = (nextValue?: string) => {
    const currentValue = (nextValue ?? editorRef.current?.getAllContent?.() ?? '').trim();
    if (!currentValue) {
      return;
    }
    props.onPressEnter && props.onPressEnter(currentValue);
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

  return (
    <div className={styles.chatContainer}>
      <div className={styles.chatWrapper}>
        <img className={styles.chatAi} src={AIImg} />
        <div className={styles.chatEditorWrapper}>
          <SingleFileMonacoEditor
            ref={editorRef}
            className={styles.chatEditor}
            defaultValue={props.value}
            disabled={props.disabled}
            placeholder={i18n('workspace.ai.input.placeholder')}
            onChange={setValue}
            handelEnter={(nextValue) => onPressEnter(nextValue)}
          />
        </div>
        {renderSuffix()}
      </div>
      {props.scopeHint && <div className={styles.scopeHint}>{props.scopeHint}</div>}
    </div>
  );
};

export default ChatInput;
