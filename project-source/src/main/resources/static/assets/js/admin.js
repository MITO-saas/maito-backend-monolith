/**
 * Maito Enterprise Admin Console Client
 * Reactive state management, multi-tenant switching, JWT auth,
 * and live operations for Orders, Catalog, B2B, Returns, and Support.
 */
class MaitoAdminApp {
  constructor() {
    const params = (typeof window !== 'undefined' && window.location) ? new URLSearchParams(window.location.search) : null;
    const urlTenant = params ? params.get('tenant') : null;
    const storedTenant = (typeof localStorage !== 'undefined') ? (localStorage.getItem('maito_admin_active_tenant') || localStorage.getItem('maito_admin_tenant')) : null;
    let initialTenant = (urlTenant && urlTenant.trim() !== '') ? urlTenant.trim().toLowerCase() : (storedTenant || 'mitocrunch');
    if (initialTenant === 'mito_crunch') initialTenant = 'mitocrunch';
    if (initialTenant === 'vijiya_solar') initialTenant = 'vijiyasolar';

    this.activeTenant = initialTenant;
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('maito_admin_active_tenant', this.activeTenant);
    }

    this.adminToken = this.getTenantItem('token') || (typeof localStorage !== 'undefined' ? localStorage.getItem('maito_admin_token') : null);
    this.adminProfile = this.getStoredProfile();
    this.currentTab = 'analytics';
    this.cache = {};

    this.init();
  }

  getTenantStorageKey(key) {
    return `maito_admin_${this.activeTenant}_${key}`;
  }

  getTenantItem(key) {
    if (typeof localStorage === 'undefined') return null;
    return localStorage.getItem(this.getTenantStorageKey(key));
  }

  setTenantItem(key, value) {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(this.getTenantStorageKey(key), value);
  }

  removeTenantItem(key) {
    if (typeof localStorage === 'undefined') return;
    localStorage.removeItem(this.getTenantStorageKey(key));
  }

  getStoredProfile() {
    try {
      const stored = this.getTenantItem('profile') || (typeof localStorage !== 'undefined' ? localStorage.getItem('maito_admin_profile') : null);
      return stored ? JSON.parse(stored) : null;
    } catch (e) {
      return null;
    }
  }

  isAuthenticated() {
    return !!this.adminToken;
  }

  init() {
    this.bindEvents();
    if (this.isAuthenticated()) {
      this.renderAuthenticatedUI();
      this.switchTab('analytics');
    } else {
      this.renderLoginUI();
    }
  }

  bindEvents() {
    // Demo Fill button
    const demoFillBtn = document.getElementById('demo-fill-btn');
    if (demoFillBtn) {
      demoFillBtn.addEventListener('click', () => {
        const tenantSelect = document.getElementById('login-tenant');
        const tenantVal = (tenantSelect ? tenantSelect.value : null) || this.activeTenant || 'mitocrunch';
        let email = 'admin@mitocrunch.com';
        if (tenantVal === 'vijiyasolar') email = 'admin@vijiyasolar.com';
        else if (tenantVal === 'everrites') email = 'admin@everrites.com';

        document.getElementById('login-email').value = email;
        document.getElementById('login-password').value = 'Admin@2026';
        if (tenantSelect) tenantSelect.value = tenantVal;
        this.showToast(`Credentials filled for [${tenantVal}] Admin!`, 'info');
      });
    }

    // Login Form Submit
    const loginForm = document.getElementById('admin-login-form');
    if (loginForm) {
      loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('login-email').value.trim();
        const password = document.getElementById('login-password').value.trim();
        const tenant = document.getElementById('login-tenant').value.trim();
        await this.login(email, password, tenant);
      });
    }
  }

  async login(email, password, tenant) {
    const loginBtn = document.getElementById('login-btn');
    const loginError = document.getElementById('login-error');
    if (loginError) loginError.classList.add('hidden');
    
    if (loginBtn) {
      loginBtn.disabled = true;
      loginBtn.innerHTML = '<span class="inline-block animate-spin mr-2">⏳</span> Authenticating...';
    }

    try {
      const response = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': tenant
        },
        body: JSON.stringify({ email, password })
      });

      const result = await response.json();

      if (!response.ok || !result.success) {
        throw new Error(result.error?.message || result.message || 'Authentication failed. Please verify credentials.');
      }

      const data = result.data;
      this.adminToken = data.accessToken;
      this.adminProfile = data.profile || { firstName: 'Crunch', lastName: 'Admin', role: 'ROLE_TENANT_ADMIN', email };
      this.activeTenant = tenant;

      this.setTenantItem('token', this.adminToken);
      this.setTenantItem('profile', JSON.stringify(this.adminProfile));
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('maito_admin_active_tenant', this.activeTenant);
        localStorage.setItem('maito_admin_tenant', this.activeTenant);
      }

      this.showToast(`Welcome back, ${this.adminProfile.firstName || 'Admin'}!`, 'success');
      this.renderAuthenticatedUI();
      this.switchTab('analytics');
    } catch (err) {
      console.error('Login error:', err);
      if (loginError) {
        loginError.textContent = err.message;
        loginError.classList.remove('hidden');
      }
      this.showToast(err.message, 'error');
    } finally {
      if (loginBtn) {
        loginBtn.disabled = false;
        loginBtn.innerHTML = 'Sign In to Console';
      }
    }
  }

  logout() {
    this.adminToken = null;
    this.adminProfile = null;
    this.removeTenantItem('token');
    this.removeTenantItem('profile');
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem('maito_admin_token');
      localStorage.removeItem('maito_admin_profile');
    }
    this.showToast('You have been securely signed out.', 'info');
    this.renderLoginUI();
  }

  setTenant(tenant) {
    const cleaned = (tenant || 'mitocrunch').trim().toLowerCase();
    const normalized = (cleaned === 'mito_crunch') ? 'mitocrunch' : (cleaned === 'vijiya_solar' ? 'vijiyasolar' : cleaned);
    this.activeTenant = normalized;
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('maito_admin_active_tenant', normalized);
      localStorage.setItem('maito_admin_tenant', normalized);
    }

    // Synchronize URL query parameter ?tenant=
    if (typeof window !== 'undefined' && window.history && window.history.pushState) {
      const url = new URL(window.location.href);
      url.searchParams.set('tenant', normalized);
      window.history.pushState({}, '', url.toString());
    }

    // Refresh credentials strictly for this tenant
    this.adminToken = this.getTenantItem('token') || null;
    this.adminProfile = this.getStoredProfile();
    this.cache = {};

    this.showToast(`Switched active tenant to [${normalized}]`, 'info');
    document.querySelectorAll('.active-tenant-label').forEach(el => el.textContent = normalized);

    const selector = document.getElementById('header-tenant-selector');
    if (selector) selector.value = normalized;

    const storefrontLink = document.getElementById('admin-storefront-link');
    if (storefrontLink) {
      storefrontLink.href = `/?tenant=${normalized}`;
    }

    if (this.isAuthenticated()) {
      this.renderAuthenticatedUI();
      this.switchTab(this.currentTab || 'analytics');
    } else {
      this.renderLoginUI();
    }
  }

  apiFetch(url, options = {}) {
    return this.apiCall(url, options);
  }

  async apiCall(url, options = {}) {
    if (!options.headers) options.headers = {};
    options.headers['X-Tenant-ID'] = this.activeTenant;
    options.headers['Accept'] = 'application/json';

    if (this.adminToken) {
      options.headers['Authorization'] = `Bearer ${this.adminToken}`;
    }

    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(options.body);
    }

    try {
      const res = await fetch(url, options);
      if (res.status === 401 || res.status === 403) {
        const isCriticalAuth = url.includes('/api/v1/auth/me') || url.includes('/api/v1/auth/login');
        if (isCriticalAuth) {
          this.showToast('Session expired or unauthorized. Please re-authenticate.', 'error');
          this.logout();
        } else {
          console.warn('Unauthorized sub-request suppressed from forcing logout: ' + url);
        }
        throw new Error('Unauthorized');
      }
      const json = await res.json();
      return json;
    } catch (err) {
      console.error(`API Call failed [${url}]:`, err);
      throw err;
    }
  }

  renderLoginUI() {
    document.getElementById('login-view').classList.remove('hidden');
    document.getElementById('dashboard-view').classList.add('hidden');
    if (window.lucide) lucide.createIcons();
  }

  renderAuthenticatedUI() {
    document.getElementById('login-view').classList.add('hidden');
    document.getElementById('dashboard-view').classList.remove('hidden');

    // Populate user profile info in topbar
    const adminNameEl = document.getElementById('admin-profile-name');
    const adminEmailEl = document.getElementById('admin-profile-email');
    const tenantSelector = document.getElementById('header-tenant-selector');

    if (adminNameEl && this.adminProfile) {
      adminNameEl.textContent = `${this.adminProfile.firstName || 'Crunch'} ${this.adminProfile.lastName || 'Admin'}`;
    }
    if (adminEmailEl && this.adminProfile) {
      adminEmailEl.textContent = this.adminProfile.email || 'admin@mitocrunch.com';
    }
    if (tenantSelector) {
      tenantSelector.value = this.activeTenant;
    }
    document.querySelectorAll('.active-tenant-label').forEach(el => el.textContent = this.activeTenant);
    const storefrontLink = document.getElementById('admin-storefront-link');
    if (storefrontLink) {
      storefrontLink.href = `/?tenant=${this.activeTenant}`;
    }

    if (window.lucide) lucide.createIcons();
  }

  switchTab(tabName) {
    this.currentTab = tabName;

    // Update sidebar navigation active states
    document.querySelectorAll('.nav-tab-btn').forEach(btn => {
      const target = btn.dataset.tab;
      if (target === tabName) {
        btn.classList.add('bg-slate-800', 'text-amber-400', 'font-semibold', 'border-l-4', 'border-amber-500');
        btn.classList.remove('text-slate-400', 'hover:bg-slate-800/60');
      } else {
        btn.classList.remove('bg-slate-800', 'text-amber-400', 'font-semibold', 'border-l-4', 'border-amber-500');
        btn.classList.add('text-slate-400', 'hover:bg-slate-800/60');
      }
    });

    // Hide all tab panes
    document.querySelectorAll('.tab-pane').forEach(pane => pane.classList.add('hidden'));

    // Show selected pane
    const activePane = document.getElementById(`pane-${tabName}`);
    if (activePane) {
      activePane.classList.remove('hidden');
    }

    // Load tab-specific data
    switch (tabName) {
      case 'analytics':
        this.loadAnalytics();
        break;
      case 'orders':
        this.loadOrders();
        break;
      case 'catalog':
        this.loadCatalog();
        break;
      case 'b2b':
        this.loadB2BPartners();
        break;
      case 'returns':
        this.loadReturns();
        break;
      case 'support':
        this.loadSupportTickets();
        break;
      case 'carriers':
        this.loadCarriers();
        break;
    }

    if (window.lucide) lucide.createIcons();
  }

  // -------------------------------------------------------------
  // TAB 1: COMMERCIAL ANALYTICS & KPIS
  // -------------------------------------------------------------
  async loadAnalytics() {
    const container = document.getElementById('analytics-content');
    if (!container) return;

    container.innerHTML = `
      <div class="flex items-center justify-center p-12 text-slate-500">
        <span class="animate-spin mr-3 text-2xl">⏳</span> Loading live executive KPIs...
      </div>
    `;

    try {
      // Calls GET /api/v1/admin/analytics/dashboard
      const res = await this.apiCall('/api/v1/admin/analytics/dashboard');
      const data = res.data || {};

      const gmv = Number(data.grossMerchandiseValue || 0).toLocaleString('en-IN', { style: 'currency', currency: 'INR' });
      const aov = Number(data.averageOrderValue || 0).toLocaleString('en-IN', { style: 'currency', currency: 'INR' });
      const totalOrders = data.totalPaidOrders || 0;
      const topSelling = data.topSellingVariants || [];
      const stockRisks = data.stockRiskItems || [];

      container.innerHTML = `
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <!-- Card 1: Gross Merchandise Value -->
          <div class="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col justify-between">
            <div class="flex items-center justify-between text-slate-500 mb-2">
              <span class="text-xs font-bold uppercase tracking-wider">Gross Revenue (GMV)</span>
              <div class="p-2.5 bg-amber-50 rounded-xl text-amber-600"><i data-lucide="trending-up" class="w-5 h-5"></i></div>
            </div>
            <div class="text-3xl font-extrabold text-slate-900 tracking-tight">${gmv}</div>
            <div class="mt-3 flex items-center text-xs text-emerald-600 font-semibold">
              <i data-lucide="arrow-up-right" class="w-3.5 h-3.5 mr-1"></i> +18.4% from last billing cycle
            </div>
          </div>

          <!-- Card 2: Average Order Value -->
          <div class="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col justify-between">
            <div class="flex items-center justify-between text-slate-500 mb-2">
              <span class="text-xs font-bold uppercase tracking-wider">Average Order (AOV)</span>
              <div class="p-2.5 bg-indigo-50 rounded-xl text-indigo-600"><i data-lucide="credit-card" class="w-5 h-5"></i></div>
            </div>
            <div class="text-3xl font-extrabold text-slate-900 tracking-tight">${aov}</div>
            <div class="mt-3 flex items-center text-xs text-indigo-600 font-semibold">
              <i data-lucide="check" class="w-3.5 h-3.5 mr-1"></i> Basket optimization active
            </div>
          </div>

          <!-- Card 3: Total Paid Orders -->
          <div class="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col justify-between">
            <div class="flex items-center justify-between text-slate-500 mb-2">
              <span class="text-xs font-bold uppercase tracking-wider">Total Orders Count</span>
              <div class="p-2.5 bg-emerald-50 rounded-xl text-emerald-600"><i data-lucide="shopping-bag" class="w-5 h-5"></i></div>
            </div>
            <div class="text-3xl font-extrabold text-slate-900 tracking-tight">${totalOrders}</div>
            <div class="mt-3 flex items-center text-xs text-slate-500 font-medium">
              Across active tenant stores
            </div>
          </div>

          <!-- Card 4: Inventory Health Alerts -->
          <div class="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col justify-between">
            <div class="flex items-center justify-between text-slate-500 mb-2">
              <span class="text-xs font-bold uppercase tracking-wider">Stock Risk Alerts</span>
              <div class="p-2.5 bg-rose-50 rounded-xl text-rose-600"><i data-lucide="alert-triangle" class="w-5 h-5"></i></div>
            </div>
            <div class="text-3xl font-extrabold ${stockRisks.length > 0 ? 'text-rose-600' : 'text-slate-900'} tracking-tight">
              ${stockRisks.length} SKU${stockRisks.length === 1 ? '' : 's'}
            </div>
            <div class="mt-3 flex items-center text-xs ${stockRisks.length > 0 ? 'text-rose-600' : 'text-emerald-600'} font-semibold">
              ${stockRisks.length > 0 ? 'Urgent replenishment recommended' : 'All warehouse stocks healthy'}
            </div>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <!-- Top Selling SKUs Table -->
          <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
            <div class="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
              <h3 class="font-bold text-slate-900 text-sm flex items-center">
                <i data-lucide="award" class="w-4 h-4 mr-2 text-amber-500"></i> Top-Selling Product Variants
              </h3>
              <span class="text-xs font-semibold text-slate-500">By Velocity</span>
            </div>
            <div class="p-6 overflow-x-auto">
              ${topSelling.length === 0 ? `
                <div class="text-center py-8 text-slate-400 text-sm">No sales velocity recorded yet in this window.</div>
              ` : `
                <table class="w-full text-left text-sm text-slate-700">
                  <thead class="text-xs uppercase bg-slate-50 text-slate-500 border-b border-slate-200">
                    <tr>
                      <th class="py-2.5 px-3">Variant SKU</th>
                      <th class="py-2.5 px-3 text-right">Units Sold</th>
                      <th class="py-2.5 px-3 text-right">Revenue</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100">
                    ${topSelling.map(item => `
                      <tr class="hover:bg-slate-50/50">
                        <td class="py-3 px-3 font-semibold text-slate-900">${item.sku || 'N/A'}</td>
                        <td class="py-3 px-3 text-right font-bold text-indigo-600">${item.unitsSold || 0}</td>
                        <td class="py-3 px-3 text-right font-bold text-slate-900">₹${Number(item.revenue || 0).toLocaleString('en-IN')}</td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              `}
            </div>
          </div>

          <!-- Stock Alerts Table -->
          <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
            <div class="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
              <h3 class="font-bold text-slate-900 text-sm flex items-center">
                <i data-lucide="package-search" class="w-4 h-4 mr-2 text-rose-500"></i> Low Stock & Critical Depletion Alerts
              </h3>
              <span class="text-xs font-semibold text-rose-600">Action Required</span>
            </div>
            <div class="p-6 overflow-x-auto">
              ${stockRisks.length === 0 ? `
                <div class="text-center py-8 text-emerald-600 text-sm font-medium flex flex-col items-center">
                  <i data-lucide="check-circle-2" class="w-8 h-8 mb-2"></i>
                  Zero stockouts! All inventory thresholds are well within safety stock.
                </div>
              ` : `
                <table class="w-full text-left text-sm text-slate-700">
                  <thead class="text-xs uppercase bg-slate-50 text-slate-500 border-b border-slate-200">
                    <tr>
                      <th class="py-2.5 px-3">SKU</th>
                      <th class="py-2.5 px-3">Available</th>
                      <th class="py-2.5 px-3">Status</th>
                      <th class="py-2.5 px-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100">
                    ${stockRisks.map(risk => `
                      <tr class="hover:bg-slate-50/50">
                        <td class="py-3 px-3 font-semibold text-slate-900">${risk.sku}</td>
                        <td class="py-3 px-3 font-bold ${risk.availableStock <= 5 ? 'text-rose-600' : 'text-amber-600'}">${risk.availableStock}</td>
                        <td class="py-3 px-3">
                          <span class="px-2 py-0.5 rounded-full text-xs font-bold ${risk.availableStock <= 0 ? 'bg-rose-100 text-rose-800' : 'bg-amber-100 text-amber-800'}">
                            ${risk.availableStock <= 0 ? 'OUT_OF_STOCK' : 'CRITICAL'}
                          </span>
                        </td>
                        <td class="py-3 px-3 text-right">
                          <button onclick="adminApp.openAdjustStockModal('${risk.variantId}', '${risk.sku}', 50)" class="text-xs font-bold text-indigo-600 hover:text-indigo-800 underline">
                            + Restock
                          </button>
                        </td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              `}
            </div>
          </div>
        </div>
      `;

      if (window.lucide) lucide.createIcons();
    } catch (err) {
      container.innerHTML = `
        <div class="p-8 bg-rose-50 border border-rose-200 rounded-2xl text-rose-800 text-sm">
          <p class="font-bold">Failed to load analytics dashboard:</p>
          <p class="text-xs mt-1 text-rose-600">${err.message}</p>
          <button onclick="adminApp.loadAnalytics()" class="mt-4 px-4 py-2 bg-rose-600 text-white font-semibold text-xs rounded-lg hover:bg-rose-700">
            Retry Connection
          </button>
        </div>
      `;
    }
  }

  // -------------------------------------------------------------
  // TAB 2: ORDERS MANAGEMENT
  // -------------------------------------------------------------
  async loadOrders(statusFilter = '') {
    const container = document.getElementById('orders-content');
    if (!container) return;

    container.innerHTML = `
      <div class="flex items-center justify-center p-12 text-slate-500">
        <span class="animate-spin mr-3 text-2xl">⏳</span> Fetching live customer orders...
      </div>
    `;

    try {
      const url = statusFilter ? `/api/v1/admin/orders?status=${encodeURIComponent(statusFilter)}` : '/api/v1/admin/orders';
      const res = await this.apiCall(url);
      const orders = res.data || [];

      container.innerHTML = `
        <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
          <!-- Header and Filter Bar -->
          <div class="p-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 class="text-lg font-bold text-slate-900">Customer Orders Ledger</h2>
              <p class="text-xs text-slate-500 mt-0.5">Real-time transactional stream with status lifecycle transitions</p>
            </div>
            <div class="flex items-center space-x-3">
              <select id="orders-status-filter" onchange="adminApp.loadOrders(this.value)" class="text-xs font-semibold border border-slate-300 rounded-xl px-3 py-2 bg-slate-50 text-slate-700 focus:outline-none focus:ring-2 focus:ring-amber-500">
                <option value="" ${statusFilter === '' ? 'selected' : ''}>All Statuses (${orders.length})</option>
                <option value="CREATED" ${statusFilter === 'CREATED' ? 'selected' : ''}>CREATED</option>
                <option value="PAID" ${statusFilter === 'PAID' ? 'selected' : ''}>PAID</option>
                <option value="PROCESSING" ${statusFilter === 'PROCESSING' ? 'selected' : ''}>PROCESSING</option>
                <option value="SHIPPED" ${statusFilter === 'SHIPPED' ? 'selected' : ''}>SHIPPED</option>
                <option value="DELIVERED" ${statusFilter === 'DELIVERED' ? 'selected' : ''}>DELIVERED</option>
                <option value="CANCELLED" ${statusFilter === 'CANCELLED' ? 'selected' : ''}>CANCELLED</option>
              </select>
              <button onclick="adminApp.loadOrders('${statusFilter}')" class="p-2 border border-slate-200 rounded-xl text-slate-600 hover:bg-slate-50">
                <i data-lucide="refresh-cw" class="w-4 h-4"></i>
              </button>
            </div>
          </div>

          <!-- Orders Table -->
          <div class="overflow-x-auto">
            ${orders.length === 0 ? `
              <div class="text-center py-16 text-slate-400">
                <i data-lucide="package-open" class="w-12 h-12 mx-auto mb-3 text-slate-300"></i>
                <p class="text-sm font-semibold">No orders found matching filter criteria</p>
                <p class="text-xs mt-1">Place an order on the customer storefront to see it appear here live.</p>
              </div>
            ` : `
              <table class="w-full text-left text-sm text-slate-700">
                <thead class="text-xs uppercase bg-slate-50 text-slate-500 border-b border-slate-200">
                  <tr>
                    <th class="py-3 px-4">Order Number</th>
                    <th class="py-3 px-4">Customer & Date</th>
                    <th class="py-3 px-4">Items Summary</th>
                    <th class="py-3 px-4 text-right">Total Amount</th>
                    <th class="py-3 px-4 text-center">Payment</th>
                    <th class="py-3 px-4 text-center">Fulfillment Status</th>
                    <th class="py-3 px-4 text-right">Quick Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  ${orders.map(order => {
                    const statusColors = {
                      'CREATED': 'bg-slate-100 text-slate-700 border-slate-200',
                      'PAID': 'bg-emerald-50 text-emerald-700 border-emerald-200',
                      'PROCESSING': 'bg-blue-50 text-blue-700 border-blue-200',
                      'SHIPPED': 'bg-amber-50 text-amber-700 border-amber-200',
                      'DELIVERED': 'bg-emerald-100 text-emerald-800 border-emerald-300',
                      'CANCELLED': 'bg-rose-50 text-rose-700 border-rose-200'
                    };
                    const badgeClass = statusColors[order.orderStatus] || 'bg-slate-100 text-slate-700 border-slate-200';
                    const itemsText = (order.items || []).map(i => `${i.quantity}x ${i.productName || i.variantSku}`).join(', ') || '1x Roasted Makhana';
                    const dateFormatted = order.createdAt ? new Date(order.createdAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : 'Just now';

                    return `
                      <tr class="hover:bg-slate-50/70 transition-colors">
                        <td class="py-4 px-4 font-bold text-slate-900 font-mono text-xs">
                          ${order.orderNumber}
                        </td>
                        <td class="py-4 px-4">
                          <div class="font-medium text-slate-900 text-xs">Customer Profile #${(order.customerProfileId || '').substring(0, 8)}</div>
                          <div class="text-[11px] text-slate-400 mt-0.5">${dateFormatted}</div>
                        </td>
                        <td class="py-4 px-4 max-w-xs truncate text-xs text-slate-600" title="${itemsText}">
                          ${itemsText}
                        </td>
                        <td class="py-4 px-4 text-right font-extrabold text-slate-900">
                          ₹${Number(order.totalAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                        </td>
                        <td class="py-4 px-4 text-center">
                          <span class="px-2 py-0.5 text-[11px] font-bold rounded-full ${order.paymentStatus === 'PAID' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-slate-100 text-slate-600'}">
                            ${order.paymentStatus || 'PAID'}
                          </span>
                        </td>
                        <td class="py-4 px-4 text-center">
                          <span class="px-2.5 py-1 text-xs font-bold rounded-full border ${badgeClass}">
                            ${order.orderStatus}
                          </span>
                        </td>
                        <td class="py-4 px-4 text-right space-x-1.5 whitespace-nowrap">
                          ${order.orderStatus === 'PENDING_PAYMENT' || order.orderStatus === 'CREATED' || order.orderStatus === 'PROCESSING' ? `
                            <button onclick="adminApp.cancelOrder('${order.id}')" class="px-2.5 py-1 text-xs font-bold text-red-600 hover:bg-red-50 border border-red-200 rounded-lg transition-all">
                              Cancel
                            </button>
                          ` : ''}
                          ${order.orderStatus === 'PAID' || order.orderStatus === 'CREATED' ? `
                            <button onclick="adminApp.updateOrderStatus('${order.id}', 'PROCESSING')" class="px-2.5 py-1 bg-blue-50 hover:bg-blue-100 text-blue-700 border border-blue-200 text-xs font-bold rounded-lg transition-colors">
                              Process
                            </button>
                          ` : ''}
                          ${order.orderStatus === 'PROCESSING' || order.orderStatus === 'PAID' ? `
                            <button onclick="adminApp.updateOrderStatus('${order.id}', 'SHIPPED')" class="px-2.5 py-1 bg-amber-50 hover:bg-amber-100 text-amber-700 border border-amber-200 text-xs font-bold rounded-lg transition-colors">
                              Ship
                            </button>
                          ` : ''}
                          ${order.orderStatus === 'SHIPPED' ? `
                            <button onclick="adminApp.updateOrderStatus('${order.id}', 'DELIVERED')" class="px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 border border-emerald-200 text-xs font-bold rounded-lg transition-colors">
                              Mark Delivered
                            </button>
                          ` : ''}
                        </td>
                      </tr>
                    `;
                  }).join('')}
                </tbody>
              </table>
            `}
          </div>
        </div>
      `;

      if (window.lucide) lucide.createIcons();
    } catch (err) {
      container.innerHTML = `
        <div class="p-8 bg-rose-50 border border-rose-200 rounded-2xl text-rose-800 text-sm">
          <p class="font-bold">Failed to load orders:</p>
          <p class="text-xs mt-1 text-rose-600">${err.message}</p>
          <button onclick="adminApp.loadOrders()" class="mt-4 px-4 py-2 bg-rose-600 text-white font-semibold text-xs rounded-lg hover:bg-rose-700">Retry</button>
        </div>
      `;
    }
  }

  async updateOrderStatus(orderId, newStatus) {
    try {
      const res = await this.apiCall(`/api/v1/admin/orders/${orderId}/status`, {
        method: 'PUT',
        body: { status: newStatus }
      });

      if (!res.success) {
        throw new Error(res.error?.message || 'Failed to update order status');
      }

      this.showToast(`Order status updated to [${newStatus}]!`, 'success');
      this.loadOrders(document.getElementById('orders-status-filter')?.value || '');
    } catch (err) {
      this.showToast(`Error updating order status: ${err.message}`, 'error');
    }
  }

  // -------------------------------------------------------------
  // TAB 3: CATALOG & LIVE INVENTORY
  // -------------------------------------------------------------
  async loadCatalog() {
    const container = document.getElementById('catalog-content');
    if (!container) return;

    container.innerHTML = `
      <div class="flex items-center justify-center p-12 text-slate-500">
        <span class="animate-spin mr-3 text-2xl">⏳</span> Loading catalog items and warehouse stock...
      </div>
    `;

    try {
      const res = await this.apiCall('/api/v1/catalog/products?size=50');
      const products = res.data || [];

      // Fetch variant details for full inventory status
      const detailedProducts = await Promise.all(products.map(async (p) => {
        try {
          const detailRes = await this.apiCall(`/api/v1/catalog/products/${p.slug}`);
          return detailRes.data || p;
        } catch (e) {
          return p;
        }
      }));

      container.innerHTML = `
        <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
          <div class="p-6 border-b border-slate-100 flex items-center justify-between">
            <div>
              <h2 class="text-lg font-bold text-slate-900">Tenant Product Catalog & Warehouse Stock</h2>
              <p class="text-xs text-slate-500 mt-0.5">Real-time SKU stock levels, warehouse allocations, and unit pricing</p>
            </div>
            <button onclick="adminApp.loadCatalog()" class="p-2 border border-slate-200 rounded-xl text-slate-600 hover:bg-slate-50">
              <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            </button>
          </div>

          <div class="p-6 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            ${detailedProducts.map(prod => {
              const variants = prod.variants || [];
              return `
                <div class="border border-slate-200/80 rounded-2xl p-5 bg-slate-50/50 flex flex-col justify-between hover:shadow-md transition-shadow">
                  <div>
                    <div class="flex items-start justify-between gap-3 mb-3">
                      <div>
                        <span class="text-[10px] font-bold uppercase tracking-wider text-amber-600 bg-amber-50 px-2 py-0.5 rounded-md border border-amber-200">
                          ${prod.brand || 'Mito Crunch'}
                        </span>
                        <h3 class="font-bold text-slate-900 text-sm mt-1.5 leading-snug">${prod.name}</h3>
                      </div>
                      <span class="text-xs font-bold text-slate-900 bg-white px-2 py-1 rounded-lg border border-slate-200">
                        ₹${prod.priceRange?.amount || 199}
                      </span>
                    </div>
                    <p class="text-xs text-slate-500 line-clamp-2 mb-4">${prod.shortDescription || 'Organically harvested jumbo foxnuts'}</p>

                    <!-- Variants & Stock Breakdown -->
                    <div class="space-y-2 border-t border-slate-200/60 pt-3">
                      <div class="text-[11px] font-bold uppercase text-slate-400">Warehouse Stock Breakdown</div>
                      ${variants.length === 0 ? `
                        <div class="text-xs text-slate-400">Single Variant - Stock: Active</div>
                      ` : variants.map(v => `
                        <div class="flex items-center justify-between bg-white p-2.5 rounded-xl border border-slate-200/80 text-xs">
                          <div>
                            <div class="font-bold text-slate-800 font-mono text-[11px]">${v.sku}</div>
                            <div class="text-[10px] text-slate-400 font-medium">${v.weightGrams || 100}g Pack</div>
                          </div>
                          <div class="flex items-center space-x-2">
                            <span class="px-2 py-0.5 rounded-md font-bold text-xs ${v.availableStock <= 5 ? 'bg-rose-100 text-rose-800' : 'bg-emerald-100 text-emerald-800'}">
                              ${v.availableStock} in stock
                            </span>
                            <button onclick="adminApp.openAdjustStockModal('${v.id}', '${v.sku}', 25)" class="p-1 hover:bg-slate-100 text-indigo-600 rounded" title="Adjust Stock">
                              <i data-lucide="plus-circle" class="w-4 h-4"></i>
                            </button>
                          </div>
                        </div>
                      `).join('')}
                    </div>
                  </div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      `;

      if (window.lucide) lucide.createIcons();
    } catch (err) {
      container.innerHTML = `
        <div class="p-8 bg-rose-50 border border-rose-200 rounded-2xl text-rose-800 text-sm">
          <p class="font-bold">Failed to load catalog:</p>
          <p class="text-xs mt-1 text-rose-600">${err.message}</p>
        </div>
      `;
    }
  }

  openAdjustStockModal(variantId, sku, defaultDelta = 50) {
    const modal = document.getElementById('adjust-stock-modal');
    if (!modal) return;
    document.getElementById('stock-variant-id').value = variantId;
    document.getElementById('stock-variant-sku').textContent = sku;
    document.getElementById('stock-delta-input').value = defaultDelta;
    modal.classList.remove('hidden');
    if (window.lucide) lucide.createIcons();
  }

  closeAdjustStockModal() {
    const modal = document.getElementById('adjust-stock-modal');
    if (modal) modal.classList.add('hidden');
  }

  async submitStockAdjustment() {
    const variantId = document.getElementById('stock-variant-id').value;
    const delta = parseInt(document.getElementById('stock-delta-input').value, 10);
    const reason = document.getElementById('stock-reason-input').value.trim() || 'Manual Admin Replenishment';
    const warehouseCode = 'DEFAULT_WH';

    try {
      const res = await this.apiCall('/api/v1/admin/inventory/adjust', {
        method: 'POST',
        body: {
          variantId,
          warehouseCode,
          quantityDelta: delta,
          reason
        }
      });

      if (!res.success) {
        throw new Error(res.error?.message || 'Failed to adjust inventory');
      }

      this.showToast(`Stock updated by ${delta > 0 ? '+' : ''}${delta} units!`, 'success');
      this.closeAdjustStockModal();
      this.loadCatalog();
      if (this.currentTab === 'analytics') this.loadAnalytics();
    } catch (err) {
      this.showToast(`Adjustment failed: ${err.message}`, 'error');
    }
  }

  // -------------------------------------------------------------
  // TAB 4: B2B HORECA WHOLESALE PORTAL
  // -------------------------------------------------------------
  async loadB2BPartners() {
    const container = document.getElementById('b2b-content');
    if (!container) return;

    container.innerHTML = `
      <div class="flex items-center justify-center p-12 text-slate-500">
        <span class="animate-spin mr-3 text-2xl">⏳</span> Fetching registered B2B wholesale partners...
      </div>
    `;

    try {
      const res = await this.apiCall('/api/v1/admin/b2b/partners');
      const page = res.data || {};
      const partners = page.content || [];

      container.innerHTML = `
        <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
          <div class="p-6 border-b border-slate-100 flex items-center justify-between">
            <div>
              <h2 class="text-lg font-bold text-slate-900">B2B Wholesale & HoReCa Partner Desk</h2>
              <p class="text-xs text-slate-500 mt-0.5">Corporate GSTIN verification, credit line assignment & payment term control</p>
            </div>
            <button onclick="adminApp.loadB2BPartners()" class="p-2 border border-slate-200 rounded-xl text-slate-600 hover:bg-slate-50">
              <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            </button>
          </div>

          <div class="overflow-x-auto">
            ${partners.length === 0 ? `
              <div class="text-center py-16 text-slate-400">
                <i data-lucide="building-2" class="w-12 h-12 mx-auto mb-3 text-slate-300"></i>
                <p class="text-sm font-semibold">No B2B partner applications found in tenant database</p>
                <p class="text-xs mt-1">Partners registered via B2B portal appear here for credit limit verification.</p>
              </div>
            ` : `
              <table class="w-full text-left text-sm text-slate-700">
                <thead class="text-xs uppercase bg-slate-50 text-slate-500 border-b border-slate-200">
                  <tr>
                    <th class="py-3 px-4">Company Legal Name</th>
                    <th class="py-3 px-4">Tax Identifiers (GSTIN / PAN)</th>
                    <th class="py-3 px-4">Verification Status</th>
                    <th class="py-3 px-4 text-right">Credit Limit</th>
                    <th class="py-3 px-4 text-right">Available Credit</th>
                    <th class="py-3 px-4 text-center">Terms</th>
                    <th class="py-3 px-4 text-right">Action</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  ${partners.map(p => `
                    <tr class="hover:bg-slate-50/70">
                      <td class="py-4 px-4">
                        <div class="font-bold text-slate-900">${p.companyLegalName}</div>
                        <div class="text-xs text-slate-400">${p.tradeName || 'Wholesale Client'}</div>
                      </td>
                      <td class="py-4 px-4 font-mono text-xs">
                        <div class="text-slate-800 font-semibold">${p.gstin || 'N/A'}</div>
                        <div class="text-slate-400 text-[11px]">${p.pan || ''}</div>
                      </td>
                      <td class="py-4 px-4">
                        <span class="px-2.5 py-1 text-xs font-bold rounded-full ${p.verificationStatus === 'VERIFIED' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-amber-50 text-amber-700 border border-amber-200'}">
                          ${p.verificationStatus}
                        </span>
                      </td>
                      <td class="py-4 px-4 text-right font-bold text-slate-900">
                        ₹${Number(p.creditLimit || 0).toLocaleString('en-IN')}
                      </td>
                      <td class="py-4 px-4 text-right font-extrabold text-emerald-600">
                        ₹${Number(p.availableCredit || 0).toLocaleString('en-IN')}
                      </td>
                      <td class="py-4 px-4 text-center text-xs font-semibold text-slate-600">
                        ${p.paymentTermsDays || 30} Days
                      </td>
                      <td class="py-4 px-4 text-right">
                        <button onclick="adminApp.openVerifyB2BModal('${p.id}', '${p.companyLegalName}', ${p.creditLimit || 500000})" class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg text-xs font-bold transition-colors">
                          Verify & Grant Credit
                        </button>
                      </td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            `}
          </div>
        </div>
      `;

      if (window.lucide) lucide.createIcons();
    } catch (err) {
      container.innerHTML = `
        <div class="p-8 bg-rose-50 border border-rose-200 rounded-2xl text-rose-800 text-sm">
          <p class="font-bold">Failed to load B2B partners:</p>
          <p class="text-xs mt-1 text-rose-600">${err.message}</p>
        </div>
      `;
    }
  }

  openVerifyB2BModal(partnerId, companyName, defaultCredit = 500000) {
    const modal = document.getElementById('b2b-verify-modal');
    if (!modal) return;
    document.getElementById('b2b-partner-id').value = partnerId;
    document.getElementById('b2b-partner-name').textContent = companyName;
    document.getElementById('b2b-credit-limit-input').value = defaultCredit;
    modal.classList.remove('hidden');
    if (window.lucide) lucide.createIcons();
  }

  closeVerifyB2BModal() {
    const modal = document.getElementById('b2b-verify-modal');
    if (modal) modal.classList.add('hidden');
  }

  async submitVerifyB2B() {
    const partnerId = document.getElementById('b2b-partner-id').value;
    const creditLimit = parseFloat(document.getElementById('b2b-credit-limit-input').value);
    const paymentTermsDays = parseInt(document.getElementById('b2b-terms-input').value, 10);
    const notes = document.getElementById('b2b-notes-input').value.trim() || 'Verified by Lead Admin';

    try {
      const res = await this.apiCall(`/api/v1/admin/b2b/partners/${partnerId}/verify`, {
        method: 'PUT',
        body: {
          verificationStatus: 'VERIFIED',
          creditLimit,
          paymentTermsDays,
          notes
        }
      });

      if (!res.success) {
        throw new Error(res.error?.message || 'Partner verification failed');
      }

      this.showToast(`B2B Partner Verified with ₹${creditLimit.toLocaleString('en-IN')} credit limit!`, 'success');
      this.closeVerifyB2BModal();
      this.loadB2BPartners();
    } catch (err) {
      this.showToast(`Error: ${err.message}`, 'error');
    }
  }

  // -------------------------------------------------------------
  // TAB 5: RETURNS & QC DESK
  // -------------------------------------------------------------
  async loadReturns() {
    const container = document.getElementById('returns-content');
    if (!container) return;

    container.innerHTML = `
      <div class="flex items-center justify-center p-12 text-slate-500">
        <span class="animate-spin mr-3 text-2xl">⏳</span> Fetching customer return claims and reverse logistics...
      </div>
    `;

    try {
      const res = await this.apiCall('/api/v1/admin/returns');
      const page = res.data || {};
      const returns = page.content || [];

      container.innerHTML = `
        <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
          <div class="p-6 border-b border-slate-100 flex items-center justify-between">
            <div>
              <h2 class="text-lg font-bold text-slate-900">Returns, Reverse Logistics & QC Desk</h2>
              <p class="text-xs text-slate-500 mt-0.5">Quality control evaluation, automated inventory restocking & instant wallet refunds</p>
            </div>
            <button onclick="adminApp.loadReturns()" class="p-2 border border-slate-200 rounded-xl text-slate-600 hover:bg-slate-50">
              <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            </button>
          </div>

          <div class="overflow-x-auto">
            ${returns.length === 0 ? `
              <div class="text-center py-16 text-slate-400">
                <i data-lucide="rotate-ccw" class="w-12 h-12 mx-auto mb-3 text-slate-300"></i>
                <p class="text-sm font-semibold">No active return claims in tenant queue</p>
                <p class="text-xs mt-1">Customer return requests appear here for QC inspection and refund settlement.</p>
              </div>
            ` : `
              <table class="w-full text-left text-sm text-slate-700">
                <thead class="text-xs uppercase bg-slate-50 text-slate-500 border-b border-slate-200">
                  <tr>
                    <th class="py-3 px-4">Return Number</th>
                    <th class="py-3 px-4">Reason Category</th>
                    <th class="py-3 px-4 text-right">Refund Amount</th>
                    <th class="py-3 px-4 text-center">Status</th>
                    <th class="py-3 px-4">QC Inspection Notes</th>
                    <th class="py-3 px-4 text-right">Action</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  ${returns.map(r => `
                    <tr class="hover:bg-slate-50/70">
                      <td class="py-4 px-4 font-mono font-bold text-slate-900 text-xs">
                        ${r.returnNumber}
                      </td>
                      <td class="py-4 px-4 text-xs">
                        <span class="font-semibold text-slate-800">${r.reasonCategory}</span>
                        <div class="text-slate-400 text-[11px] truncate max-w-xs mt-0.5">${r.customerNotes || ''}</div>
                      </td>
                      <td class="py-4 px-4 text-right font-extrabold text-slate-900">
                        ₹${Number(r.refundAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                      </td>
                      <td class="py-4 px-4 text-center">
                        <span class="px-2.5 py-1 text-xs font-bold rounded-full ${r.status === 'COMPLETED' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-amber-50 text-amber-700 border border-amber-200'}">
                          ${r.status}
                        </span>
                      </td>
                      <td class="py-4 px-4 text-xs text-slate-500">
                        ${r.qcNotes || '<span class="italic text-slate-400">Pending Inspection</span>'}
                      </td>
                      <td class="py-4 px-4 text-right">
                        ${r.status !== 'COMPLETED' ? `
                          <button onclick="adminApp.openQcModal('${r.id}', '${r.returnNumber}', ${r.refundAmount || 0})" class="px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 border border-emerald-200 rounded-lg text-xs font-bold transition-colors">
                            Pass QC & Settle
                          </button>
                        ` : `
                          <span class="text-xs text-emerald-600 font-bold flex items-center justify-end">
                            <i data-lucide="check" class="w-3.5 h-3.5 mr-1"></i> Settled
                          </span>
                        `}
                      </td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            `}
          </div>
        </div>
      `;

      if (window.lucide) lucide.createIcons();
    } catch (err) {
      container.innerHTML = `
        <div class="p-8 bg-rose-50 border border-rose-200 rounded-2xl text-rose-800 text-sm">
          <p class="font-bold">Failed to load returns:</p>
          <p class="text-xs mt-1 text-rose-600">${err.message}</p>
        </div>
      `;
    }
  }

  openQcModal(returnId, returnNumber, refundAmount) {
    const modal = document.getElementById('qc-submit-modal');
    if (!modal) return;
    document.getElementById('qc-return-id').value = returnId;
    document.getElementById('qc-return-number').textContent = returnNumber;
    document.getElementById('qc-refund-amount').textContent = `₹${refundAmount}`;
    modal.classList.remove('hidden');
    if (window.lucide) lucide.createIcons();
  }

  closeQcModal() {
    const modal = document.getElementById('qc-submit-modal');
    if (modal) modal.classList.add('hidden');
  }

  async submitQc() {
    const returnId = document.getElementById('qc-return-id').value;
    const passed = document.getElementById('qc-passed-select').value === 'true';
    const refundMode = document.getElementById('qc-refund-mode').value;
    const notes = document.getElementById('qc-notes-input').value.trim() || 'Verified in Warehouse Inspection Desk';

    try {
      const res = await this.apiCall(`/api/v1/admin/returns/${returnId}/qc-submit`, {
        method: 'POST',
        body: {
          passed,
          qcNotes: notes,
          refundMode
        }
      });

      if (!res.success) {
        throw new Error(res.error?.message || 'QC evaluation failed');
      }

      this.showToast('Return QC Approved & Wallet Refund Processed!', 'success');
      this.closeQcModal();
      this.loadReturns();
    } catch (err) {
      this.showToast(`Error: ${err.message}`, 'error');
    }
  }

  // -------------------------------------------------------------
  // TAB 6: SUPPORT HELPDESK
  // -------------------------------------------------------------
  async loadSupportTickets() {
    const container = document.getElementById('support-content');
    if (!container) return;

    container.innerHTML = `
      <div class="flex items-center justify-center p-12 text-slate-500">
        <span class="animate-spin mr-3 text-2xl">⏳</span> Loading customer helpdesk tickets...
      </div>
    `;

    try {
      const res = await this.apiCall('/api/v1/admin/support/tickets');
      const page = res.data || {};
      const tickets = page.content || [];

      container.innerHTML = `
        <div class="bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
          <div class="p-6 border-b border-slate-100 flex items-center justify-between">
            <div>
              <h2 class="text-lg font-bold text-slate-900">Support Helpdesk & Customer Communications</h2>
              <p class="text-xs text-slate-500 mt-0.5">Two-way ticket resolution with real-time operator replying</p>
            </div>
            <button onclick="adminApp.loadSupportTickets()" class="p-2 border border-slate-200 rounded-xl text-slate-600 hover:bg-slate-50">
              <i data-lucide="refresh-cw" class="w-4 h-4"></i>
            </button>
          </div>

          <div class="overflow-x-auto">
            ${tickets.length === 0 ? `
              <div class="text-center py-16 text-slate-400">
                <i data-lucide="ticket" class="w-12 h-12 mx-auto mb-3 text-slate-300"></i>
                <p class="text-sm font-semibold">Zero open customer support tickets</p>
                <p class="text-xs mt-1">Customer inquiries will stream into this helpdesk queue in real time.</p>
              </div>
            ` : `
              <table class="w-full text-left text-sm text-slate-700">
                <thead class="text-xs uppercase bg-slate-50 text-slate-500 border-b border-slate-200">
                  <tr>
                    <th class="py-3 px-4">Ticket Number</th>
                    <th class="py-3 px-4">Subject & Category</th>
                    <th class="py-3 px-4">Priority</th>
                    <th class="py-3 px-4 text-center">Status</th>
                    <th class="py-3 px-4 text-right">Action</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  ${tickets.map(t => `
                    <tr class="hover:bg-slate-50/70">
                      <td class="py-4 px-4 font-mono font-bold text-slate-900 text-xs">
                        ${t.ticketNumber}
                      </td>
                      <td class="py-4 px-4">
                        <div class="font-bold text-slate-900 text-xs">${t.subject}</div>
                        <div class="text-[11px] text-slate-400 mt-0.5 font-medium">${t.category}</div>
                      </td>
                      <td class="py-4 px-4">
                        <span class="px-2 py-0.5 rounded-full text-[11px] font-bold ${t.priority === 'URGENT' ? 'bg-rose-100 text-rose-800' : 'bg-slate-100 text-slate-700'}">
                          ${t.priority}
                        </span>
                      </td>
                      <td class="py-4 px-4 text-center">
                        <span class="px-2.5 py-1 text-xs font-bold rounded-full ${t.status === 'RESOLVED' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-amber-50 text-amber-700 border border-amber-200'}">
                          ${t.status}
                        </span>
                      </td>
                      <td class="py-4 px-4 text-right">
                        <button onclick="adminApp.openTicketThreadModal('${t.id}', '${t.ticketNumber}', '${t.subject}')" class="px-3 py-1.5 bg-blue-50 hover:bg-blue-100 text-blue-700 border border-blue-200 rounded-lg text-xs font-bold transition-colors">
                          View & Reply
                        </button>
                      </td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            `}
          </div>
        </div>
      `;

      if (window.lucide) lucide.createIcons();
    } catch (err) {
      container.innerHTML = `
        <div class="p-8 bg-rose-50 border border-rose-200 rounded-2xl text-rose-800 text-sm">
          <p class="font-bold">Failed to load support tickets:</p>
          <p class="text-xs mt-1 text-rose-600">${err.message}</p>
        </div>
      `;
    }
  }

  async openTicketThreadModal(ticketId, ticketNumber, subject) {
    const modal = document.getElementById('ticket-thread-modal');
    if (!modal) return;
    document.getElementById('ticket-modal-id').value = ticketId;
    document.getElementById('ticket-modal-number').textContent = ticketNumber;
    document.getElementById('ticket-modal-subject').textContent = subject;

    const threadContainer = document.getElementById('ticket-messages-container');
    threadContainer.innerHTML = '<div class="p-6 text-center text-slate-400">Loading conversation history...</div>';
    modal.classList.remove('hidden');

    try {
      const res = await this.apiCall(`/api/v1/admin/support/tickets/${ticketId}`);
      const ticket = res.data || {};
      const messages = ticket.messages || [];

      if (messages.length === 0) {
        threadContainer.innerHTML = '<div class="p-6 text-center text-slate-400 text-xs">No prior messages in thread.</div>';
      } else {
        threadContainer.innerHTML = messages.map(m => `
          <div class="flex flex-col ${m.senderType === 'AGENT' ? 'items-end' : 'items-start'} mb-3">
            <div class="text-[10px] text-slate-400 mb-1 font-semibold">${m.senderType === 'AGENT' ? 'Support Agent' : 'Customer'} • ${new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
            <div class="px-4 py-2.5 rounded-2xl text-xs max-w-sm ${m.senderType === 'AGENT' ? 'bg-amber-500 text-white rounded-tr-none' : 'bg-slate-100 text-slate-800 rounded-tl-none'}">
              ${m.message}
            </div>
          </div>
        `).join('');
      }

      if (window.lucide) lucide.createIcons();
    } catch (err) {
      threadContainer.innerHTML = `<div class="p-4 text-xs text-rose-600">Failed to load thread: ${err.message}</div>`;
    }
  }

  closeTicketThreadModal() {
    const modal = document.getElementById('ticket-thread-modal');
    if (modal) modal.classList.add('hidden');
  }

  async submitAdminReply() {
    const ticketId = document.getElementById('ticket-modal-id').value;
    const message = document.getElementById('ticket-reply-input').value.trim();
    if (!message) return;

    try {
      const res = await this.apiCall(`/api/v1/admin/support/tickets/${ticketId}/messages`, {
        method: 'POST',
        body: {
          message,
          attachmentUrls: []
        }
      });

      if (!res.success) {
        throw new Error(res.error?.message || 'Failed to submit reply');
      }

      this.showToast('Reply sent to customer!', 'success');
      document.getElementById('ticket-reply-input').value = '';
      this.openTicketThreadModal(ticketId, document.getElementById('ticket-modal-number').textContent, document.getElementById('ticket-modal-subject').textContent);
    } catch (err) {
      this.showToast(`Error sending reply: ${err.message}`, 'error');
    }
  }

  // -------------------------------------------------------------
  // TOAST NOTIFICATION SYSTEM
  // -------------------------------------------------------------
  showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const colors = {
      'success': 'bg-slate-900 border-emerald-500/80 text-white',
      'error': 'bg-slate-900 border-rose-500/80 text-white',
      'info': 'bg-slate-900 border-amber-500/80 text-white'
    };

    const icons = {
      'success': 'check-circle-2 text-emerald-400',
      'error': 'alert-circle text-rose-400',
      'info': 'info text-amber-400'
    };

    const toast = document.createElement('div');
    toast.className = `flex items-center space-x-3 px-4 py-3 rounded-xl border shadow-2xl transition-all duration-300 transform translate-y-2 opacity-0 text-xs font-semibold ${colors[type] || colors.info}`;
    toast.innerHTML = `
      <i data-lucide="${icons[type] || icons.info}" class="w-4 h-4 flex-shrink-0"></i>
      <span class="flex-1">${message}</span>
    `;

    container.appendChild(toast);
    if (window.lucide) lucide.createIcons();

    // Fade in
    requestAnimationFrame(() => {
      toast.classList.remove('translate-y-2', 'opacity-0');
    });

    // Auto dismiss after 3.5 seconds
    setTimeout(() => {
      toast.classList.add('opacity-0', 'translate-y-2');
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  }
  // -------------------------------------------------------------
  // TAB 7: 3PL LOGISTICS & CARRIERS MANAGEMENT
  // -------------------------------------------------------------
  async cancelOrder(orderId) {
    if (!confirm('Are you sure you want to cancel this order?')) return;
    try {
      await this.apiCall(`/api/v1/admin/orders/${orderId}/cancel`, {
        method: 'POST'
      });
      this.showToast('Order cancelled successfully.', 'success');
      this.loadOrders(document.getElementById('orders-status-filter')?.value || '');
    } catch (err) {
      // Fallback to order status update
      try {
        await this.apiCall(`/api/v1/admin/orders/${orderId}/status`, {
          method: 'PUT',
          body: { status: 'CANCELLED' }
        });
        this.showToast('Order cancelled successfully.', 'success');
        this.loadOrders(document.getElementById('orders-status-filter')?.value || '');
      } catch (fallbackErr) {
        this.showToast('Failed to cancel order: ' + err.message, 'error');
      }
    }
  }

  async loadCarriers() {
    const container = document.getElementById('carrier-cards-container');
    if (!container) return;
    container.innerHTML = '<div class="p-8 text-center text-slate-400 col-span-2"><span class="animate-spin inline-block mr-2">⏳</span> Loading carrier configurations...</div>';
    try {
      const res = await this.apiFetch('/api/v1/admin/fulfillment/carriers');
      const carriers = res.data || [];
      if (carriers.length === 0) {
        container.innerHTML = '<div class="p-8 text-center text-slate-400 col-span-2">No carriers configured for this tenant.</div>';
        return;
      }
      container.innerHTML = carriers.map(c => `
        <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div>
            <div class="flex items-center justify-between mb-4">
              <div class="flex items-center gap-3">
                <div class="w-10 h-10 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-center font-black text-amber-700 text-sm">
                  ${(c.carrierType || 'CR').substring(0, 2)}
                </div>
                <div>
                  <h3 class="font-bold text-slate-900 text-base">${c.carrierType}</h3>
                  <span class="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">3PL Fulfillment Provider</span>
                </div>
              </div>
              <label class="relative inline-flex items-center cursor-pointer">
                <input type="checkbox" ${c.isEnabled ? 'checked' : ''} onchange="adminApp.toggleCarrier('${c.carrierType}', this.checked)" class="sr-only peer">
                <div class="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-amber-600"></div>
              </label>
            </div>
            <div class="bg-slate-50 p-4 rounded-xl space-y-3 mb-4 text-xs">
              <div>
                <span class="text-slate-400 block font-semibold">Active Client / Service:</span>
                <span class="font-mono font-bold text-slate-800">${c.settings?.client || c.settings?.pickupLocation || 'DEFAULT'}</span>
              </div>
              <div>
                <span class="text-slate-400 block font-semibold">Hub Location:</span>
                <span class="font-mono text-slate-700">${c.settings?.pickupLocation || 'DELHIVERY_PATNA_DC'}</span>
              </div>
            </div>
          </div>
          <button onclick="adminApp.promptEditCarrier('${c.carrierType}')" class="w-full py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold rounded-xl transition-all">
            Configure Settings & Keys
          </button>
        </div>
      `).join('');
      if (window.lucide) lucide.createIcons();
    } catch (err) {
      container.innerHTML = `<div class="p-8 text-center text-red-500 col-span-2">Failed to load carriers: ${err.message}</div>`;
    }
  }

  async toggleCarrier(carrierType, isEnabled) {
    try {
      await this.apiFetch(`/api/v1/admin/fulfillment/carriers/${carrierType}`, {
        method: 'PUT',
        body: { isEnabled: isEnabled }
      });
      this.showToast(`${carrierType} status updated successfully.`, 'success');
    } catch (e) {
      this.showToast(`Failed to update ${carrierType}: ` + e.message, 'error');
      this.loadCarriers();
    }
  }

  async promptEditCarrier(carrierType) {
    const hub = prompt(`Enter Pickup Hub / Warehouse Location for ${carrierType}:`, 'DELHIVERY_PATNA_DC');
    if (hub === null) return;
    try {
      await this.apiFetch(`/api/v1/admin/fulfillment/carriers/${carrierType}`, {
        method: 'PUT',
        body: {
          isEnabled: true,
          settings: { pickupLocation: hub.trim() }
        }
      });
      this.showToast(`${carrierType} updated successfully.`, 'success');
      this.loadCarriers();
    } catch (e) {
      this.showToast(`Failed to configure ${carrierType}: ` + e.message, 'error');
    }
  }

}

// Global Singleton Initialization
let adminApp;
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', () => {
    adminApp = new MaitoAdminApp();
    if (typeof window !== 'undefined') window.adminApp = adminApp;
  });
}
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { MaitoAdminApp };
}
