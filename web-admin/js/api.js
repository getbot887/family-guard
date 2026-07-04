// FamilyGuard Web — API 客户端
// =====================================

const API_PREFIX = '/api/v1';
const DEFAULT_URL = 'http://192.168.2.169:8080';

let token = localStorage.getItem('token') || '';
let baseUrl = localStorage.getItem('server_url') || DEFAULT_URL;

export function setToken(t) { token = t; if (t) localStorage.setItem('token', t); else localStorage.removeItem('token'); }
export function getToken() { return token; }

export function getBaseUrl() { return baseUrl; }
export function setBaseUrl(url) {
  baseUrl = url.replace(/\/+$/, '');
  localStorage.setItem('server_url', baseUrl);
}

// 首次运行时保存默认地址到 localStorage
if (!localStorage.getItem('server_url')) {
  localStorage.setItem('server_url', baseUrl);
}

export async function api(method, path, body) {
  const url = baseUrl + API_PREFIX + path;
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = 'Bearer ' + token;

  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  if (res.status === 401) { setToken(''); throw new Error('未授权，请重新登录'); }
  return res.json();
}
