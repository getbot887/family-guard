// FamilyGuard Web — 规则管理
// =====================================
import { api } from './api.js';
import { showToast, showModal, hideModal, escapeHtml } from './ui.js';

let editingRuleId = null;

export function loadRules() {
  const listEl = document.getElementById('rule-list');
  if (!listEl) return;
  listEl.innerHTML = '<tr><td colspan="6" class="empty">加载中...</td></tr>';

  api('GET', '/rules').then(res => {
    const rules = res?.data || [];
    if (rules.length === 0) { listEl.innerHTML = '<tr><td colspan="6" class="empty">暂无规则</td></tr>'; return; }

    listEl.innerHTML = rules.map(r => {
      const badges = (r.apps || []).map(a => `<span class="badge">${escapeHtml(a.app_name || a.package_name || a)}</span>`).join(' ');
      const sched = (r.schedules || []).map(s =>
        `${s.start_time || '—'} - ${s.end_time || '—'}`).join('; ');
      const deviceNames = r.device_ids?.length ? `(${r.device_ids.length}台设备)` : '(全部设备)';
      return `<tr>
        <td><label class="toggle"><input type="checkbox" class="rule-toggle" data-id="${r.id}" ${r.is_active ? 'checked' : ''}><span class="toggle-slider"></span></label></td>
        <td><strong>${escapeHtml(r.name)}</strong></td>
        <td>${badges || '<span style="color:#94a3b8">无</span>'}</td>
        <td style="color:#64748b;font-size:12px">${sched || '<span style="color:#94a3b8">—</span>'}</td>
        <td style="color:#64748b;font-size:12px">${deviceNames}</td>
        <td>
          <button class="btn-ghost btn-sm btn-edit-rule" data-id="${r.id}">编辑</button>
          <button class="btn-sm btn-danger btn-delete-rule" data-id="${r.id}">删除</button>
        </td>
      </tr>`;
    }).join('');

    listEl.querySelectorAll('.rule-toggle').forEach(t => t.addEventListener('change', () => toggleRule(t.dataset.id, t.checked)));
    listEl.querySelectorAll('.btn-edit-rule').forEach(b => b.addEventListener('click', () => editRule(b.dataset.id)));
    listEl.querySelectorAll('.btn-delete-rule').forEach(b => b.addEventListener('click', () => deleteRule(b.dataset.id)));
  });
}

export function createRule() {
  editingRuleId = null;
  loadFormAndShow(null);
}

function editRule(id) {
  editingRuleId = id;
  api('GET', '/rules').then(res => {
    const rule = (res?.data || []).find(r => r.id == id);
    if (rule) loadFormAndShow(rule);
  });
}

function loadFormAndShow(rule) {
  const isEdit = !!rule;
  Promise.all([
    api('GET', '/devices').catch(() => ({ data: [] })),
    isEdit ? Promise.resolve(null) : null,
  ]).then(([devRes]) => {
    const devices = devRes?.data || [];

    // 加载所有设备的App列表
    const appPromises = devices.map(d => api('GET', `/devices/${d.id}/apps?page=1&page_size=200`).catch(() => ({ data: { data: [] } })));
    Promise.all(appPromises).then(appResults => {
      const allApps = {};
      appResults.forEach(r => {
        const apps = r?.data?.data || r?.data || [];
        apps.forEach(a => { if (a.package_name) allApps[a.package_name] = a.app_name || a.package_name; });
      });

      const selectedApps = new Set((rule?.apps || []).map(a => a.package_name));
      const selectedDevices = new Set(rule?.device_ids || []);
      const selectedSched = rule?.schedules?.[0] || {};
      const selectedDays = new Set(selectedSched.days_of_week || []);

      const keys = Object.keys(allApps);
      const appHtml = keys.length === 0
        ? '<p class="empty">未检测到应用</p>'
        : keys.map(p => `<label class="checkbox-label">
            <input type="checkbox" name="app" value="${escapeHtml(p)}" ${selectedApps.has(p)?'checked':''}>${escapeHtml(allApps[p])}<span class="app-package">${escapeHtml(p)}</span>
          </label>`).join('');

      const deviceHtml = devices.length === 0
        ? '<p class="empty">无设备</p>'
        : devices.map(d => `<label class="checkbox-label">
            <input type="checkbox" name="device" value="${d.id}" ${selectedDevices.has(d.id)?'checked':''}>${escapeHtml(d.device_name||'设备'+d.id)}
          </label>`).join('');

      showModal(isEdit ? '编辑规则' : '新建规则', `<form id="form-rule">
        <div class="form-group"><label>规则名称</label><input type="text" id="rule-name" required placeholder="例如：学习时间禁止游戏" value="${escapeHtml(rule?.name||'')}"></div>
        <div class="form-group"><label>关联应用</label><div class="app-selector">${appHtml}</div></div>
        <div class="form-group"><label>生效时段</label>
          <div class="schedule-row">
            <input type="time" id="sched-start" value="${selectedSched.start_time||'08:00'}"><span>至</span><input type="time" id="sched-end" value="${selectedSched.end_time||'17:00'}">
          </div>
        </div>
        <div class="form-group"><label>重复</label>
          <div class="day-selector">${['一','二','三','四','五','六','日'].map((n,i) => `<button type="button" class="day-btn ${selectedDays.has(i+1)?'active':''}" data-day="${i+1}">${n}</button>`).join('')}</div>
        </div>
        <div class="form-group"><label>适用设备（不选=所有设备）</label><div style="max-height:120px;overflow-y:auto;display:flex;flex-direction:column;gap:2px;padding:8px;background:#f8fafc;border-radius:6px">${deviceHtml}</div></div>
        <div class="modal-actions"><button type="button" class="btn-ghost" id="cancel-rule">取消</button><button type="submit" class="btn-primary">${isEdit?'保存修改':'创建'}</button></div>
      </form>`);

      setTimeout(() => {
        document.querySelectorAll('.day-btn').forEach(b => b.addEventListener('click', () => b.classList.toggle('active')));
        document.getElementById('cancel-rule')?.addEventListener('click', hideModal);
        document.getElementById('form-rule')?.addEventListener('submit', e => {
          e.preventDefault();
          const name = document.getElementById('rule-name').value.trim();
          if (!name) return showToast('请输入规则名称', 'error');
          const appIds = [...document.querySelectorAll('input[name="app"]:checked')].map(c => c.value);
          const deviceIds = [...document.querySelectorAll('input[name="device"]:checked')].map(c => parseInt(c.value, 10));
          const days = [...document.querySelectorAll('.day-btn.active')].map(b => parseInt(b.dataset.day, 10));
          const start = document.getElementById('sched-start').value;
          const end = document.getElementById('sched-end').value;
          const schedules = [{ days_of_week: days, start_time: start || '00:00', end_time: end || '23:59' }];
          const body = { name, app_ids: appIds, schedules, device_ids: deviceIds };

          const request = isEdit
            ? api('PUT', '/rules/' + editingRuleId, body)
            : api('POST', '/rules', body);

          request.then(res => {
            if (res.data || res.success) {
              showToast(isEdit ? '规则已更新' : '规则已创建', 'success');
              hideModal(); loadRules();
            } else showToast(res.error || '操作失败', 'error');
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
