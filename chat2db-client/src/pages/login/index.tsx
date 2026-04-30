import React, { useEffect, useState } from 'react';
import { Button, Divider, Form, Input, Tooltip } from 'antd';
import { userLogin } from '@/service/user';
import LogoImg from '@/assets/logo/logo.png';
import styles from './index.less';
import Setting from '@/blocks/Setting';
import Iconfont from '@/components/Iconfont';
import i18n from '@/i18n';
import ssoService from '@/service/sso';
import { IOidcPublicConfig } from '@/typings';
// import { useNavigate } from 'react-router-dom';
import { logoutClearSomeLocalStorage, navigate } from '@/utils';
import { queryCurUser } from '@/store/user';

interface IFormData {
  userName: string;
  password: string;
}

const Login: React.FC = () => {
  const [oidcConfig, setOidcConfig] = useState<IOidcPublicConfig>({
    enabled: false,
    authMode: 'LOCAL_AND_OIDC',
    allowLocalLogin: true,
    authorizePath: '/api/oauth/oidc/authorize',
    buttonText: 'Enterprise SSO',
  });

  useEffect(() => {
    logoutClearSomeLocalStorage();
    void ssoService
      .getOidcPublicConfig()
      .then((config) => {
        if (config) {
          setOidcConfig(config);
        }
      })
      .catch(() => void 0);
  }, []);

  const handleLogin = async (formData: IFormData) => {
    const token = await userLogin(formData);
    const res = await queryCurUser();
    if (token && res) {
      navigate('/');
    }
  };

  const handleSsoLogin = () => {
    const search = new URLSearchParams(window.location.search);
    const callback = search.get('callback') || (__ENV__ === 'desktop' ? '#/' : '/');
    window.location.assign(`${window._BaseURL}${oidcConfig.authorizePath}?callback=${encodeURIComponent(callback)}`);
  };

  return (
    <div className={styles.loginPage}>
      <div className={styles.logo}>
        <img className={styles.logoImage} src={LogoImg} />
        <div className={styles.logoText}>Chat2DB</div>
      </div>
      <div className={styles.loginPlane}>
        <div className={styles.loginWelcome}>{i18n('login.text.welcome')}</div>
        <Tooltip
          placement="right"
          color={window._AppThemePack?.colorBgBase}
          title={
            <div style={{ color: window._AppThemePack?.colorText, opacity: 0.8, padding: '8px 4px' }}>
              {i18n('login.text.tips')}
            </div>
          }
        >
          <div className={styles.whyLogin}>{i18n('login.text.tips.title')}</div>
        </Tooltip>

        {oidcConfig.enabled ? (
          <Button type="primary" className={styles.loginSsoBtn} onClick={handleSsoLogin}>
            {oidcConfig.buttonText || i18n('login.button.sso')}
          </Button>
        ) : null}

        {oidcConfig.enabled && oidcConfig.allowLocalLogin ? (
          <Divider className={styles.loginDivider}>{i18n('login.text.or')}</Divider>
        ) : null}

        {oidcConfig.allowLocalLogin ? (
          <Form className={styles.loginForm} size="large" onFinish={handleLogin}>
            <Form.Item
              className={styles.loginFormItem}
              name="userName"
              rules={[{ required: true, message: i18n('login.form.user.placeholder') }]}
            >
              <Input autoComplete="off" placeholder={i18n('login.form.user')} />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: i18n('login.form.password.placeholder') }]}>
              <Input.Password placeholder={i18n('login.form.password')} />
            </Form.Item>
            <div className={styles.defaultPasswordTips}>{i18n('login.tips.defaultPassword')}</div>
            <Button type="primary" htmlType="submit" className={styles.loginFormSubmit}>
              {i18n('login.button.login')}
            </Button>
          </Form>
        ) : (
          <div className={styles.ssoOnlyTips}>{i18n('login.text.ssoOnly')}</div>
        )}
      </div>

      <Setting
        className={styles.setting}
        noLogin
        render={
          <Button
            type="text"
            icon={<Iconfont style={{ fontSize: '14px' }} code="&#xe630;" />}
            className={styles.settingBtn}
          >
            {i18n('login.text.setting')}
          </Button>
        }
      />
    </div>
  );
};

export default Login;
