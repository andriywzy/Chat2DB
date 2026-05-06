import React, { useEffect, useMemo, useState } from 'react';
import { Modal, Upload, Button, message, Select, Typography } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import i18n from '@/i18n';
import connectionService from '@/service/connection';
import { useConnectionStore } from '@/pages/main/store/connection';
import { downloadJsonFile } from '@/utils/file';
import { IConnectionTemplate } from '@/typings';

const uploadFileType = {
  ncx: {
    accept: '.ncx',
    uploadUrl: window._BaseURL + '/api/converter/ncx/upload',
  },
  dbp: {
    accept: '.dbp',
    uploadUrl: window._BaseURL + '/api/converter/dbp/upload',
  },
};

interface IImportConnectionProps {
  open: boolean;
  onClose: () => void;
  onConfirm?: () => void;
}

const ImportConnection: React.FC<IImportConnectionProps> = ({ open, onClose, onConfirm }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [defaultEnvironmentId, setDefaultEnvironmentId] = useState<number | undefined>();
  const { connectionEnvList } = useConnectionStore((state) => ({
    connectionEnvList: state.connectionEnvList,
  }));

  const environmentOptions = useMemo(() => {
    return (connectionEnvList || []).map((item) => ({
      label: item.shortName || item.name,
      value: item.id,
    }));
  }, [connectionEnvList]);

  useEffect(() => {
    if (open) {
      setSelectedFile(null);
      setDefaultEnvironmentId(connectionEnvList?.[0]?.id);
    }
  }, [open, connectionEnvList]);

  const handleBeforeUpload = (file: File) => {
    setSelectedFile(file);
    return false; // 停止自动上传
  };

  const handleDownloadTemplate = async () => {
    const template = await connectionService.getImportTemplate();
    downloadJsonFile('chat2db-connection-template.json', template);
  };

  const normalizeTemplate = (payload: any): IConnectionTemplate => {
    if (Array.isArray(payload)) {
      return {
        version: '1.0',
        template: 'chat2db.datasource.template',
        exportedAt: new Date().toISOString(),
        connections: payload,
      };
    }
    return {
      version: payload?.version || '1.0',
      template: payload?.template || 'chat2db.datasource.template',
      exportedAt: payload?.exportedAt || new Date().toISOString(),
      connections: Array.isArray(payload?.connections) ? payload.connections : [],
    };
  };

  const handleConfirmUpload = async () => {
    if (!selectedFile) return;

    const fileExtension = selectedFile.name.split('.').pop() || '';

    try {
      setUploading(true);
      if (fileExtension === 'json') {
        const text = await selectedFile.text();
        const payload = normalizeTemplate(JSON.parse(text));
        const result = await connectionService.importConnections({
          version: payload.version,
          template: payload.template,
          defaultEnvironmentId,
          connections: payload.connections,
        });
        if (result.failureCount > 0) {
          message.warning(
            i18n('connection.message.importPartialSuccess', result.successCount, result.failureCount),
          );
        } else {
          message.success(i18n('connection.message.importSuccess', result.successCount));
        }
        onConfirm && onConfirm();
        return;
      }

      const formData = new FormData();
      formData.append('file', selectedFile);
      const { uploadUrl } = uploadFileType[fileExtension] || {};
      if (!uploadUrl) {
        message.error(i18n('connection.message.unsupportedImportFile'));
        return;
      }
      const response = await fetch(uploadUrl, {
        method: 'POST',
        body: formData,
      });
      if (!response.ok) {
        message.error(`${selectedFile.name} 导入数据源失败`);
        return;
      }
      message.success(`${selectedFile.name} 导入数据源成功`);
      onConfirm && onConfirm();
    } catch (error) {
      message.error(`Error: ${error}`);
    } finally {
      setUploading(false);
    }
  };

  return (
    <Modal
      title={i18n('connection.title.importTitle')}
      open={open}
      onCancel={onClose}
      onOk={handleConfirmUpload}
      confirmLoading={uploading}
    >
      <Typography.Paragraph type="secondary">
        {i18n('connection.tips.importTemplate')}
      </Typography.Paragraph>
      <Upload
        name="file"
        accept=".json,.ncx,.dbp"
        beforeUpload={handleBeforeUpload}
        maxCount={1}
        fileList={
          selectedFile
            ? [
                {
                  uid: selectedFile.name,
                  name: selectedFile.name,
                  status: 'done',
                  originFileObj: selectedFile,
                } as any,
              ]
            : []
        }
      >
        <Button icon={<UploadOutlined />}>{i18n('common.text.selectFile')}</Button>
      </Upload>
      <div style={{ marginTop: 12 }}>
        <Select
          allowClear
          style={{ width: '100%' }}
          placeholder={i18n('connection.placeholder.defaultEnvironment')}
          options={environmentOptions}
          value={defaultEnvironmentId}
          onChange={setDefaultEnvironmentId}
        />
      </div>
      <div style={{ marginTop: 12 }}>
        <Button onClick={handleDownloadTemplate}>{i18n('connection.button.downloadTemplate')}</Button>
      </div>
    </Modal>
  );
};

export default ImportConnection;
