import React, { memo, useCallback, useEffect, useMemo, ForwardedRef, forwardRef, useImperativeHandle, useRef, useState } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import MonacoEditor, { IExportRefFunction } from '@/components/MonacoEditor';
import { v4 as uuid } from 'uuid';

interface IProps {
  className?: string;
  handelEnter?: (value: string) => void;
  focusChange?: (isActive: boolean) => void;
  defaultValue?: string;
  placeholder?: string;
  disabled?: boolean;
  onChange?: (value: string) => void;
}

export interface ISingleFileMonacoEditorRefFunction {
  getAllContent?: () => string;
  setValue?: (value: string) => void;
}

const options = {
  lineNumbers: false,
  renderLineHighlight: 'none',
  scrollBeyondLastLine: false,
  wordWrap: 'off',
  minimap: {
    enabled: false,
  },
  // 不显示滚动条
  scrollbar: {
    vertical: 'hidden',
    horizontal: 'hidden',
  },
  overviewRulerBorder: false,
  glyphMargin: false,
  folding: false,
  lineDecorationsWidth: 0, // 行号宽度
  lineNumbersMinChars: 0, // 行号最小宽度
};

const SingleFileMonacoEditor = forwardRef(
  (props: IProps, ref: ForwardedRef<ISingleFileMonacoEditorRefFunction>) => {
    const { className, handelEnter, focusChange, defaultValue, placeholder, disabled, onChange } = props;
    const editorRef = useRef<any>(null);
    const monacoEditorRef = useRef<IExportRefFunction>(null);
    const changeListenerRef = useRef<{ dispose: () => void } | null>(null);
    const [value, setValue] = useState(defaultValue || '');
    const [isFocused, setIsFocused] = useState(false);

    const editorId = useMemo(() => {
      return uuid();
    }, []);

    const handleKeydown = useCallback((event) => {
      if (event.key === 'Enter' && editorRef.current) {
        const controller = editorRef.current.getContribution('editor.contrib.suggestController') as any;
        const suggestWidget = controller._widget;
        if (suggestWidget && suggestWidget.suggestWidgetVisible.get()) {
          return;
        }
        // 否则，阻止回车键的默认行为
        event.preventDefault();
        const value = monacoEditorRef.current?.getAllContent().trim() || '';
        handelEnter && handelEnter(value);
      }
    }, []);

    // 监听keydown事件，阻止回车键的默认行为
    const registerShortcutKey = useCallback((_editor, _monaco, isActive) => {
      if (isActive) {
        editorRef.current = _editor;
        window.addEventListener('keydown', handleKeydown);
      } else {
        window.removeEventListener('keydown', handleKeydown);
      }
    }, []);

    const getAllContent = () => {
      return monacoEditorRef.current?.getAllContent() || '';
    };

    const setEditorValue = (nextValue: string) => {
      monacoEditorRef.current?.setValue?.(nextValue, 'cover');
      setValue(nextValue);
    };

    useEffect(() => {
      const nextValue = defaultValue || '';
      setValue(nextValue);
      if (monacoEditorRef.current && getAllContent() !== nextValue) {
        monacoEditorRef.current.setValue(nextValue, 'cover');
      }
    }, [defaultValue]);

    useEffect(() => {
      editorRef.current?.updateOptions({
        readOnly: !!disabled,
      });
    }, [disabled]);

    useEffect(() => {
      return () => {
        changeListenerRef.current?.dispose();
      };
    }, []);

    useImperativeHandle(ref, () => ({
      getAllContent,
      setValue: setEditorValue,
    }));

    return (
      <div className={classnames(styles.singleFileMonacoEditor, className)}>
        {!value && !isFocused && placeholder && <div className={styles.placeholder}>{placeholder}</div>}
        <MonacoEditor
          ref={monacoEditorRef}
          id={editorId}
          options={options as any}
          shortcutKey={registerShortcutKey}
          defaultValue={defaultValue}
          onChange={(nextValue) => {
            setValue(nextValue);
            onChange?.(nextValue);
          }}
          focusChange={(active) => {
            setIsFocused(active);
            focusChange?.(active);
          }}
          didMount={(editor) => {
            editorRef.current = editor;
            editor.updateOptions({
              readOnly: !!disabled,
            });
            changeListenerRef.current?.dispose();
            changeListenerRef.current = editor.onDidChangeModelContent((event) => {
              if (disabled || event.isFlush) {
                return;
              }
              const shouldTriggerSuggest = event.changes.some((change) => {
                const insertedText = change.text || '';
                if (!insertedText || insertedText.includes('\n')) {
                  return false;
                }
                return /[A-Za-z_]$/.test(insertedText);
              });

              if (shouldTriggerSuggest) {
                editor.trigger('chat-input', 'editor.action.triggerSuggest', {});
              }
            });
          }}
        />
      </div>
    );
  },
);

export default memo(SingleFileMonacoEditor);
