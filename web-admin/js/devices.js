// FamilyGuard Web — 设备管理
// =====================================
import { api } from './api.js';
import { showToast, showModal, hideModal, formatDate, escapeHtml } from './ui.js';

export function loadDevices() {
  const listEl = document.getElementById('device-list');
  if (!listEl) return;
  listEl.innerHTML = '<li class="device-item empty">加载中...</li>';

  api('GET', '/devices').then(res => {
    const devs = res?.data || [];
    if (devs.length === 0) {
      listEl.innerHTML = '<li class="device-item empty">暂无绑定设备</li>';
      return;
    }
    listEl.innerHTML = devs.map(d => {
      const statusClass = d.is_online ? 'online' : 'offline';
      const statusText = d.is_online ? '在线' : '离线';
      return `<li class="device-item" data-id="${escapeHtml(d.id)}">
        <div class="device-info">
          <span class="device-status-dot ${statusClass}"></span>
          <div class="device-text">
            <span class="device-name">${escapeHtml(d.device_name || d.model || '未知设备')}</span>
            <span class="device-meta">${escapeHtml(d.model || '')} · ${statusText}</span>
            <span class="device-meta">最后在线: ${formatDate(d.last_seen_at)}</span>
          </div>
        </div>
        <div class="device-actions">
          <button class="btn-view-apps" data-id="${escapeHtml(d.id)}">查看应用</button>
          <button class="btn-unbind-device" data-id="${escapeHtml(d.id)}" data-name="${escapeHtml(d.device_name || d.model || '')}">解绑</button>
        </div>
      </li>`;
    }).join('');

    listEl.querySelectorAll('.btn-view-apps').forEach(b =>
      b.addEventListener('click', () => loadDeviceApps(b.dataset.id)));
    listEl.querySelectorAll('.btn-unbind-device').forEach(b =>
      b.addEventListener('click', () => confirmUnbind(b.dataset.id, b.dataset.name)));
  });
}

export function bindDevice() {
  showModal('绑定设备', `<form id="form-bind">
    <div class="form-group"><label>配对码</label><input type="text" id="bind-code" required maxlength="6" placeholder="6位数字，由家长设置"></div>
    <div class="form-group"><label>设备名称</label><input type="text" id="bind-name" required placeholder="例如：小明的手机"></div>
    <div class="modal-actions"><button type="button" class="btn-ghost" id="cancel-bind">取消</button><button type="submit" class="btn-primary">绑定</button></div>
  </form>`);

  setTimeout(() => {
    document.getElementById('cancel-bind')?.addEventListener('click', hideModal);
    document.getElementById('form-bind')?.addEventListener('submit', e => {
      e.preventDefault();
      const pairingCode = document.getElementById('bind-code').value.trim();
      const deviceName = document.getElementById('bind-name').value.trim();
      if (!pairingCode || !deviceName) return showToast('请填写所有字段', 'error');
      if (pairingCode.length !== 6) return showToast('配对码为6位数字', 'error');
      api('POST', '/devices/bind', { pairing_code: pairingCode, device_name: deviceName })
        .then(res => { if (res.data) { showToast('绑定成功', 'success'); hideModal(); loadDevices(); } else showToast(res.error || '绑定失败', 'error'); })
        .catch(err => showToast(err.message, 'error'));
    });
  }, 100);
}

function confirmUnbind(id, name) {
  showModal('解绑设备', `<p>确定要解绑 <strong>${escapeHtml(name)}</strong> 吗？</p>
    <div class="modal-actions"><button class="btn-primary" id="confirm-unbind">确定解绑</button><button class="btn-ghost" id="cancel-unbind">取消</button></div>`);
  setTimeout(() => {
    document.getElementById('confirm-unbind')?.addEventListener('click', () => {
      api('DELETE', '/devices/' + id).then(res => {
        if (res.success) { showToast('已解绑', 'success'); hideModal(); loadDevices(); }
        else showToast(res.error || '解绑失败', 'error');
      }).catch(err => showToast(err.message, 'error'));
    });
    document.getElementById('cancel-unbind')?.addEventListener('click', hideModal);
  }, 0);
}

function loadDeviceApps(deviceId) {
  showModal('已安装应用', '<p style="color:#888">加载中...</p>');
  api('GET', `/devices/${deviceId}/apps?page=1&page_size=100`).then(res => {
    const apps = res?.data?.data || res?.data || [];
    const html = apps.length === 0
      ? '<p class="empty">暂无应用数据</p>'
      : '<ul class="app-list">' + apps.map(a => `<li class="app-item"><span class="app-name">${escapeHtml(a.app_name || '未知')}</span><span class="app-package">${escapeHtml(a.package_name || '')}</span></li>`).join('') + '</ul>';
    showModal('已安装应用', html + '<div class="modal-actions" style="margin-top:16px"><button class="btn-ghost modal-close-btn">关闭</button></div>');
    setTimeout(() => document.querySelector('.modal-close-btn')?.addEventListener('click', hideModal), 0);
  }).catch(() => showModal('已安装应用', '<p class="empty">加载失败</p><div class="modal-actions" style="margin-top:16px"><button class="btn-ghost modal-close-btn">关闭</button></div>'));
}
