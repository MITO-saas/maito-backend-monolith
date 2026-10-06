/**
 * Maito Storefront Client State Manager
 * Provides reactive session state, JWT auth management, cart operations,
 * checkout flow, and fulfillment tracking.
 */
class MaitoStore {
  constructor() {
    this.activeTenant = 'mito_crunch';
    this.cartId = this.getOrCreateCartId();
    this.authToken = localStorage.getItem('maito_auth_token') || null;
    this.userProfile = this.getStoredProfile();
    this.cart = { items: [], subtotalAmount: 0, totalAmount: 0, currencyCode: 'INR' };
    this.appliedCoupon = null;
    this.listeners = [];
  }

  subscribe(listener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  notify(event, data) {
    this.listeners.forEach(fn => fn(event, data));
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
      return saved ? JSON.parse(saved) : null;
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
      this.userProfile = res.data.userProfile;
      localStorage.setItem('maito_auth_token', this.authToken);
      localStorage.setItem('maito_user_profile', JSON.stringify(this.userProfile));

      // Attempt to merge guest cart into customer account
      try {
        await this.mergeCart();
      } catch (e) {
        console.warn('Cart merge notice:', e.message);
      }

      await this.fetchCart();
      this.notify('AUTH_CHANGED', { authenticated: true, user: this.userProfile });
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
      this.userProfile = res.data.userProfile;
      localStorage.setItem('maito_auth_token', this.authToken);
      localStorage.setItem('maito_user_profile', JSON.stringify(this.userProfile));

      try {
        await this.mergeCart();
      } catch (e) {
        console.warn('Cart merge notice:', e.message);
      }

      await this.fetchCart();
      this.notify('AUTH_CHANGED', { authenticated: true, user: this.userProfile });
      return res.data;
    }
    throw new Error(res.message || 'Registration failed');
  }

  logout() {
    this.authToken = null;
    this.userProfile = null;
    this.appliedCoupon = null;
    localStorage.removeItem('maito_auth_token');
    localStorage.removeItem('maito_user_profile');
    
    // Fresh guest cart
    localStorage.removeItem('maito_cart_id');
    this.cartId = this.getOrCreateCartId();
    this.cart = { items: [], subtotalAmount: 0, totalAmount: 0, currencyCode: 'INR' };

    this.notify('AUTH_CHANGED', { authenticated: false, user: null });
    this.notify('CART_UPDATED', this.cart);
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
    const shipping = (subtotal >= 499 || freeShipping) ? 0 : 49;
    const total = Math.max(0, subtotal - discount + shipping);
    return { subtotal, discount, shipping, total };
  }

  // --- CHECKOUT & ORDER ---
  async checkout(shippingAddress) {
    if (!this.isAuthenticated()) {
      throw new Error('Please sign in to complete your checkout');
    }

    const payload = {
      shippingAddress,
      couponCode: this.appliedCoupon ? this.appliedCoupon.code : null
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

      // Reset cart
      this.appliedCoupon = null;
      localStorage.removeItem('maito_cart_id');
      this.cartId = this.getOrCreateCartId();
      this.cart = { items: [], subtotalAmount: 0, totalAmount: 0, currencyCode: 'INR' };
      this.notify('CART_UPDATED', this.cart);

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
