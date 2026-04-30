import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import i18n from '@/i18n';
import ssoService from '@/service/sso';
import { getCommonTeamList } from '@/service/team';
import { ISsoAdminConfig, ISsoGroupMapping, ITeamVO, SsoAuthMode } from '@/typings';
import { formatDate } from '@/utils/date';
import styles from './index.less';

const defaultConfig: ISsoAdminConfig = {
  enabled: false,
  authMode: 'LOCAL_AND_OIDC',
  scopes: 'openid profile email',
  usernameClaim: 'preferred_username',
  emailClaim: 'email',
  nameClaim: 'name',
  groupsClaim: 'groups',
};

function SsoSetting() {
  const [configForm] = Form.useForm<ISsoAdminConfig>();
  const [mappingForm] = Form.useForm();
  const oidcEnabled = Form.useWatch('enabled', configForm);
  const issuerUri = Form.useWatch('issuerUri', configForm);
  const authorizationUri = Form.useWatch('authorizationUri', configForm);
  const tokenUri = Form.useWatch('tokenUri', configForm);
  const jwkSetUri = Form.useWatch('jwkSetUri', configForm);
  const [loading, setLoading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<string>('');
  const [teamOptions, setTeamOptions] = useState<ITeamVO[]>([]);
  const [mappingList, setMappingList] = useState<ISsoGroupMapping[]>([]);
  const [mappingPagination, setMappingPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
    searchKey: '',
    showSizeChanger: true,
  });
  const [mappingModalOpen, setMappingModalOpen] = useState(false);
  const enabled = Boolean(oidcEnabled);
  const hasIssuer = Boolean(issuerUri?.trim());
  const hasAllManualEndpoints = Boolean(authorizationUri?.trim() && tokenUri?.trim() && jwkSetUri?.trim());

  useEffect(() => {
    void initialize();
  }, []);

  useEffect(() => {
    void queryGroupMappings();
  }, [mappingPagination.current, mappingPagination.pageSize, mappingPagination.searchKey]);

  const mappingColumns = useMemo(
    () => [
      {
        title: i18n('setting.sso.mapping.group'),
        dataIndex: 'externalGroupCode',
        key: 'externalGroupCode',
      },
      {
        title: i18n('setting.sso.mapping.team'),
        key: 'team',
        render: (_: unknown, record: ISsoGroupMapping) => record.team?.name || '-',
      },
      {
        title: i18n('setting.sso.mapping.issuer'),
        dataIndex: 'issuer',
        key: 'issuer',
        ellipsis: true,
      },
      {
        title: i18n('setting.sso.mapping.syncMode'),
        dataIndex: 'syncMode',
        key: 'syncMode',
        render: (value: string) => <Tag color="blue">{value || 'MEMBERSHIP'}</Tag>,
      },
      {
        title: i18n('setting.sso.mapping.updatedAt'),
        dataIndex: 'gmtModified',
        key: 'gmtModified',
        render: (value: string) => formatDate(value, 'yyyy-MM-dd hh:mm:ss') || '-',
      },
      {
        title: i18n('common.text.action'),
        key: 'action',
        width: 150,
        render: (_: unknown, record: ISsoGroupMapping) => (
          <Space size={4}>
            <Button
              type="link"
              onClick={() => {
                mappingForm.setFieldsValue({
                  id: record.id,
                  issuer: record.issuer,
                  externalGroupCode: record.externalGroupCode,
                  teamId: record.team?.id,
                });
                setMappingModalOpen(true);
              }}
            >
              {i18n('common.button.edit')}
            </Button>
            <Popconfirm
              title={i18n('common.tips.delete.confirm')}
              onConfirm={() => handleDeleteMapping(record.id!)}
              okText={i18n('common.button.affirm')}
              cancelText={i18n('common.button.cancel')}
            >
              <Button type="link" danger>
                {i18n('common.button.delete')}
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [teamOptions],
  );

  const initialize = async () => {
    setLoading(true);
    try {
      const [config, teams] = await Promise.all([
        ssoService.getSsoAdminConfig(),
        getCommonTeamList({ searchKey: '' }),
      ]);
      configForm.setFieldsValue({
        ...defaultConfig,
        ...config,
      });
      setTeamOptions(teams || []);
    } finally {
      setLoading(false);
    }
  };

  const queryGroupMappings = async () => {
    const res = await ssoService.getSsoGroupMappingPage({
      pageNo: mappingPagination.current,
      pageSize: mappingPagination.pageSize,
      searchKey: mappingPagination.searchKey,
    });
    if (res) {
      setMappingList(res.data || []);
      setMappingPagination((prev) => ({
        ...prev,
        total: res.total || 0,
      }));
    }
  };

  const handleSaveConfig = async () => {
    const values = await configForm.validateFields();
    await ssoService.saveSsoAdminConfig(values);
    message.success(i18n('common.tips.saveSuccessfully'));
    await initialize();
  };

  const handleTestConnection = async () => {
    const values = await configForm.validateFields();
    setTesting(true);
    try {
      const result = await ssoService.testSsoConnection(values);
      setTestResult(JSON.stringify(result, null, 2));
      message.success(i18n('setting.sso.test.success'));
    } finally {
      setTesting(false);
    }
  };

  const renderLabel = (key: string, required?: boolean) => (
    <span>
      {i18n(key)}
      {required ? <span className={styles.requiredMark}>*</span> : null}
    </span>
  );

  const validateIssuerOrManual = async (value?: string) => {
    if (!enabled) {
      return;
    }
    const issuer = String(value ?? '').trim();
    const authorization = String(configForm.getFieldValue('authorizationUri') ?? '').trim();
    const token = String(configForm.getFieldValue('tokenUri') ?? '').trim();
    const jwkSet = String(configForm.getFieldValue('jwkSetUri') ?? '').trim();
    if (issuer || (authorization && token && jwkSet)) {
      return;
    }
    throw new Error(i18n('setting.sso.validation.discoveryOrManual'));
  };

  const validateManualEndpointOrIssuer = async (fieldName: 'authorizationUri' | 'tokenUri' | 'jwkSetUri', value?: string) => {
    if (!enabled) {
      return;
    }
    const issuer = String(configForm.getFieldValue('issuerUri') ?? '').trim();
    const authorization = String(
      fieldName === 'authorizationUri' ? value ?? '' : configForm.getFieldValue('authorizationUri') ?? '',
    ).trim();
    const token = String(fieldName === 'tokenUri' ? value ?? '' : configForm.getFieldValue('tokenUri') ?? '').trim();
    const jwkSet = String(fieldName === 'jwkSetUri' ? value ?? '' : configForm.getFieldValue('jwkSetUri') ?? '').trim();
    if (issuer || (authorization && token && jwkSet)) {
      return;
    }
    throw new Error(i18n('setting.sso.validation.discoveryOrManual'));
  };

  const handleSubmitMapping = async () => {
    const values = await mappingForm.validateFields();
    const requestApi = values.id ? ssoService.updateSsoGroupMapping : ssoService.createSsoGroupMapping;
    await requestApi(values);
    message.success(i18n('common.tips.saveSuccessfully'));
    setMappingModalOpen(false);
    mappingForm.resetFields();
    await queryGroupMappings();
  };

  const handleDeleteMapping = async (id: number) => {
    await ssoService.deleteSsoGroupMapping({ id });
    message.success(i18n('common.text.successfullyDelete'));
    await queryGroupMappings();
  };

  return (
    <div className={styles.wrapper}>
      <Alert
        showIcon
        type="info"
        className={styles.alert}
        message={i18n('setting.sso.tip')}
        description={i18n('setting.sso.tip.description')}
      />

      <Form form={configForm} layout="vertical" initialValues={defaultConfig}>
        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <div className={styles.sectionTitle}>{i18n('setting.sso.section.config')}</div>
            <Space>
              <Button loading={testing} onClick={handleTestConnection}>
                {i18n('setting.sso.button.test')}
              </Button>
              <Button type="primary" loading={loading} onClick={handleSaveConfig}>
                {i18n('setting.button.apply')}
              </Button>
            </Space>
          </div>

          <Alert
            showIcon
            type="info"
            className={styles.discoveryAlert}
            message={i18n('setting.sso.discovery.title')}
            description={
              <>
                <div>{i18n('setting.sso.discovery.description')}</div>
                <div>{i18n('setting.sso.discovery.claimHint')}</div>
              </>
            }
          />

          <div className={styles.grid}>
            <Form.Item label={i18n('setting.sso.field.enabled')} name="enabled" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label={i18n('setting.sso.field.authMode')} name="authMode">
              <Select
                options={[
                  { label: 'LOCAL_ONLY', value: 'LOCAL_ONLY' as SsoAuthMode },
                  { label: 'LOCAL_AND_OIDC', value: 'LOCAL_AND_OIDC' as SsoAuthMode },
                  { label: 'OIDC_ONLY', value: 'OIDC_ONLY' as SsoAuthMode },
                ]}
              />
            </Form.Item>
            <Form.Item
              label={renderLabel('setting.sso.field.issuerUri', enabled && !hasAllManualEndpoints)}
              name="issuerUri"
              dependencies={['enabled', 'authorizationUri', 'tokenUri', 'jwkSetUri']}
              rules={[
                {
                  validator: async (_rule, value) => validateIssuerOrManual(value),
                },
              ]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label={renderLabel('setting.sso.field.clientId', enabled)}
              name="clientId"
              required={enabled}
              rules={enabled ? [{ required: true, message: i18n('common.form.error.required') }] : []}
            >
              <Input />
            </Form.Item>
            <Form.Item label={i18n('setting.sso.field.clientSecret')} name="clientSecret">
              <Input.Password autoComplete="off" />
            </Form.Item>
            <Form.Item
              label={renderLabel('setting.sso.field.scopes', enabled)}
              name="scopes"
              required={enabled}
              rules={[
                {
                  validator: async (_rule, value) => {
                    if (!enabled) {
                      return;
                    }
                    const normalized = String(value || '')
                      .split(/[,\s]+/)
                      .map((item) => item.trim())
                      .filter(Boolean);
                    if (!normalized.length) {
                      throw new Error(i18n('common.form.error.required'));
                    }
                    if (!normalized.includes('openid')) {
                      throw new Error(i18n('setting.sso.validation.openidScope'));
                    }
                  },
                },
              ]}
            >
              <Input placeholder={i18n('setting.sso.placeholder.scopes')} />
            </Form.Item>
            <Form.Item
              label={renderLabel('setting.sso.field.authorizationUri', enabled && !hasIssuer)}
              name="authorizationUri"
              dependencies={['enabled', 'issuerUri', 'tokenUri', 'jwkSetUri']}
              rules={[
                {
                  validator: async (_rule, value) => validateManualEndpointOrIssuer('authorizationUri', value),
                },
              ]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label={renderLabel('setting.sso.field.tokenUri', enabled && !hasIssuer)}
              name="tokenUri"
              dependencies={['enabled', 'issuerUri', 'authorizationUri', 'jwkSetUri']}
              rules={[
                {
                  validator: async (_rule, value) => validateManualEndpointOrIssuer('tokenUri', value),
                },
              ]}
            >
              <Input />
            </Form.Item>
            <Form.Item label={i18n('setting.sso.field.userInfoUri')} name="userInfoUri">
              <Input />
            </Form.Item>
            <Form.Item
              label={renderLabel('setting.sso.field.jwkSetUri', enabled && !hasIssuer)}
              name="jwkSetUri"
              dependencies={['enabled', 'issuerUri', 'authorizationUri', 'tokenUri']}
              rules={[
                {
                  validator: async (_rule, value) => validateManualEndpointOrIssuer('jwkSetUri', value),
                },
              ]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label={renderLabel('setting.sso.field.redirectUri', enabled)}
              name="redirectUri"
              required={enabled}
              rules={enabled ? [{ required: true, message: i18n('common.form.error.required') }] : []}
            >
              <Input />
            </Form.Item>
            <Form.Item label={i18n('setting.sso.field.logoutRedirectUri')} name="logoutRedirectUri">
              <Input />
            </Form.Item>
            <Form.Item label={i18n('setting.sso.field.usernameClaim')} name="usernameClaim">
              <Input placeholder={i18n('setting.sso.placeholder.usernameClaim')} />
            </Form.Item>
            <Form.Item label={i18n('setting.sso.field.emailClaim')} name="emailClaim">
              <Input placeholder={i18n('setting.sso.placeholder.emailClaim')} />
            </Form.Item>
            <Form.Item label={i18n('setting.sso.field.nameClaim')} name="nameClaim">
              <Input placeholder={i18n('setting.sso.placeholder.nameClaim')} />
            </Form.Item>
            <Form.Item label={i18n('setting.sso.field.groupsClaim')} name="groupsClaim">
              <Input placeholder={i18n('setting.sso.placeholder.groupsClaim')} />
            </Form.Item>
          </div>

          {testResult ? (
            <div className={styles.testBlock}>
              <Typography.Text strong>{i18n('setting.sso.test.result')}</Typography.Text>
              <pre className={styles.testResult}>{testResult}</pre>
            </div>
          ) : null}
        </div>
      </Form>

      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <div className={styles.sectionTitle}>{i18n('setting.sso.section.mapping')}</div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              mappingForm.resetFields();
              setMappingModalOpen(true);
            }}
          >
            {i18n('setting.sso.mapping.add')}
          </Button>
        </div>

        <div className={styles.mappingToolbar}>
          <Input.Search
            style={{ width: 320 }}
            placeholder={i18n('setting.sso.mapping.search')}
            enterButton={<SearchOutlined />}
            onSearch={(searchKey) => {
              setMappingPagination((prev) => ({
                ...prev,
                current: 1,
                searchKey,
              }));
            }}
          />
        </div>

        <Table
          rowKey="id"
          dataSource={mappingList}
          columns={mappingColumns as any}
          pagination={mappingPagination}
          onChange={(pagination) => {
            setMappingPagination((prev) => ({
              ...prev,
              current: pagination.current || 1,
              pageSize: pagination.pageSize || 10,
            }));
          }}
        />
      </div>

      <Modal
        open={mappingModalOpen}
        title={i18n('setting.sso.mapping.modalTitle')}
        onOk={handleSubmitMapping}
        onCancel={() => {
          setMappingModalOpen(false);
          mappingForm.resetFields();
        }}
      >
        <Form form={mappingForm} layout="vertical">
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
          <Form.Item
            label={i18n('setting.sso.mapping.group')}
            name="externalGroupCode"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label={i18n('setting.sso.mapping.issuer')}
            name="issuer"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label={i18n('setting.sso.mapping.team')}
            name="teamId"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Select
              options={(teamOptions || []).map((team) => ({
                label: team.name,
                value: team.id,
              }))}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default SsoSetting;
