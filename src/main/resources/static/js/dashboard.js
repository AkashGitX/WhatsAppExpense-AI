/**
 * BudgetBot AI — Dashboard Page
 * userId is extracted server-side from the JWT — not passed as a query param.
 */
let categoryChartInstance = null;
let trendChartInstance    = null;
let budgetModal           = null;

document.addEventListener('DOMContentLoaded', async () => {
  const user = requireAuth();
  if (!user) return;

  const now = new Date();
  document.getElementById('current-month').textContent =
    now.toLocaleString('default', { month: 'long', year: 'numeric' });

  budgetModal = new bootstrap.Modal(document.getElementById('budgetModal'));

  await Promise.all([loadSummary(), loadRecentExpenses()]);
});

// ── Summary / KPIs ────────────────────────────────────────────────────────────

async function loadSummary() {
  try {
    const res = await api('/expenses/summary');
    if (!res || !res.success) {
      showToast(res?.message || 'Could not load summary', 'warning');
      return;
    }
    const d = res.data;

    document.getElementById('kpi-monthly').textContent   = formatCurrency(d.monthlySpending);
    document.getElementById('kpi-weekly').textContent    = formatCurrency(d.weeklySpending);
    document.getElementById('kpi-daily').textContent     = formatCurrency(d.dailySpending);
    document.getElementById('kpi-expenses-count').textContent =
      `${d.totalExpensesThisMonth} expense(s) this month`;

    renderBudgetKpi(d);
    renderBudgetBar(d);
    renderCategoryChart(d.categoryChart);
    renderTrendChart(d.monthlyTrendChart);

  } catch (e) {
    console.error('Summary load error:', e);
    showToast('Error loading analytics.', 'danger');
  }
}

// ── Budget KPI card ───────────────────────────────────────────────────────────

function renderBudgetKpi(d) {
  const card       = document.getElementById('kpi-budget-card');
  const remaining  = document.getElementById('kpi-remaining');
  const limitLabel = document.getElementById('kpi-budget-limit');

  if (d.budgetNotSet) {
    // No budget set yet
    remaining.textContent  = '–';
    limitLabel.innerHTML   =
      '<a href="javascript:void(0)" onclick="openBudgetModal()" class="small text-success fw-semibold">' +
      '<i class="bi bi-plus-circle me-1"></i>Set your monthly budget</a>';
    card.classList.remove('kpi-red');
    card.classList.add('kpi-green');
    return;
  }

  remaining.textContent = formatCurrency(d.remainingBudget);

  if (d.overBudget) {
    limitLabel.innerHTML = `<span class="text-danger fw-bold">⚠️ ${formatCurrency(d.overBudgetAmount)} over budget!</span>`;
    card.classList.remove('kpi-green');
    card.classList.add('kpi-red');
  } else {
    limitLabel.textContent = `of ${formatCurrency(d.monthlyBudgetLimit)} budget`;
    card.classList.remove('kpi-red');
    card.classList.add('kpi-green');
  }
}

// ── Budget progress bar ───────────────────────────────────────────────────────

function renderBudgetBar(d) {
  const hint  = document.getElementById('budget-not-set-hint');
  const bar   = document.getElementById('budget-bar');
  const label = document.getElementById('budget-pct-label');

  if (d.budgetNotSet) {
    bar.style.width = '0%';
    bar.className   = 'progress-bar bg-secondary';
    label.textContent = 'No budget set';
    hint.classList.remove('d-none');
    return;
  }

  hint.classList.add('d-none');
  const spent = parseFloat(d.monthlySpending) || 0;
  const limit = parseFloat(d.monthlyBudgetLimit) || 1;
  const pct   = Math.min(Math.round((spent / limit) * 100), 100);

  bar.style.width = pct + '%';
  bar.className   = 'progress-bar ' +
    (d.overBudget ? 'bg-danger' : pct >= 80 ? 'bg-warning' : 'bg-success');
  label.textContent = d.overBudget ? '🔴 Over budget!' : `${pct}% used`;
}

// ── Budget modal ──────────────────────────────────────────────────────────────

function openBudgetModal() {
  document.getElementById('budget-input').value = '';
  document.getElementById('budget-error').classList.add('d-none');
  budgetModal.show();
  setTimeout(() => document.getElementById('budget-input').focus(), 300);
}

async function saveBudget() {
  const input  = document.getElementById('budget-input');
  const errEl  = document.getElementById('budget-error');
  const saveBtn = document.getElementById('budget-save-btn');
  const value  = parseFloat(input.value);

  errEl.classList.add('d-none');

  if (!input.value || isNaN(value) || value <= 0) {
    errEl.textContent = 'Please enter a valid budget amount greater than zero.';
    errEl.classList.remove('d-none');
    input.focus();
    return;
  }

  saveBtn.disabled = true;
  saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';

  try {
    const res = await api('/users/budget', {
      method: 'PUT',
      body: JSON.stringify({ budget: value })
    });

    if (res && res.success) {
      budgetModal.hide();
      showToast(`Monthly budget set to ${formatCurrency(value)} ✅`, 'success');
      await loadSummary();
    } else {
      errEl.textContent = res?.message || 'Failed to save budget. Please try again.';
      errEl.classList.remove('d-none');
    }
  } catch (e) {
    console.error('Budget save error:', e);
    errEl.textContent = 'Network error. Please try again.';
    errEl.classList.remove('d-none');
  } finally {
    saveBtn.disabled = false;
    saveBtn.innerHTML = '<i class="bi bi-check-lg me-1"></i>Save Budget';
  }
}

// Allow pressing Enter in the input to save
document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('budget-input')?.addEventListener('keydown', e => {
    if (e.key === 'Enter') saveBudget();
  });
});

// ── Charts ────────────────────────────────────────────────────────────────────

function renderCategoryChart(chart) {
  const ctx = document.getElementById('category-chart');
  if (!chart || !chart.labels || chart.labels.length === 0) {
    ctx.style.display = 'none';
    document.getElementById('category-empty')?.classList.remove('d-none');
    return;
  }
  if (categoryChartInstance) categoryChartInstance.destroy();
  categoryChartInstance = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels:   chart.labels,
      datasets: [{
        data:            chart.data,
        backgroundColor: chart.backgroundColor,
        borderWidth:     2,
        borderColor:     '#fff',
        hoverOffset:     6
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      cutout: '65%',
      plugins: {
        legend: { position: 'bottom', labels: { font: { size: 11 }, padding: 12, boxWidth: 12 } },
        tooltip: { callbacks: { label: ctx => ` ${ctx.label}: ${formatCurrency(ctx.raw)}` } }
      }
    }
  });
}

function renderTrendChart(chart) {
  const ctx = document.getElementById('trend-chart');
  if (!chart || !chart.labels) return;
  if (trendChartInstance) trendChartInstance.destroy();
  trendChartInstance = new Chart(ctx, {
    type: 'bar',
    data: {
      labels:   chart.labels,
      datasets: [{
        label:                'Monthly Spending',
        data:                 chart.data,
        backgroundColor:      'rgba(67,97,238,.15)',
        borderColor:          '#4361ee',
        borderWidth:          2,
        borderRadius:         6,
        hoverBackgroundColor: 'rgba(67,97,238,.35)'
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: true,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: ctx => ` ${formatCurrency(ctx.raw)}` } }
      },
      scales: {
        x: { grid: { display: false }, ticks: { font: { size: 11 } } },
        y: {
          beginAtZero: true,
          grid: { color: '#f0f2f9' },
          ticks: { font: { size: 11 }, callback: v => formatCurrency(v) }
        }
      }
    }
  });
}

// ── Recent Expenses ───────────────────────────────────────────────────────────

async function loadRecentExpenses() {
  const tbody = document.getElementById('recent-expenses-tbody');
  try {
    const res = await api('/expenses', {}, { page: 0, size: 8 });
    if (!res || !res.success || !res.data || res.data.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted">
        No expenses yet. <a href="/expenses/new">Add your first one!</a></td></tr>`;
      return;
    }
    tbody.innerHTML = res.data.slice(0, 8).map(e => `
      <tr>
        <td><span class="text-muted-sm">${formatDate(e.date)}</span></td>
        <td>${categoryBadge(e.category)}</td>
        <td class="text-truncate" style="max-width:180px;">${e.note || '—'}</td>
        <td>${sourceBadge(e.source)}</td>
        <td class="text-end fw-600">${formatCurrency(e.amount)}</td>
      </tr>`).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-3 text-danger">
      Failed to load expenses.</td></tr>`;
  }
}
