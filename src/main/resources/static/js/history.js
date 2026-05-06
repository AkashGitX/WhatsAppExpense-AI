/**
 * BudgetBot AI — Expense History Page
 * userId is extracted server-side from the JWT — not passed as a query param.
 */
let allExpenses = [];

document.addEventListener('DOMContentLoaded', async () => {
  const user = requireAuth();
  if (!user) return;
  await loadExpenses();
});

async function loadExpenses() {
  const tbody = document.getElementById('history-tbody');
  try {
    const res = await api('/expenses');
    if (!res) return; // 401 — logout() already called
    if (!res.success) { showToast(res.message || 'Failed to load', 'danger'); return; }

    allExpenses = res.data || [];
    renderTable(allExpenses);
  } catch(e) {
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-danger">
      Failed to load expenses.</td></tr>`;
  }
}

function renderTable(expenses) {
  const tbody = document.getElementById('history-tbody');

  if (expenses.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-5 text-muted">
      <i class="bi bi-inbox" style="font-size:2rem;"></i>
      <p class="mt-2">No expenses found.</p>
      <a href="/expenses/new" class="btn btn-sm btn-primary">Add First Expense</a>
    </td></tr>`;
    document.getElementById('result-count').textContent = '';
    updateFooter(0, 0);
    return;
  }

  const rows = expenses.map(e => `
    <tr>
      <td><span class="fw-500">${formatDate(e.date)}</span></td>
      <td>${categoryBadge(e.category)}</td>
      <td class="text-truncate" style="max-width:220px;"
          title="${escapeAttr(e.note || '')}">${e.note || '—'}</td>
      <td>${sourceBadge(e.source)}</td>
      <td class="text-end fw-600 text-dark">${formatCurrency(e.amount)}</td>
    </tr>`).join('');
  tbody.innerHTML = rows;

  const total = expenses.reduce((s, e) => s + parseFloat(e.amount), 0);
  document.getElementById('result-count').textContent = `${expenses.length} record(s)`;
  updateFooter(expenses.length, total);
}

function updateFooter(count, total) {
  const footer = document.getElementById('totals-footer');
  if (count === 0) { footer.style.display = 'none'; return; }
  footer.style.display = '';
  document.getElementById('totals-count').textContent  = `${count} transaction(s)`;
  document.getElementById('totals-amount').textContent = 'Total: ' + formatCurrency(total);
}

function applyFilters() {
  const category = document.getElementById('filter-category').value;
  const source   = document.getElementById('filter-source').value;
  const search   = document.getElementById('filter-search').value.toLowerCase();

  const filtered = allExpenses.filter(e => {
    const matchCat    = !category || e.category === category;
    const matchSrc    = !source   || e.source   === source;
    const matchSearch = !search   || (e.note || '').toLowerCase().includes(search)
                                  || e.category.toLowerCase().includes(search);
    return matchCat && matchSrc && matchSearch;
  });

  renderTable(filtered);
}

function resetFilters() {
  document.getElementById('filter-category').value = '';
  document.getElementById('filter-source').value   = '';
  document.getElementById('filter-search').value   = '';
  renderTable(allExpenses);
}

function escapeAttr(s) {
  return String(s).replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}
