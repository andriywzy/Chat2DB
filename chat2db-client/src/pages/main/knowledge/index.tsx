import React from 'react';
import i18n from '@/i18n';
import KnowledgeBase from '@/blocks/Setting/KnowledgeBase';
import styles from './index.less';

export default function KnowledgePage() {
  return (
    <div className={styles.page}>
      <div className={styles.pageTitle}>{i18n('setting.nav.knowledge')}</div>
      <KnowledgeBase className={styles.content} />
    </div>
  );
}
