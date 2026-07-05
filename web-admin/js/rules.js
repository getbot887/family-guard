// FamilyGuard Web — 规则管理
// =====================================
import { api } from './api.js';
import { showToast, showModal, hideModal, escapeHtml } from './ui.js';

export function loadRules() {
  const listEl = document.getElementById('rule-list');
  if (!listEl) return;
  listEl.innerHTML = '<tr><td colspan="5" class="empty">加载中...</td></tr>';

  api('GET', '/rules').then(res => {
    const rules = res?.data || [];
    if (rules.length === 0) { listEl.innerHTML = '<tr><td colspan="5" class="empty">暂无规则</td></tr>'; return; }

    listEl.innerHTML = rules.map(r => {
      const badges = (r.apps || []).map(a => `<span class="badge">${escapeHtml(a.app_name || a.package_name || a)}</span>`).join(' ');
      const sched = (r.schedules || []).map(s =>
        `${s.start_time || '—'} - ${s.end_time || '—'}`).join('; ');
      return `<tr>
        <td><label class="toggle"><input type="checkbox" class="rule-toggle" data-id="${r.id}" ${r.is_active ? 'checked' : ''}><span class="toggle-slider"></span></label></td>
        <td><strong>${escapeHtml(r.name)}</strong></td>
        <td>${badges || '<span style="color:#94a3b8">无</span>'}</td>
        <td style="color:#64748b;font-size:12px">${sched || '<span style="color:#94a3b8">—</span>'}</td>
        <td><button class="btn-sm btn-danger rule-delete" data-id="${r.id}">删除</button></td>
      </tr>`;
    }).join('');

    listEl.querySelectorAll('.rule-toggle').forEach(t => t.addEventListener('change', () => toggleRule(t.dataset.id, t.checked)));
    listEl.querySelectorAll('.rule-delete').forEach(b => b.addEventListener('click', () => deleteRule(b.dataset.id)));
  });
}

export function createRule() {
  api('GET', '/devices').then(res => {
    const devices = res?.data || [];
    if (devices.length === 0) { showModal('新建规则', '<p class="empty">请先绑定设备</p>'); return; }

    const promises = devices.map(d => api('GET', `/devices/${d.id}/apps?page=1&page_size=200`).catch(() => ({ data: { data: [] } })));
    Promise.all(promises).then(results => {
      const allApps = {};
      results.forEach(r => {
        const apps = r?.data?.data || r?.data || [];
        apps.forEach(a => { if (a.package_name) allApps[a.package_name] = a.app_name || a.package_name; });
      });

      const keys = Object.keys(allApps);
      const appHtml = keys.length === 0
        ? '<p class="empty">未检测到应用</p>'
        : keys.map(p => `<label class="checkbox-label">
            <input type="checkbox" name="app" value="${escapeHtml(p)}">${escapeHtml(allApps[p])}<span class="app-package">${escapeHtml(p)}</span>
          </label>`).join('');

      showModal('新建规则', `<form id="form-rule">
        <div class="form-group"><label>规则名称</label><input type="text" id="rule-name" required placeholder="例如：学习时间禁止游戏"></div>
        <div class="form-group"><label>关联应用</label><div class="app-selector">${appHtml}</div></div>
        <div class="form-group"><label>生效时段</label>
          <div class="schedule-row">
            <input type="time" id="sched-start" value="08:00"><span>至</span><input type="time" id="sched-end" value="17:00">
          </div>
        </div>
        <div class="form-group"><label>重复</label>
          <div class="day-selector">${['一','二','三','四','五','六','日'].map((n,i) => `<button type="button" class="day-btn" data-day="${i+1}">${n}</button>`).join('')}</div>
        </div>
        <div class="modal-actions"><button type="button" class="btn-ghost" id="cancel-rule">取消</button><button type="submit" class="btn-primary">保存</button></div>
      </form>`);

      setTimeout(() => {
        document.querySelectorAll('.day-btn').forEach(b => b.addEventListener('click', () => b.classList.toggle('active')));
        document.getElementById('cancel-rule')?.addEventListener('click', hideModal);
        document.getElementById('form-rule')?.addEventListener('submit', e => {
          e.preventDefault();
          const name = document.getElementById('rule-name').value.trim();
          if (!name) return showToast('请输入规则名称', 'error');
          const appIds = [...document.querySelectorAll('input[name="app"]:checked')].map(c => c.value);
          const days = [...document.querySelectorAll('.day-btn.active')].map(b => parseInt(b.dataset.day, 10));
          const start = document.getElementById('sched-start').value;
          const end = document.getElementById('sched-end').value;
          const schedules = [{ days_of_week: days, start_time: start || '00:00', end_time: end || '23:59' }];

          api('POST', '/rules', { name, app_ids: appIds, schedules, device_ids: [] }).then(res => {
            if (res.data) { showToast('规则已创建', 'success'); hideModal(); loadRules(); }
            else showToast(res.error || '创建失败', 'error');
          }).catch(err => showToast(err.message, 'error'));
        });
      }, 0);
    });
  });
}

function toggleRule(id, active) {
  api('POST', '/rules/' + id + '/toggle', { is_active: active })
    .then(res => { if (res.success) { showToast(active ? '已启用' : '已禁用', 'success'); loadRules(); } else loadRules(); })
    .catch(() => loadRules());
}

function deleteRule(id) {
  showModal('删除规则', `<p>确定要删除此规则吗？</p>
    <div class="modal-actions"><button class="btn-primary" id="confirm-del">确定删除</button><button class="btn-ghost" id="cancel-del">取消</button></div>`);
  setTimeout(() => {
    document.getElementById('confirm-del')?.addEventListener('click', () => {
      api('DELETE', '/rules/' + id).then(res => { if (res.success) { showToast('已删除', 'success'); hideModal(); loadRules(); } }).catch(err => showToast(err.message, 'error'));
    });
    document.getElementById('cancel-del')?.addEventListener('click', hideModal);
  }, 0);
}
