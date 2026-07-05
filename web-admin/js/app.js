// FamilyGuard Web — 入口
// =====================================
import { setBaseUrl, getBaseUrl } from './api.js';
import { showPage, showToast, hideModal } from './ui.js';
import { login, register, logout, initAuth } from './auth.js';
import { loadDashboard } from './dashboard.js';
import { loadDevices, bindDevice } from './devices.js';
import { loadRules, createRule } from './rules.js';
import { loadEvents } from './events.js';
import { loadLogs } from './logs.js';

document.addEventListener('DOMContentLoaded', () => {
  // --- 配置面板 ---
  const configPanel = document.getElementById('config-panel');
  const serverUrlInput = document.getElementById('server-url');
  if (serverUrlInput) serverUrlInput.value = getBaseUrl();

  document.querySelector('[data-action="toggle-server-config"]')?.addEventListener('click', () => configPanel?.classList.toggle('hidden'));
  document.querySelector('[data-action="save-server-url"]')?.addEventListener('click', () => {
    const url = serverUrlInput?.value.trim();
    if (!url || !url.startsWith('http')) return showToast('请输入有效的服务器地址', 'error');
    setBaseUrl(url);
    showToast('服务器地址已保存', 'success');
    configPanel?.classList.add('hidden');
  });

  // --- 登录/注册 ---
  document.querySelectorAll('.auth-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.auth-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      const loginForm = document.getElementById('form-login');
      const regForm = document.getElementById('form-register');
      if (tab.dataset.tab === 'login') { loginForm?.classList.remove('hidden'); regForm?.classList.add('hidden'); }
      else { loginForm?.classList.add('hidden'); regForm?.classList.remove('hidden'); }
    });
  });

  document.getElementById('form-login')?.addEventListener('submit', e => {
    e.preventDefault();
    login(
      document.getElementById('login-email').value.trim(),
      document.getElementById('login-password').value
    );
  });

  document.getElementById('form-register')?.addEventListener('submit', e => {
    e.preventDefault();
    const pw = document.getElementById('reg-password').value;
    const confirm = document.getElementById('reg-password-confirm').value;
    if (pw !== confirm) return showToast('两次输入的密码不一致', 'error');
    const nickname = document.getElementById('reg-nickname').value.trim();
    if (!nickname) return showToast('请输入昵称', 'error');
    register(
      document.getElementById('reg-email').value.trim(),
      pw,
      nickname
    );
  });

  // --- 侧边栏导航 ---
  document.querySelectorAll('.sidebar-nav .nav-item').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.sidebar-nav .nav-item').forEach(n => n.classList.remove('active'));
      btn.classList.add('active');
      const pageId = btn.dataset.page.replace('page-', '');
      showPage(pageId);
      if (pageId === 'dashboard') loadDashboard();
      else if (pageId === 'devices') loadDevices();
      else if (pageId === 'rules') loadRules();
      else if (pageId === 'events') loadEvents(1);
      else if (pageId === 'logs') loadLogs();
    });
  });

  // --- 退出登录 ---
  document.getElementById('btn-logout')?.addEventListener('click', logout);

  // --- 快捷操作 ---
  document.getElementById('btn-quick-bind')?.addEventListener('click', () => { showPage('devices'); setTimeout(bindDevice, 50); });
  document.getElementById('btn-quick-rule')?.addEventListener('click', () => { showPage('rules'); setTimeout(createRule, 50); });
  document.getElementById('btn-add-device')?.addEventListener('click', bindDevice);
  document.getElementById('btn-add-rule')?.addEventListener('click', createRule);
  document.getElementById('btn-refresh-logs')?.addEventListener('click', loadLogs);
  document.getElementById('log-source')?.addEventListener('change', loadLogs);
  document.getElementById('log-level')?.addEventListener('change', loadLogs);

  // --- Modal ---
  document.getElementById('modal-overlay')?.addEventListener('click', e => { if (e.target === e.currentTarget) hideModal(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') hideModal(); });

  // --- 初始化（检查token决定显示登录页还是后台）---
  initAuth();
});
