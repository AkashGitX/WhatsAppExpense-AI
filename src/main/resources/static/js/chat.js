/**
 * BudgetBot AI — AI Chat Page
 * userId is extracted server-side from the JWT — not passed as a query param.
 */
let currentUser = null;

document.addEventListener('DOMContentLoaded', async () => {
  currentUser = requireAuth();
  if (!currentUser) return;

  await loadChatHistory();

  // Enter to send (Shift+Enter for newline)
  document.getElementById('chat-input').addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
  });
});

async function loadChatHistory() {
  try {
    const res = await api('/chat/history');
    if (!res) return;
    if (res.success && res.data && res.data.length > 0) {
      // Show messages in chronological order (API returns newest-first)
      const ordered = [...res.data].reverse();
      ordered.forEach(item => {
        appendBubble('user', item.question, item.timestamp);
        appendBubble('ai',   item.answer,   item.timestamp);
      });
      document.getElementById('chat-empty')?.remove();
      document.getElementById('clear-btn').style.display = 'inline-block';
      updateHistoryPanel(res.data);
      scrollToBottom();
    }
  } catch(e) {
    console.warn('Could not load chat history:', e);
  }
}

async function sendMessage() {
  const input    = document.getElementById('chat-input');
  const question = input.value.trim();
  if (!question) return;

  const sendBtn = document.getElementById('send-btn');
  input.value   = '';
  sendBtn.disabled = true;

  document.getElementById('chat-empty')?.remove();
  appendBubble('user', question);
  scrollToBottom();

  const typingId = showTyping();

  try {
    const res = await api('/chat/ask', {
      method: 'POST',
      body:   JSON.stringify({ question })
    });

    removeTyping(typingId);

    if (!res) return; // 401 — logout() already called

    if (res.success && res.data) {
      appendBubble('ai', res.data.answer, res.data.timestamp);
      const histRes = await api('/chat/history');
      if (histRes?.success) {
        updateHistoryPanel(histRes.data);
        document.getElementById('clear-btn').style.display = 'inline-block';
      }
    } else {
      appendBubble('ai', res.message || 'Sorry, I could not process that. Please try again.');
    }
  } catch(e) {
    removeTyping(typingId);
    appendBubble('ai', 'Network error. Please check your connection and try again.');
  } finally {
    sendBtn.disabled = false;
    input.focus();
    scrollToBottom();
  }
}

function useSuggestion(el) {
  document.getElementById('chat-input').value = el.textContent.trim();
  document.getElementById('chat-input').focus();
}

function appendBubble(role, text, timestamp) {
  const container = document.getElementById('chat-messages');
  const isUser    = role === 'user';
  const ts        = timestamp ? formatDateTime(timestamp) : new Date().toLocaleTimeString();
  const escaped   = String(text).replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>');

  const div = document.createElement('div');
  div.className = `chat-message ${isUser ? 'user-msg' : 'ai-msg'}`;
  div.innerHTML = `
    <div class="chat-bubble">${escaped}</div>
    <div class="chat-meta">${isUser ? '👤 You' : '🤖 AI'} · ${ts}</div>
  `;
  container.appendChild(div);
}

function showTyping() {
  const id = 'typing-' + Date.now();
  document.getElementById('chat-messages').insertAdjacentHTML('beforeend', `
    <div id="${id}" class="chat-message ai-msg">
      <div class="chat-bubble">
        <div class="typing-indicator"><span></span><span></span><span></span></div>
      </div>
    </div>`);
  scrollToBottom();
  return id;
}

function removeTyping(id)  { document.getElementById(id)?.remove(); }
function scrollToBottom()  {
  const el = document.getElementById('chat-messages');
  el.scrollTop = el.scrollHeight;
}

function updateHistoryPanel(items) {
  const list  = document.getElementById('history-list');
  const empty = document.getElementById('history-empty');
  if (!items || items.length === 0) return;
  empty?.remove();

  list.innerHTML = items.map(item => `
    <div class="border-bottom p-3" style="cursor:pointer;"
         onclick="reuseQuestion('${escapeAttr(item.question)}')">
      <div class="small fw-600 text-truncate">${escapeHtml(item.question)}</div>
      <div class="text-muted-sm mt-1 text-truncate">${escapeHtml(item.answer||'').substring(0,80)}…</div>
      <div class="text-muted-sm">${formatDateTime(item.timestamp)}</div>
    </div>`).join('');
}

function reuseQuestion(q) {
  document.getElementById('chat-input').value = q;
  document.getElementById('chat-input').focus();
}

function clearHistory() {
  if (!confirm('Clear all chat history? This cannot be undone.')) return;
  document.getElementById('chat-messages').innerHTML = `
    <div class="chat-empty" id="chat-empty">
      <i class="bi bi-robot"></i>
      <strong>Chat cleared.</strong>
      <p class="small mt-2">Ask me anything about your expenses.</p>
    </div>`;
  document.getElementById('history-list').innerHTML =
    '<div class="text-center text-muted p-4 small" id="history-empty">No history yet.</div>';
  document.getElementById('clear-btn').style.display = 'none';
}

function escapeHtml(str) { return String(str).replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
function escapeAttr(str) { return String(str).replace(/'/g,'&#39;').replace(/"/g,'&quot;'); }
