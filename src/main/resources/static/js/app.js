/**
 * BudgetBot AI — Shared Utilities
 * Loaded on every page.
 */

// ── Storage keys ────────────────────────────────────────────────────────────
const BB_USER_KEY  = 'budgetbot_user';
const BB_TOKEN_KEY = 'budgetbot_token';
const CATEGORIES   = ['Food','Transport','Shopping','Bills','Entertainment','Health','Education','Others'];

// ── Auth helpers ─────────────────────────────────────────────────────────────

/** Returns the stored user object or null. */
function getUser() {
  try { return JSON.parse(localStorage.getItem(BB_USER_KEY)); }
  catch { return null; }
}

/** Returns the stored JWT token or null. */
function getToken() {
  return localStorage.getItem(BB_TOKEN_KEY);
}

/** Saves user profile to localStorage. */
function saveUser(user) {
  localStorage.setItem(BB_USER_KEY, JSON.stringify(user));
}

/** Saves JWT token to localStorage. */
function saveToken(token) {
  if (token) localStorage.setItem(BB_TOKEN_KEY, token);
}

/** Clears user + token and redirects to login. */
function logout() {
  localStorage.removeItem(BB_USER_KEY);
  localStorage.removeItem(BB_TOKEN_KEY);
  window.location.href = '/login';
}

/**
 * Call on every protected page.
 * Redirects to /login if no valid session exists.
 * Fills sidebar user info if logged in.
 */
function requireAuth() {
  const user  = getUser();
  const token = getToken();
  if (!user || !user.userId || !token) {
    window.location.href = '/login';
    return null;
  }
  populateSidebarUser(user);
  return user;
}

function populateSidebarUser(user) {
  const nameEl   = document.getElementById('sidebar-user-name');
  const emailEl  = document.getElementById('sidebar-user-email');
  const avatarEl = document.getElementById('sidebar-user-avatar');
  const imgEl    = document.getElementById('sidebar-user-img');

  if (nameEl)   nameEl.textContent  = user.name  || 'User';
  if (emailEl)  emailEl.textContent = user.email || '';
  if (avatarEl) avatarEl.textContent = (user.name || 'U').charAt(0).toUpperCase();

  if (imgEl) {
    if (user.profileImageUrl) {
      imgEl.src          = user.profileImageUrl;
      imgEl.style.display = 'block';
      if (avatarEl) avatarEl.style.display = 'none';
      imgEl.onerror = () => {
        imgEl.style.display    = 'none';
        if (avatarEl) avatarEl.style.display = '';
      };
    } else {
      imgEl.style.display    = 'none';
      if (avatarEl) avatarEl.style.display = '';
    }
  }
}

// ── Fetch wrapper ─────────────────────────────────────────────────────────────

/**
 * Lightweight fetch wrapper that automatically attaches the JWT Bearer token.
 *
 * @param {string} path   - relative URL, e.g. '/expenses'
 * @param {object} opts   - standard fetch options (method, body, etc.)
 * @param {object} params - extra query params (object) — for non-auth params only
 * @returns {Promise<object>} parsed JSON response
 */
async function api(path, opts = {}, params = {}) {
  const url = new URL(path, window.location.origin);
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null) url.searchParams.set(k, v);
  });

  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { 'Authorization': 'Bearer ' + token } : {})
  };

  const res = await fetch(url.toString(), { ...opts, headers: { ...headers, ...(opts.headers || {}) } });

  // Token expired or invalid — force re-login
  if (res.status === 401) {
    logout();
    return null;
  }

  const data = await res.json();
  return data;
}

// ── Toast notifications ────────────────────────────────────────────────────

function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container')
    || createToastContainer();

  const id   = 'toast-' + Date.now();
  const icon = type === 'success' ? 'check-circle-fill'
             : type === 'danger'  ? 'x-circle-fill'
             : 'info-circle-fill';

  container.insertAdjacentHTML('beforeend', `
    <div id="${id}" class="toast align-items-center text-bg-${type} border-0 show" role="alert">
      <div class="d-flex">
        <div class="toast-body">
          <i class="bi bi-${icon} me-2"></i>${message}
        </div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto"
                data-bs-dismiss="toast"></button>
      </div>
    </div>`);

  setTimeout(() => document.getElementById(id)?.remove(), 4000);
}

function createToastContainer() {
  const el = document.createElement('div');
  el.id = 'toast-container';
  el.className = 'toast-container position-fixed top-0 end-0 p-3';
  document.body.appendChild(el);
  return el;
}

// ── Number / date formatters ───────────────────────────────────────────────

function formatCurrency(amount, symbol = '₹') {
  if (amount == null) return symbol + '0';
  return symbol + Number(amount).toLocaleString('en-IN', {
    minimumFractionDigits: 0, maximumFractionDigits: 2
  });
}

function formatDate(dateStr) {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatDateTime(dtStr) {
  if (!dtStr) return '-';
  const d = new Date(dtStr);
  return d.toLocaleString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}

function categoryBadge(cat) {
  return `<span class="badge-category badge-${cat || 'Others'}">${cat || 'Others'}</span>`;
}

function sourceBadge(src) {
  const cls  = src === 'WHATSAPP' ? 'badge-whatsapp' : 'badge-web';
  const icon = src === 'WHATSAPP' ? '📱' : '🌐';
  return `<span class="badge-category ${cls}">${icon} ${src}</span>`;
}

// ── Active nav highlight ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  const path = window.location.pathname;
  document.querySelectorAll('.sidebar-nav a').forEach(a => {
    if (a.getAttribute('href') === path) a.classList.add('active');
  });

  // Logout button
  document.getElementById('logout-btn')?.addEventListener('click', e => {
    e.preventDefault();
    logout();
  });
});
