import connectionService from '@/service/connection';
import {
  getCommonProjectList,
  getCommonTeamList,
  getCommonUserList,
} from '@/service/team';
import { IEnvironmentVO, IProjectVO, ITeamProjectGrantPayload, ITeamVO, IUserVO, SearchType } from '@/typings/team';
import { Form, Modal, Select, Spin } from 'antd';
import debounce from 'lodash/debounce';
import React, { useEffect, useMemo, useState } from 'react';
import i18n from '@/i18n';

interface IProps {
  open: boolean;
  type?: SearchType;
  onConfirm: (values: object) => void;
  onClose: () => void;
  initialValues?: {
    projectId?: number;
    environmentIdList?: number[];
  };
}

interface ValueType {
  id?: number;
  key: number;
  label: React.ReactNode;
  value: number;
}

const addAuthMap = {
  [SearchType.TEAM]: {
    title: i18n('team.action.addTeam'),
    loadRequest: getCommonTeamList,
    searchLabel: (data: ITeamVO) => data.name,
    searchValue: (data: ITeamVO) => data.id,
    searchListKey: 'teamIdList',
    placeholder: i18n('team.action.addTeam.placeholder'),
  },
  [SearchType.USER]: {
    title: i18n('team.action.addUser'),
    loadRequest: getCommonUserList,
    searchLabel: (data: IUserVO) => data.userName,
    searchValue: (data: IUserVO) => data.id,
    searchListKey: 'userIdList',
    placeholder: i18n('team.action.addUser.placeholder'),
  },
  [SearchType.PROJECT]: {
    title: i18n('team.action.addProject'),
    loadRequest: getCommonProjectList,
    searchLabel: (data: IProjectVO) => data.name,
    searchValue: (data: IProjectVO) => data.id,
    searchListKey: 'projectIdList',
    placeholder: i18n('team.action.addProject.placeholder'),
  },
};

function UniversalAddModal(props: IProps) {
  const { open, type } = props;
  const [form] = Form.useForm();

  const [fetching, setFetching] = useState(false);
  const [options, setOptions] = useState<ValueType[]>([]);
  const [selectedValues, setSelectedValues] = useState([]);
  const [environmentList, setEnvironmentList] = useState<IEnvironmentVO[]>([]);

  const authData = useMemo(() => {
    if (type) {
      return addAuthMap[type];
    }
  }, [type]);

  const selectedProjectId = Form.useWatch('projectId', form);
  const availableEnvironmentOptions = useMemo(
    () =>
      environmentList
        .filter((environment) => environment.projectId === selectedProjectId)
        .map((environment) => ({
          label: environment.name,
          value: environment.id,
        })),
    [environmentList, selectedProjectId],
  );

  useEffect(() => {
    if (!open) {
      return;
    }
    if (type === SearchType.PROJECT) {
      getCommonProjectList({ searchKey: '' }).then((res) => {
        const projectOptions = (res || []).map((i) => ({
          ...i,
          label: i.name,
          value: i.id,
          key: i.id,
        }));
        setOptions(projectOptions);
      });
      connectionService.getEnvList().then((res) => {
        setEnvironmentList(res || []);
      });
      form.setFieldsValue({
        projectId: props.initialValues?.projectId,
        environmentIdList: props.initialValues?.environmentIdList || [],
      });
      return;
    }
    loadOptions('');
  }, [open, type, props.initialValues?.projectId, props.initialValues?.environmentIdList]);

  const loadOptions = (value: string) => {
    setOptions([]);
    setFetching(true);

    authData?.loadRequest({ searchKey: value }).then((res) => {
      const newOptions = (res || []).map((i) => ({
        ...i,
        label: authData.searchLabel(i),
        value: authData.searchValue(i),
        key: i.id,
      }));

      setOptions(newOptions);

      setFetching(false);
    });
  };

  const handleOk = () => {
    if (!props.onConfirm || !authData) {
      return;
    }

    if (type === SearchType.PROJECT) {
      form
        .validateFields()
        .then((values: ITeamProjectGrantPayload & { environmentIdList?: number[] }) => {
          props.onConfirm({
            projectGrantList: [
              {
                projectId: values.projectId,
                environmentIdList: values.environmentIdList || [],
              },
            ],
          });
          props.onClose && props.onClose();
          form.resetFields();
          setOptions([]);
        });
      return;
    }

    const realValue = {
      [authData.searchListKey]: selectedValues,
    };

    props.onConfirm(realValue);
    props.onClose && props.onClose();
    setSelectedValues([]);
    setOptions([]);
  };

  return (
    <Modal
      open={open}
      onOk={handleOk}
      onCancel={() => {
        props.onClose && props.onClose();
      }}
      title={authData?.title}
    >
      {type === SearchType.PROJECT ? (
        <Form form={form} layout="vertical">
          <Form.Item
            label={i18n('team.project.name')}
            name="projectId"
            rules={[{ required: true, message: i18n('common.form.error.required') }]}
          >
            <Select
              size="large"
              showSearch
              placeholder={i18n('team.action.addProject.placeholder')}
              options={options}
              onChange={() => form.setFieldValue('environmentIdList', [])}
              filterOption={(input, option) =>
                String(option?.label || '')
                  .toLowerCase()
                  .includes(input.toLowerCase())
              }
            />
          </Form.Item>
          <Form.Item label={i18n('team.project.environments')} name="environmentIdList">
            <Select
              size="large"
              mode="multiple"
              allowClear
              placeholder={i18n('team.project.environments.placeholder')}
              options={availableEnvironmentOptions}
            />
          </Form.Item>
        </Form>
      ) : (
        <Select
          size="large"
          mode="multiple"
          style={{ width: '100%' }}
          onSearch={debounce(loadOptions, 300)}
          placeholder={authData?.placeholder}
          filterOption={false}
          notFoundContent={fetching ? <Spin style={{ margin: '16px 0' }} size="small" /> : null}
          options={options}
          value={selectedValues}
          onChange={(values) => {
            setSelectedValues(values);
          }}
        />
      )}
    </Modal>
  );
}

export default UniversalAddModal;
