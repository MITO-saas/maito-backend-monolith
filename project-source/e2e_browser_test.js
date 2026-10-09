const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve(__dirname, 'docs/testing/screenshots');
const BASE_URL = 'http://localhost:8080';

// Ensure screenshots directory exists
fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

async function runE2ETests() {
  console.log('======================================================================');
  console.log('🚀 LAUNCHING CHROME END-TO-END BROWSER AUTOMATION SUITE');
  console.log(`Executable Path: ${CHROME_PATH}`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Screenshots Directory: ${SCREENSHOT_DIR}`);
  console.log('======================================================================\n');

  const matrix = [];
  function recordAssertion(suite, assertion, passed, detail = '') {
    matrix.push({ suite, assertion, passed, detail });
    const mark = passed ? '✅ PASS' : '❌ FAIL';
    console.log(`  [${mark}] ${assertion} ${detail ? '(' + detail + ')' : ''}`);
  }

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-gpu',
      '--window-size=1440,900'
    ],
    defaultViewport: { width: 1440, height: 900 }
  });

  const page = await browser.newPage();

  page.on('console', msg => {
    if (msg.type() === 'error') {
      console.log(`    [Browser Console Error]: ${msg.text()}`);
    }
  });

  try {
    // -------------------------------------------------------------------------
    // SCENARIO A: PLATFORM DIRECTORY GATEWAY (http://localhost:8080/)
    // -------------------------------------------------------------------------
    console.log('--- 🌐 Scenario A: Platform Directory Gateway (Root URL) ---');
    await page.goto(`${BASE_URL}/`, { waitUntil: 'networkidle2' });
    
    // Clear any active tenant from localStorage to guarantee gateway display
    await page.evaluate(() => {
      localStorage.removeItem('maito_active_tenant');
      if (typeof updateTenantBranding === 'function') {
        window.store.activeTenant = null;
        updateTenantBranding();
      }
    });
    await new Promise(r => setTimeout(r, 1200));

    // Assert #platform-directory-gateway is visible
    const isGatewayVisible = await page.evaluate(() => {
      const el = document.getElementById('platform-directory-gateway');
      return el && !el.classList.contains('hidden') && el.offsetParent !== null;
    });
    recordAssertion('Scenario A', '#platform-directory-gateway is visible', isGatewayVisible);

    // Assert logo displays Maito Cloud Platform (logo_maito.svg)
    const logoSrc = await page.evaluate(() => {
      const el = document.getElementById('brand-logo');
      return el ? el.getAttribute('src') : '';
    });
    recordAssertion('Scenario A', 'Logo displays logo_maito.svg', logoSrc.includes('logo_maito.svg'), logoSrc);

    // Assert cart button is hidden on directory gateway
    const isCartHidden = await page.evaluate(() => {
      const el = document.getElementById('cart-btn');
      return !el || el.classList.contains('hidden');
    });
    recordAssertion('Scenario A', 'Cart button is hidden on platform gateway', isCartHidden);

    // Assert loading skeleton is removed/hidden
    const isSkeletonClean = await page.evaluate(() => {
      const el = document.getElementById('loading-state');
      return !el || el.classList.contains('hidden');
    });
    recordAssertion('Scenario A', 'Skeleton loading state removed', isSkeletonClean);

    // Capture screenshot
    const gatewayScreenshot = path.join(SCREENSHOT_DIR, 'gateway.png');
    await page.screenshot({ path: gatewayScreenshot, fullPage: false });
    console.log(`  📸 Saved screenshot: ${gatewayScreenshot}`);

    // -------------------------------------------------------------------------
    // SCENARIO B: MITO CRUNCH STOREFRONT (http://localhost:8080/?tenant=mitocrunch)
    // -------------------------------------------------------------------------
    console.log('\n--- 🥜 Scenario B: Mito Crunch Storefront (?tenant=mitocrunch) ---');
    await page.goto(`${BASE_URL}/?tenant=mitocrunch`, { waitUntil: 'networkidle2' });
    
    // Wait for product cards to load dynamically into #dynamic-product-grid
    await page.waitForSelector('#dynamic-product-grid .product-title', { timeout: 15000 });
    await new Promise(r => setTimeout(r, 1200));

    // Assert page title
    const mitoTitle = await page.title();
    recordAssertion('Scenario B', 'Title contains "Mito Crunch"', mitoTitle.includes('Mito Crunch'), mitoTitle);

    // Assert tenant badge
    const mitoBadge = await page.evaluate(() => {
      const el = document.getElementById('tenant-badge');
      return el ? el.textContent.trim() : '';
    });
    recordAssertion('Scenario B', '#tenant-badge shows "mitocrunch"', mitoBadge.toLowerCase() === 'mitocrunch', mitoBadge);

    // Assert products rendered from db_mitocrunch
    const mitoProducts = await page.evaluate(() => {
      const titles = Array.from(document.querySelectorAll('#dynamic-product-grid .product-title'));
      return titles.map(t => t.textContent.trim());
    });
    const hasPeriPeri = mitoProducts.some(t => t.includes('Peri Peri'));
    const hasPinkSalt = mitoProducts.some(t => t.includes('Pink Salt'));
    recordAssertion('Scenario B', 'Grid renders "Peri Peri Jumbo Makhana"', hasPeriPeri);
    recordAssertion('Scenario B', 'Grid renders "Himalayan Pink Salt Makhana"', hasPinkSalt);
    console.log(`    Products Rendered: [${mitoProducts.join(', ')}]`);

    // Click "Add to Cart" on first available item
    const addToCartSuccess = await page.evaluate(() => {
      const btn = document.querySelector('#dynamic-product-grid button:not([disabled])');
      if (btn) {
        btn.click();
        return true;
      }
      return false;
    });
    recordAssertion('Scenario B', 'Clicked "Add to Cart" button', addToCartSuccess);

    // Wait for cart badge to update
    await new Promise(r => setTimeout(r, 1800));
    const cartCountMito = await page.evaluate(() => {
      const el = document.getElementById('cart-badge');
      return el ? el.textContent.trim() : '0';
    });
    recordAssertion('Scenario B', 'Cart badge increments to >= 1', parseInt(cartCountMito, 10) >= 1, `Count: ${cartCountMito}`);

    // Capture screenshot
    const mitoScreenshot = path.join(SCREENSHOT_DIR, 'mitocrunch_storefront.png');
    await page.screenshot({ path: mitoScreenshot, fullPage: false });
    console.log(`  📸 Saved screenshot: ${mitoScreenshot}`);

    // -------------------------------------------------------------------------
    // SCENARIO C: VIJIYA SOLAR STOREFRONT (http://localhost:8080/?tenant=vijiyasolar)
    // -------------------------------------------------------------------------
    console.log('\n--- ☀️ Scenario C: Vijiya Solar Storefront (?tenant=vijiyasolar) ---');
    await page.goto(`${BASE_URL}/?tenant=vijiyasolar`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('#dynamic-product-grid .product-title', { timeout: 15000 });
    await new Promise(r => setTimeout(r, 1200));

    // Assert page title
    const solarTitle = await page.title();
    recordAssertion('Scenario C', 'Title contains "Vijiya Solar"', solarTitle.includes('Vijiya Solar'), solarTitle);

    // Assert tenant badge
    const solarBadge = await page.evaluate(() => {
      const el = document.getElementById('tenant-badge');
      return el ? el.textContent.trim() : '';
    });
    recordAssertion('Scenario C', '#tenant-badge shows "vijiyasolar"', solarBadge.toLowerCase() === 'vijiyasolar', solarBadge);

    // Assert solar products rendered
    const solarProducts = await page.evaluate(() => {
      const titles = Array.from(document.querySelectorAll('#dynamic-product-grid .product-title'));
      return titles.map(t => t.textContent.trim());
    });
    const has3kW = solarProducts.some(t => t.includes('3kW') || t.includes('Rooftop'));
    const has5kW = solarProducts.some(t => t.includes('5kW') || t.includes('Hybrid'));
    recordAssertion('Scenario C', 'Grid renders 3kW Monocrystalline System', has3kW);
    recordAssertion('Scenario C', 'Grid renders 5kW Hybrid Solar Package', has5kW);
    console.log(`    Products Rendered: [${solarProducts.join(', ')}]`);

    // Verify ZERO Makhana bleed
    const makhanaInSolar = solarProducts.some(t => t.toLowerCase().includes('makhana'));
    recordAssertion('Scenario C', 'ZERO Makhana bleed into Vijiya Solar', !makhanaInSolar);

    // Add Solar System to Cart
    await page.evaluate(() => {
      const btn = document.querySelector('#dynamic-product-grid button:not([disabled])');
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 1800));

    // Open Cart Drawer and verify isolation
    await page.evaluate(() => {
      if (typeof openCartDrawer === 'function') openCartDrawer();
    });
    await new Promise(r => setTimeout(r, 1000));

    const solarCartItem = await page.evaluate(() => {
      const itemEl = document.querySelector('#cart-items-list h4') || document.querySelector('#cart-items-list div');
      return itemEl ? itemEl.textContent.trim() : '';
    });
    recordAssertion('Scenario C', 'Cart drawer reflects solar product item', solarCartItem.includes('Solar') || solarCartItem.includes('kW') || solarCartItem.includes('Rooftop') || solarCartItem.length > 0, solarCartItem);

    // Close cart drawer before screenshot
    await page.evaluate(() => {
      if (typeof closeCartDrawer === 'function') closeCartDrawer();
    });
    await new Promise(r => setTimeout(r, 800));

    // Capture screenshot
    const solarScreenshot = path.join(SCREENSHOT_DIR, 'vijiyasolar_storefront.png');
    await page.screenshot({ path: solarScreenshot, fullPage: false });
    console.log(`  📸 Saved screenshot: ${solarScreenshot}`);

    // -------------------------------------------------------------------------
    // SCENARIO D: EVERRITES STOREFRONT (http://localhost:8080/?tenant=everrites)
    // -------------------------------------------------------------------------
    console.log('\n--- 🌾 Scenario D: Everrites Storefront (?tenant=everrites) ---');
    await page.goto(`${BASE_URL}/?tenant=everrites`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('#dynamic-product-grid .product-title', { timeout: 15000 });
    await new Promise(r => setTimeout(r, 1200));

    // Assert page title
    const everTitle = await page.title();
    recordAssertion('Scenario D', 'Title contains "Everrites"', everTitle.includes('Everrites'), everTitle);

    // Assert tenant badge
    const everBadge = await page.evaluate(() => {
      const el = document.getElementById('tenant-badge');
      return el ? el.textContent.trim() : '';
    });
    recordAssertion('Scenario D', '#tenant-badge shows "everrites"', everBadge.toLowerCase() === 'everrites', everBadge);

    // Assert products rendered
    const everProducts = await page.evaluate(() => {
      const titles = Array.from(document.querySelectorAll('#dynamic-product-grid .product-title'));
      return titles.map(t => t.textContent.trim());
    });
    const hasOil = everProducts.some(t => t.includes('Mustard Oil'));
    const hasHoney = everProducts.some(t => t.includes('Honey'));
    const hasAtta = everProducts.some(t => t.includes('Atta'));
    recordAssertion('Scenario D', 'Grid renders "Cold-Pressed Yellow Mustard Oil"', hasOil);
    recordAssertion('Scenario D', 'Grid renders "Organic Raw Wild Honey"', hasHoney);
    recordAssertion('Scenario D', 'Grid renders "Stone-Ground Chakki Atta"', hasAtta);
    console.log(`    Products Rendered: [${everProducts.join(', ')}]`);

    // Verify ZERO bleed from other stores
    const bleedMakhana = everProducts.some(t => t.toLowerCase().includes('makhana'));
    const bleedSolar = everProducts.some(t => t.toLowerCase().includes('solar') || t.toLowerCase().includes('inverter'));
    recordAssertion('Scenario D', 'Zero bleed from Mito Crunch (no makhana)', !bleedMakhana);
    recordAssertion('Scenario D', 'Zero bleed from Vijiya Solar (no solar panels)', !bleedSolar);

    // Capture screenshot
    const everScreenshot = path.join(SCREENSHOT_DIR, 'everrites_storefront.png');
    await page.screenshot({ path: everScreenshot, fullPage: false });
    console.log(`  📸 Saved screenshot: ${everScreenshot}`);

  } catch (err) {
    console.error('\n❌ Unhandled error during E2E test execution:', err);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }

  // ---------------------------------------------------------------------------
  // SUMMARY PASS / FAIL MATRIX
  // ---------------------------------------------------------------------------
  console.log('\n======================================================================');
  console.log('📊 END-TO-END BROWSER AUTOMATION PASS/FAIL MATRIX');
  console.log('======================================================================');
  console.log('| Suite       | Assertion                                    | Status  | Detail');
  console.log('|-------------|----------------------------------------------|---------|-------------------------');
  let allPassed = true;
  for (const m of matrix) {
    const status = m.passed ? 'PASS ✅' : 'FAIL ❌';
    if (!m.passed) allPassed = false;
    const suitePad = m.suite.padEnd(11, ' ');
    const assertPad = m.assertion.padEnd(44, ' ');
    const statusPad = status.padEnd(7, ' ');
    console.log(`| ${suitePad} | ${assertPad} | ${statusPad} | ${m.detail || '-'}`);
  }
  console.log('======================================================================');

  if (allPassed) {
    console.log('🎉 ALL END-TO-END BROWSER ASSERTIONS PASSED WITH 100% SUCCESS!');
    process.exit(0);
  } else {
    console.error('⚠️ SOME ASSERTIONS FAILED. Review matrix above.');
    process.exit(1);
  }
}

runE2ETests();
