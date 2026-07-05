// FamilyGuard Web — 日志查看
// =====================================
import { api } from './api.js';
import { showToast } from './ui.js';

export function loadLogs() {
  const source = document.getElementById('log-source')?.value || 'child-app';
  const level = document.getElementById('log-level')?.value || 'all';
  const contentEl = document.getElementById('log-content');
  if (!contentEl) return;
  contentEl.textContent = '加载中...';

  const params = new URLSearchParams({ page_size: '200' });
  if (source !== 'all') params.set('source', source);
  if (level !== 'all') params.set('level', level);

  api('GET', '/logs?' + params.toString()).then(res => {
    const logs = res?.data?.data || [];
    if (logs.length === 0) {
      contentEl.textContent = '暂无日志';
      return;
    }
    contentEl.textContent = logs.map(l => {
      const time = l.logged_at ? new Date(l.logged_at).toLocaleString() : '—';
      const levelTag = l.level === 'error' ? '❌' : l.level === 'warn' ? '⚠️' : '📘';
      const tag = l.tag || '';
      const msg = l.message || '';
      return `${time} ${levelTag} [${l.level}] [${tag}] ${msg}`;
    }).join('\n');
    contentEl.scrollTop = contentEl.scrollHeight;
  }).catch(err => {
    contentEl.textContent = '加载失败: ' + err.message;
  });
}
