// FamilyGuard Web — 认证
// =====================================
import { api, setToken, getBaseUrl } from './api.js';
import { showToast } from './ui.js';
import { loadDashboard } from './dashboard.js';

function showApp() {
  document.getElementById('page-login')?.classList.add('hidden');
  document.getElementById('app-layout')?.classList.remove('hidden');
  document.querySelectorAll('.main-content .page').forEach(p => p.style.display = 'none');
  const dashboard = document.getElementById('page-dashboard');
  if (dashboard) dashboard.style.display = 'block';
  loadDashboard();
}

function showLogin() {
  document.getElementById('app-layout')?.classList.add('hidden');
  document.getElementById('page-login')?.classList.remove('hidden');
  const loginPage = document.getElementById('page-login');
  if (loginPage) loginPage.style.display = 'block';
}

export function login(email, password) {
  api('POST', '/login', { email, password }).then(res => {
    if (res.data?.token) {
      setToken(res.data.token);
      showToast('登录成功', 'success');
      showApp();
    } else {
      showToast(res.error || '登录失败', 'error');
    }
  }).catch(err => showToast(err.message, 'error'));
}

export function register(email, password, nickname) {
  api('POST', '/register', { email, password, nickname }).then(res => {
    if (res.data?.token) {
      setToken(res.data.token);
      showToast('注册成功', 'success');
      showApp();
    } else {
      showToast(res.error || '注册失败', 'error');
    }
  }).catch(err => showToast(err.message, 'error'));
}

export function logout() {
  setToken('');
  showLogin();
}

export function initAuth() {
  const token = localStorage.getItem('token');
  if (token) {
    setToken(token);
    showApp();
  } else {
    showLogin();
  }
}
