// FamilyGuard Web — 使用时长
// =====================================
import { api } from './api.js';
import { formatDate, escapeHtml } from './ui.js';

export function loadUsage() {
  const dateEl = document.getElementById('usage-date');
  const listEl = document.getElementById('usage-list');
  if (!listEl) return;
  const date = dateEl?.value || new Date().toISOString().slice(0, 10);
  listEl.innerHTML = '<tr><td colspan="3" class="empty">加载中...</td></tr>';

  api('GET', `/usage?date=${date}`).then(res => {
    const stats = res?.data || [];
    if (stats.length === 0) {
      listEl.innerHTML = '<tr><td colspan="3" class="empty">暂无数据（孩子端需开启使用访问权限后自动上报）</td></tr>';
      return;
    }
    listEl.innerHTML = stats.map(s => `<tr>
      <td><strong>${escapeHtml(s.app_name || s.package_name)}</strong></td>
      <td style="color:#64748b;font-size:12px">${escapeHtml(s.package_name)}</td>
      <td>${s.usage_minutes} 分钟</td>
    </tr>`).join('');
  }).catch(() => {
    listEl.innerHTML = '<tr><td colspan="3" class="empty">加载失败</td></tr>';
  });
}
