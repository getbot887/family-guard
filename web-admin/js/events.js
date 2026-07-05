// FamilyGuard Web — 拦截记录
// =====================================
import { api } from './api.js';
import { showModal, formatDate, escapeHtml } from './ui.js';

let currentPage = 1;

export function loadEvents(page = 1) {
  currentPage = page;
  const listEl = document.getElementById('events-full');
  const prevBtn = document.getElementById('btn-prev-page');
  const nextBtn = document.getElementById('btn-next-page');
  const pageInfo = document.getElementById('page-info');
  if (!listEl) return;
  listEl.innerHTML = '<tr><td colspan="3" class="empty">加载中...</td></tr>';

  api('GET', `/events?page=${page}&page_size=20`).then(res => {
    const data = res?.data || {};
    const events = data.data || [];
    const totalPages = data.total_pages || 1;

    listEl.innerHTML = events.length === 0
      ? '<tr><td colspan="3" class="empty">暂无事件</td></tr>'
      : events.map(e => `<tr>
          <td><strong>${escapeHtml(e.app_name || e.package_name || '—')}</strong></td>
          <td style="color:#64748b;font-size:12px">${escapeHtml(e.package_name || '')}</td>
          <td style="color:#64748b;font-size:12px">${formatDate(e.blocked_at)}</td>
        </tr>`).join('');

    if (pageInfo) pageInfo.textContent = `第 ${page} 页 / 共 ${totalPages} 页`;
    if (prevBtn) { prevBtn.disabled = page <= 1; prevBtn.onclick = () => loadEvents(page - 1); }
    if (nextBtn) { nextBtn.disabled = page >= totalPages; nextBtn.onclick = () => loadEvents(page + 1); }
  });
}
