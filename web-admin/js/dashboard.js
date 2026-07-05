// FamilyGuard Web — 仪表盘
// =====================================
import { api } from './api.js';
import { showToast, showPage, formatDate, escapeHtml } from './ui.js';

export function loadDashboard() {
  const todayEl = document.getElementById('stat-today');
  const totalEl = document.getElementById('stat-total');
  const boundEl = document.getElementById('stat-bound');
  const onlineEl = document.getElementById('stat-online');
  const listEl = document.getElementById('dashboard-events');
  [todayEl, totalEl, boundEl, onlineEl].forEach(el => { if (el) el.textContent = '—'; });
  if (listEl) listEl.innerHTML = '<tr><td colspan="3" class="empty">加载中...</td></tr>';

  Promise.all([
    api('GET', '/events/stats').catch(() => null),
    api('GET', '/devices').catch(() => null),
    api('GET', '/events?page=1&page_size=5').catch(() => null),
  ]).then(([statsRes, devRes, evtRes]) => {
    if (statsRes?.data) {
      if (todayEl) todayEl.textContent = statsRes.data.today_blocked ?? 0;
      if (totalEl) totalEl.textContent = statsRes.data.total_blocked ?? 0;
    }
    if (devRes?.data) {
      if (boundEl) boundEl.textContent = devRes.data.length;
      if (onlineEl) onlineEl.textContent = devRes.data.filter(d => d.is_online).length;
    }
    if (listEl) {
      const events = evtRes?.data?.data || [];
      listEl.innerHTML = events.length === 0
        ? '<tr><td colspan="3" class="empty">暂无事件</td></tr>'
        : events.map(e => `<tr>
            <td><strong>${escapeHtml(e.app_name || e.package_name || '—')}</strong></td>
            <td style="color:#64748b;font-size:12px">${escapeHtml(e.package_name || '')}</td>
            <td style="color:#64748b;font-size:12px">${formatDate(e.blocked_at)}</td>
          </tr>`).join('');
    }
  });
}
