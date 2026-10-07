/**
 * Maito Storefront Client State Manager
 * Provides reactive session state, JWT auth management, cart operations,
 * wallet/loyalty coin integration, customer address management, store settings,
 * checkout flow, and fulfillment tracking.
 */
class MaitoStore {
  constructor() {
    this.activeTenant = this.resolveActiveTenant();
    this.cartId = this.getOrCreateCartId();
    this.authToken = localStorage.getItem('maito_auth_token') || null;
    this.userProfile = this.getStoredProfile();
    this.cart = { items: [], subtotalAmount: 0, totalAmount: 0, currencyCode: 'INR' };
    this.appliedCoupon = null;
    this.wallet = { balance: 0.00, currencyCode: 'INR', isActive: true };
    this.coinsToRedeem = 0;
    this.savedAddresses = [];
    this.storeSettings = null;
    this.listeners = [];
  }

  subscribe(listener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  notify(event, data) {
    this.listeners.forEach(fn => {
      try {
        fn(event, data);
      } catch (e) {
        console.error('Store listener error:', e);
      }
    });
  }

  getOrCreateCartId() {
    let id = localStorage.getItem('maito_cart_id');
    if (!id || id === 'null' || id === 'undefined') {
      id = (typeof crypto !== 'undefined' && crypto.randomUUID) 
        ? crypto.randomUUID() 
        : 'guest-' + Math.random().toString(36).substring(2, 15) + '-' + Date.now();
      localStorage.setItem('maito_cart_id', id);
    }
    return id;
  }

  getStoredProfile() {
    try {
      const saved = localStorage.getItem('maito_user_profile');
      if (!saved || saved === 'undefined' || saved === 'null') return null;
      const parsed = JSON.parse(saved);
      return (parsed && typeof parsed === 'object') ? parsed : null;
    } catch (e) {
      return null;
    }
  }

  isAuthenticated() {
    return !!this.authToken && !!this.userProfile;
  }

  async apiFetch(url, options = {}) {
    const headers = {
      'Accept': 'application/json',
      'X-Tenant-ID': this.activeTenant,
      'X-Cart-ID': this.cartId,
      ...(options.headers || {})
    };

    if (this.authToken) {
      headers['Authorization'] = `Bearer ${this.authToken}`;
    }

    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(options.body);
    }

    const response = await fetch(url, { ...options, headers });
    let resData;
    try {
      resData = await response.json();
    } catch (err) {
      throw new Error(`HTTP ${response.status}: Non-JSON response`);
    }

    if (!response.ok) {
      const errMsg = (resData && (resData.message || (resData.error && resData.error.message))) 
        || `Request failed with HTTP ${response.status}`;
      throw new Error(errMsg);
    }

    return resData;
  }

  // --- AUTHENTICATION ---
  async login(email, password) {
    const res = await this.apiFetch('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password }
    });

    if (res.success && res.data) {
      this.authToken = res.data.accessToken;
      this.userProfile = res.data.profile || res.data.userProfile || null;
      if (this.authToken) localStorage.setItem('maito_auth_token', this.authToken);
      if (this.userProfile) {
        localStorage.setItem('maito_user_profile', JSON.stringify(this.userProfile));
      } else {
        localStorage.removeItem('maito_user_profile');
      }

      await this.onAuthSuccess();
      return res.data;
    }
    throw new Error(res.message || 'Login failed');
  }

  async register(firstName, lastName, email, phone, password) {
    const res = await this.apiFetch('/api/v1/auth/register', {
      method: 'POST',
      body: { firstName, lastName, email, phone, password }
    });

    if (res.success && res.data) {
      this.authToken = res.data.accessToken;
      this.userProfile = res.data.profile || res.data.userProfile || null;
      if (this.authToken) localStorage.setItem('maito_auth_token', this.authToken);
      if (this.userProfile) {
        localStorage.setItem('maito_user_profile', JSON.stringify(this.userProfile));
      } else {
        localStorage.removeItem('maito_user_profile');
      }

      await this.onAuthSuccess();
      return res.data;
    }
    throw new Error(res.message || 'Registration failed');
  }

  // --- SOCIAL & OTP AUTHENTICATION ---
  async loginWithSocial(provider, idToken) {
    const res = await this.apiFetch('/api/v1/auth/social-login', {
      method: 'POST',
      body: {
        provider: provider.toUpperCase(),
        idToken,
        guestCartId: this.cartId
      }
    });

    if (res.success && res.data) {
      this.authToken = res.data.accessToken;
      this.userProfile = res.data.profile || res.data.userProfile || null;
      if (this.authToken) localStorage.setItem('maito_auth_token', this.authToken);
      if (this.userProfile) {
        localStorage.setItem('maito_user_profile', JSON.stringify(this.userProfile));
      } else {
        localStorage.removeItem('maito_user_profile');
      }

      await this.onAuthSuccess();
      return res.data;
    }
    throw new Error(res.message || 'Social sign-in failed');
  }

  async loginWithGoogle(credential) {
    return this.loginWithSocial('GOOGLE', credential || 'mock-google-token-customer@mitocrunch.com');
  }

  async loginWithFacebook(credential) {
    return this.loginWithSocial('FACEBOOK', credential || 'mock-facebook-token-customer@mitocrunch.com');
  }

  async sendOtp(phone) {
    const res = await this.apiFetch('/api/v1/auth/otp/send', {
      method: 'POST',
      body: { phone }
    });
    if (res.success && res.data) {
      return res.data;
    }
    throw new Error(res.message || 'Failed to send OTP');
  }

  async verifyOtp(phone, code) {
    const res = await this.apiFetch('/api/v1/auth/otp/verify', {
      method: 'POST',
      body: {
        phone,
        code,
        guestCartId: this.cartId
      }
    });

    if (res.success && res.data) {
      this.authToken = res.data.accessToken;
      this.userProfile = res.data.profile || res.data.userProfile || null;
      if (this.authToken) localStorage.setItem('maito_auth_token', this.authToken);
      if (this.userProfile) {
        localStorage.setItem('maito_user_profile', JSON.stringify(this.userProfile));
      } else {
        localStorage.removeItem('maito_user_profile');
      }

      await this.onAuthSuccess();
      return res.data;
    }
    throw new Error(res.message || 'OTP verification failed');
  }

  async onAuthSuccess() {
    // Attempt to merge guest cart into customer account
    try {
      await this.mergeCart();
    } catch (e) {
      console.warn('Cart merge notice:', e.message);
    }

    await this.fetchCart();
    await this.fetchWalletBalance();
    await this.fetchSavedAddresses();
    this.notify('AUTH_CHANGED', { authenticated: true, user: this.userProfile });
  }

  logout() {
    this.authToken = null;
    this.userProfile = null;
    this.appliedCoupon = null;
    this.savedAddresses = [];
    this.wallet = { balance: 0.00, currencyCode: 'INR', isActive: true };
    this.coinsToRedeem = 0;
    localStorage.removeItem('maito_auth_token');
    localStorage.removeItem('maito_user_profile');
    
    // Fresh guest cart
    localStorage.removeItem('maito_cart_id');
    this.cartId = this.getOrCreateCartId();
    this.cart = { items: [], subtotalAmount: 0, totalAmount: 0, currencyCode: 'INR' };

    this.notify('AUTH_CHANGED', { authenticated: false, user: null });
    this.notify('CART_UPDATED', this.cart);
    this.notify('WALLET_UPDATED', this.wallet);
    this.notify('ADDRESSES_UPDATED', this.savedAddresses);
  }

  // --- WALLET OPERATIONS ---
  async fetchWalletBalance() {
    if (!this.isAuthenticated()) return this.wallet;
    try {
      const res = await this.apiFetch('/api/v1/wallet/balance');
      if (res.success && res.data) {
        this.wallet = res.data;
        this.notify('WALLET_UPDATED', this.wallet);
        return this.wallet;
      }
    } catch (err) {
      console.warn('Fetch wallet balance notice:', err.message);
    }
    return this.wallet;
  }

  setCoinsToRedeem(amount) {
    this.coinsToRedeem = Math.max(0, Number(amount) || 0);
    this.notify('CART_UPDATED', this.cart);
  }

  // --- ADDRESS OPERATIONS ---
  async fetchSavedAddresses() {
    if (!this.isAuthenticated()) {
      this.savedAddresses = [];
      return this.savedAddresses;
    }
    try {
      const res = await this.apiFetch('/api/v1/account/addresses');
      if (res.success && res.data) {
        this.savedAddresses = Array.isArray(res.data) ? res.data : [];
        this.notify('ADDRESSES_UPDATED', this.savedAddresses);
        return this.savedAddresses;
      }
    } catch (err) {
      console.warn('Fetch saved addresses error:', err.message);
    }
    return this.savedAddresses;
  }

  async saveAddress(addressData) {
    if (!this.isAuthenticated()) {
      throw new Error('Please sign in to save an address to your profile');
    }
    const payload = {
      addressType: addressData.addressType || 'SHIPPING',
      recipientName: addressData.recipientName || addressData.name || '',
      phone: addressData.phone || '',
      addressLine1: addressData.addressLine1 || addressData.line1 || addressData.street || '',
      addressLine2: addressData.addressLine2 || addressData.line2 || '',
      city: addressData.city || '',
      state: addressData.state || '',
      postalCode: addressData.postalCode || addressData.pincode || '',
      countryCode: addressData.countryCode || 'IN',
      isDefault: !!addressData.isDefault
    };

    const res = await this.apiFetch('/api/v1/account/addresses', {
      method: 'POST',
      body: payload
    });

    if (res.success && res.data) {
      await this.fetchSavedAddresses();
      return res.data;
    }
    throw new Error(res.message || 'Failed to save address');
  }

  // --- STORE SETTINGS & FORMATTING ---
  async fetchStoreSettings() {
    try {
      const res = await this.apiFetch('/api/v1/store/settings');
      if (res.success && res.data) {
        this.storeSettings = res.data;
        return this.storeSettings;
      }
    } catch (err) {
      console.warn('Fetch store settings notice:', err.message);
    }
    return this.storeSettings;
  }

  formatCurrency(amount) {
    const currency = (this.storeSettings && this.storeSettings.baseCurrency) || 'INR';
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: currency,
      maximumFractionDigits: 2
    }).format(Number(amount) || 0);
  }

  // --- CART OPERATIONS ---
  async fetchCart() {
    try {
      const res = await this.apiFetch('/api/v1/cart');
      if (res.success && res.data) {
        this.cart = res.data;
        this.notify('CART_UPDATED', this.cart);
        return this.cart;
      }
    } catch (err) {
      console.warn('Fetch cart error:', err.message);
    }
    return this.cart;
  }

  async addToCart(variantId, quantity = 1) {
    const res = await this.apiFetch('/api/v1/cart/items', {
      method: 'POST',
      body: { variantId, quantity }
    });

    if (res.success && res.data) {
      this.cart = res.data;
      this.notify('CART_UPDATED', this.cart);
      return this.cart;
    }
    throw new Error(res.message || 'Failed to add item to cart');
  }

  async updateItemQuantity(itemId, quantity) {
    if (quantity <= 0) {
      return this.removeItem(itemId);
    }
    const res = await this.apiFetch(`/api/v1/cart/items/${itemId}`, {
      method: 'PUT',
      body: { quantity }
    });

    if (res.success && res.data) {
      this.cart = res.data;
      this.notify('CART_UPDATED', this.cart);
      return this.cart;
    }
    throw new Error(res.message || 'Failed to update item quantity');
  }

  async removeItem(itemId) {
    const res = await this.apiFetch(`/api/v1/cart/items/${itemId}`, {
      method: 'DELETE'
    });

    if (res.success && res.data) {
      this.cart = res.data;
      this.notify('CART_UPDATED', this.cart);
      return this.cart;
    }
    throw new Error(res.message || 'Failed to remove item');
  }

  async mergeCart() {
    if (!this.isAuthenticated() || !this.cartId) return;
    try {
      const res = await this.apiFetch('/api/v1/cart/merge', {
        method: 'POST',
        body: { guestCartId: this.cartId }
      });
      if (res.success && res.data) {
        this.cart = res.data;
        this.notify('CART_UPDATED', this.cart);
      }
    } catch (e) {
      console.warn('Merge cart silent catch:', e.message);
    }
  }

  async applyCoupon(code) {
    const subtotal = Number(this.cart.subtotalAmount || this.cart.subtotal || 0);
    const res = await this.apiFetch('/api/v1/promotions/apply', {
      method: 'POST',
      body: { code, cartSubtotal: subtotal }
    });

    if (res.success && res.data) {
      if (res.data.applied) {
        this.appliedCoupon = res.data;
        this.notify('COUPON_APPLIED', this.appliedCoupon);
        return this.appliedCoupon;
      } else {
        throw new Error(res.data.message || 'Coupon could not be applied');
      }
    }
    throw new Error(res.message || 'Coupon evaluation failed');
  }

  removeCoupon() {
    this.appliedCoupon = null;
    this.notify('COUPON_REMOVED', null);
  }

  getCartCount() {
    if (!this.cart || !this.cart.items) return 0;
    return this.cart.items.reduce((sum, item) => sum + (item.quantity || 0), 0);
  }

  getCartPayable() {
    const subtotal = Number(this.cart.subtotalAmount || this.cart.subtotal || 0);
    const discount = this.appliedCoupon ? Number(this.appliedCoupon.discountAmount || 0) : 0;
    const freeShipping = this.appliedCoupon ? Number(this.appliedCoupon.freeShippingSavings || 0) > 0 : false;
    const threshold = (this.storeSettings && this.storeSettings.commercialSettings && this.storeSettings.commercialSettings.freeShippingThreshold) 
      ? Number(this.storeSettings.commercialSettings.freeShippingThreshold) 
      : 499;
    const shipping = (subtotal >= threshold || freeShipping) ? 0 : 49;
    const totalBeforeCoins = Math.max(0, subtotal - discount + shipping);
    const coinsRedeemed = Math.min(this.coinsToRedeem, totalBeforeCoins);
    const total = Math.max(0, totalBeforeCoins - coinsRedeemed);
    return { subtotal, discount, shipping, coinsRedeemed, total };
  }

  // --- CHECKOUT & ORDER ---
  async checkout(shippingAddress) {
    if (!this.isAuthenticated()) {
      throw new Error('Please sign in to complete your checkout');
    }

    const payload = {
      shippingAddress,
      couponCode: this.appliedCoupon ? this.appliedCoupon.code : null,
      coinsToRedeem: this.coinsToRedeem
    };

    const res = await this.apiFetch('/api/v1/checkout/create-order', {
      method: 'POST',
      body: payload
    });

    if (res.success && res.data) {
      const order = res.data;

      // Automatically simulate instant mock payment callback
      try {
        await this.simulatePayment(order.id);
        order.orderStatus = 'PAID';
      } catch (payErr) {
        console.warn('Mock payment callback auto-transition note:', payErr.message);
      }

      // Reset cart and coins
      this.appliedCoupon = null;
      this.coinsToRedeem = 0;
      localStorage.removeItem('maito_cart_id');
      this.cartId = this.getOrCreateCartId();
      this.cart = { items: [], subtotalAmount: 0, totalAmount: 0, currencyCode: 'INR' };
      this.notify('CART_UPDATED', this.cart);

      // Refresh wallet balance post-purchase
      await this.fetchWalletBalance();

      return order;
    }
    throw new Error(res.message || 'Order creation failed');
  }

  async simulatePayment(orderId) {
    return await this.apiFetch(`/api/v1/checkout/payment-callback/${orderId}`, {
      method: 'POST',
      body: {
        paymentReference: 'pay_sim_' + Math.random().toString(36).substring(2, 10).toUpperCase(),
        status: 'PAID',
        signature: 'mock_sig_ok'
      }
    });
  }


  // --- ELASTICSEARCH FULL-TEXT SEARCH & AUTOCOMPLETE ---
  async searchProducts(params = {}) {
    const queryParts = [];
    if (params.q) queryParts.push(`q=${encodeURIComponent(params.q)}`);
    if (params.category) queryParts.push(`category=${encodeURIComponent(params.category)}`);
    if (params.brand) queryParts.push(`brand=${encodeURIComponent(params.brand)}`);
    if (params.minPrice !== undefined && params.minPrice !== null && params.minPrice !== '') {
      queryParts.push(`minPrice=${encodeURIComponent(params.minPrice)}`);
    }
    if (params.maxPrice !== undefined && params.maxPrice !== null && params.maxPrice !== '') {
      queryParts.push(`maxPrice=${encodeURIComponent(params.maxPrice)}`);
    }
    if (params.inStock !== undefined && params.inStock !== null) {
      queryParts.push(`inStock=${encodeURIComponent(params.inStock)}`);
    }
    if (params.sort) queryParts.push(`sort=${encodeURIComponent(params.sort)}`);
    if (params.page !== undefined && params.page !== null) queryParts.push(`page=${encodeURIComponent(params.page)}`);
    if (params.size !== undefined && params.size !== null) queryParts.push(`size=${encodeURIComponent(params.size)}`);

    const queryString = queryParts.length > 0 ? `?${queryParts.join('&')}` : '';
    const res = await this.apiFetch(`/api/v1/search/products${queryString}`);
    if (res.success && res.data) {
      return res.data;
    }
    throw new Error(res.message || 'Product search failed');
  }

  async getSearchSuggestions(query) {
    const trimmed = (query || '').trim();
    if (!trimmed) {
      return [];
    }

    if (this._suggestDebounceTimer) {
      clearTimeout(this._suggestDebounceTimer);
    }

    return new Promise((resolve) => {
      this._suggestDebounceTimer = setTimeout(async () => {
        try {
          const res = await this.apiFetch(`/api/v1/search/suggest?q=${encodeURIComponent(trimmed)}`);
          if (res.success && Array.isArray(res.data)) {
            resolve(res.data);
          } else {
            resolve([]);
          }
        } catch (e) {
          console.warn('Autocomplete fetch failed:', e);
          resolve([]);
        }
      }, 250);
    });
  }

  // --- FULFILLMENT TRACKING ---
  async trackOrder(orderNumber) {
    const trimmed = (orderNumber || '').trim();
    if (!trimmed) {
      throw new Error('Please enter a valid order number');
    }

    const res = await this.apiFetch(`/api/v1/fulfillment/track/${encodeURIComponent(trimmed)}`);
    if (res.success && res.data) {
      return res.data;
    }
    throw new Error(res.message || 'Tracking information not found');
  }
}

// Global Singleton Instance
window.store = new MaitoStore();
