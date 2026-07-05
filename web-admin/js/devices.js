// FamilyGuard Web — 设备管理
// =====================================
import { api } from './api.js';
import { showToast, showModal, hideModal, formatDate, escapeHtml } from './ui.js';

let refreshTimer = null;

export function loadDevices() {
  const listEl = document.getElementById('device-list');
  if (!listEl) return;
  listEl.innerHTML = '<tr><td colspan="7" class="empty">加载中...</td></tr>';

  // 启动自动刷新（每3秒）
  if (refreshTimer) clearInterval(refreshTimer);
  refreshTimer = setInterval(() => { refreshDeviceList(); }, 3000);

  refreshDeviceList();
}

function refreshDeviceList() {
  api('GET', '/devices').then(res => {
    const devs = res?.data || [];
    const listEl = document.getElementById('device-list');
    if (!listEl) return;
    if (devs.length === 0) {
      listEl.innerHTML = '<tr><td colspan="7" class="empty">暂无绑定设备</td></tr>';
      return;
    }
    listEl.innerHTML = devs.map(d => {
      const statusDot = d.is_online ? '🟢' : '🔴';
      const statusText = d.is_online ? '在线' : '离线';
      const logLevel = d.log_level || 'debug';
      const currentApp = d.current_app || '';
      return `<tr>
        <td><span class="status-dot ${d.is_online ? 'online' : 'offline'}"></span>${statusText}</td>
        <td><strong>${escapeHtml(d.device_name || d.model || '未知设备')}</strong></td>
        <td style="color:#64748b">${escapeHtml(d.model || '')}</td>
        <td style="color:#64748b;font-size:12px">${formatDate(d.last_seen_at)}</td>
        <td style="font-size:12px">${currentApp ? '📱 ' + escapeHtml(currentApp) : '<span style="color:#94a3b8">—</span>'}</td>
        <td>
          <select class="log-level-select" data-id="${escapeHtml(d.id)}" style="padding:4px 6px;border:1px solid var(--border);border-radius:4px;font-size:12px">
            <option value="debug" ${logLevel==='debug'?'selected':''}>debug</option>
            <option value="info" ${logLevel==='info'?'selected':''}>info</option>
            <option value="warn" ${logLevel==='warn'?'selected':''}>warn</option>
            <option value="error" ${logLevel==='error'?'selected':''}>error</option>
          </select>
        </td>
        <td>
          <button class="btn-ghost btn-sm btn-view-apps" data-id="${escapeHtml(d.id)}">应用</button>
          <button class="btn-sm btn-lock" data-id="${escapeHtml(d.id)}" style="padding:4px 8px;font-size:12px;background:#fff;color:#f59e0b;border:1px solid #f59e0b;border-radius:6px;cursor:pointer">锁屏</button>
          <button class="btn-sm btn-danger btn-unbind" data-id="${escapeHtml(d.id)}" data-name="${escapeHtml(d.device_name || d.model || '')}">解绑</button>
        </td>
      </tr>`;
    }).join('');

    listEl.querySelectorAll('.btn-view-apps').forEach(b =>
      b.addEventListener('click', () => { clearInterval(refreshTimer); refreshTimer = null; loadDeviceApps(b.dataset.id); }));
    listEl.querySelectorAll('.btn-unbind').forEach(b =>
      b.addEventListener('click', () => confirmUnbind(b.dataset.id, b.dataset.name)));
    listEl.querySelectorAll('.log-level-select').forEach(sel =>
      sel.addEventListener('change', () => {
        const id = sel.dataset.id;
        const level = sel.value;
        api('PUT', `/devices/${id}/config`, { log_level: level })
          .then(res => { if (res.success) showToast('日志等级已更新: ' + level, 'success');
            else { showToast('更新失败', 'error'); sel.value = sel.getAttribute('data-old') || 'debug'; }
          }).catch(err => showToast(err.message, 'error'));
        sel.setAttribute('data-old', level);
      }));
    listEl.querySelectorAll('.btn-lock').forEach(b =>
      b.addEventListener('click', () => {
        const id = b.dataset.id;
        if (confirm('确定要锁定该设备吗？')) {
          api('POST', `/devices/${id}/lock`).then(res => {
            if (res.success) showToast('锁屏指令已发送', 'success');
            else showToast(res.error || '操作失败', 'error');
          }).catch(err => showToast(err.message, 'error'));
        }
      }));
  });
}

export function bindDevice() {
  const deviceName = prompt('请输入设备名称（例如：小明的手机）');
  if (!deviceName || !deviceName.trim()) return;

  api('POST', '/devices/bind', { device_name: deviceName.trim() }).then(res => {
    if (res.data?.pairing_code) {
      showModal('绑定设备', `
        <p style="margin-bottom:16px">配对码已生成，请在30分钟内完成绑定</p>
        <div style="text-align:center;padding:24px;background:#f8fafc;border-radius:8px;margin-bottom:16px">
          <div style="font-size:12px;color:#64748b;margin-bottom:4px">配对码</div>
          <div style="font-size:42px;font-weight:700;letter-spacing:12px;color:#6366f1;font-family:monospace">${res.data.pairing_code}</div>
        </div>
        <p style="font-size:13px;color:#64748b">在孩子端App中输入此配对码即可完成绑定</p>
        <div class="modal-actions"><button class="btn-primary" onclick="hideModal()">我知道了</button></div>
      `);
      loadDevices();
    } else {
      showToast(res.error || '生成配对码失败', 'error');
    }
  }).catch(err => showToast(err.message, 'error'));
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
