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
  if (listEl) listEl.innerHTML = '<li class="event-item empty">加载中...</li>';

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
        ? '<li class="event-item empty">暂无事件</li>'
        : events.map(e => `<li class="event-item">
            <div class="event-info">
              <span class="event-app">${escapeHtml(e.app_name || e.package_name || '—')}</span>
              <span class="event-action">blocked</span>
            </div>
            <span class="event-time">${formatDate(e.blocked_at)}</span>
          </li>`).join('');
    }
  });
}
