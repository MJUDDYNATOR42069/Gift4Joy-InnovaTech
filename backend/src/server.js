require('dotenv').config();
const crypto = require('crypto');
const dns = require('dns').promises;
const fs = require('fs');
const path = require('path');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const multer = require('multer');
const rateLimit = require('express-rate-limit');
const admin = require('firebase-admin');

const app = express();
const port = Number(process.env.PORT || 3000);
const publicBaseUrl = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
const sandbox = (process.env.PAYFAST_ENV || 'sandbox').toLowerCase() !== 'live';
const payfastHost = sandbox ? 'sandbox.payfast.co.za' : 'www.payfast.co.za';
const payfastProcessUrl = `https://${payfastHost}/eng/process`;
const imageRoot = path.resolve(process.env.IMAGE_STORAGE_DIR || './storage/product-images');
fs.mkdirSync(imageRoot, { recursive: true });

if (!admin.apps.length) admin.initializeApp({ credential: admin.credential.applicationDefault() });
const db = admin.firestore();

app.disable('x-powered-by');
if (process.env.TRUST_PROXY === '1') app.set('trust proxy', 1);
app.use(helmet({ contentSecurityPolicy: false }));
const allowedOrigins = (process.env.CORS_ORIGINS || '').split(',').map(v => v.trim()).filter(Boolean);
app.use(cors({ origin: (origin, cb) => cb(null, !origin || allowedOrigins.length === 0 || allowedOrigins.includes(origin)) }));
app.use(express.json({ limit: '256kb' }));
app.use('/uploads', express.static(imageRoot, { fallthrough: false, maxAge: '7d', immutable: true }));
app.use('/api/', rateLimit({ windowMs: 60_000, limit: 90, standardHeaders: 'draft-7', legacyHeaders: false }));

const upload = multer({
  storage: multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, imageRoot),
    filename: (req, file, cb) => {
      const ext = ({ 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' })[file.mimetype];
      if (!ext) return cb(new Error('Only JPEG, PNG and WebP images are supported'));
      const id = String(req.params.productId || '').replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 100);
      cb(null, `${id}_${crypto.randomUUID()}${ext}`);
    }
  }),
  limits: { fileSize: 8 * 1024 * 1024, files: 1 },
  fileFilter: (_req, file, cb) => cb(null, ['image/jpeg', 'image/png', 'image/webp'].includes(file.mimetype))
});

async function authenticate(req, res, next) {
  try {
    const match = /^Bearer\s+(.+)$/i.exec(req.headers.authorization || '');
    if (!match) return res.status(401).json({ error: 'Authentication required' });
    req.user = await admin.auth().verifyIdToken(match[1]);
    next();
  } catch (_e) { res.status(401).json({ error: 'Invalid or expired Firebase token' }); }
}

async function requireOwner(req, res, next) {
  try {
    const snap = await db.collection('users').doc(req.user.uid).get();
    if (!snap.exists || snap.get('role') !== 'owner') return res.status(403).json({ error: 'Owner access required' });
    next();
  } catch (_e) { res.status(503).json({ error: 'Could not verify owner permissions' }); }
}

function encodePayFast(value) { return encodeURIComponent(String(value).trim()).replace(/%20/g, '+').replace(/[!'()*~]/g, c => `%${c.charCodeAt(0).toString(16).toUpperCase()}`); }
function signatureFor(fields, passphrase) {
  const parts = Object.entries(fields).filter(([, v]) => v !== '' && v !== null && v !== undefined)
    .map(([k, v]) => `${k}=${encodePayFast(v)}`);
  if (passphrase) parts.push(`passphrase=${encodePayFast(passphrase)}`);
  return crypto.createHash('md5').update(parts.join('&')).digest('hex');
}
function safeEqual(a, b) {
  const aa = Buffer.from(String(a || ''), 'utf8'); const bb = Buffer.from(String(b || ''), 'utf8');
  return aa.length === bb.length && crypto.timingSafeEqual(aa, bb);
}
function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function checkoutHtml(fields, signature) {
  const inputs = { ...fields, signature };
  const controls = Object.entries(inputs).map(([k, v]) => `<input type="hidden" name="${escapeHtml(k)}" value="${escapeHtml(v)}">`).join('\n');
  return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Secure PayFast checkout</title></head><body><p>Connecting securely to PayFast…</p><form id="pf" method="post" action="${payfastProcessUrl}">${controls}<noscript><button type="submit">Continue to PayFast</button></noscript></form><script>document.getElementById('pf').submit();</script></body></html>`;
}

app.get('/health', (_req, res) => res.json({ ok: true, environment: sandbox ? 'sandbox' : 'live' }));

// Images are persisted on the backend host's disk, not Firebase Storage.
app.post('/api/products/:productId/image', authenticate, requireOwner, (req, res) => {
  upload.single('image')(req, res, async err => {
    if (err) return res.status(400).json({ error: err.message || 'Image upload failed' });
    if (!req.file) return res.status(400).json({ error: 'Image file is required' });
    try {
      const header = Buffer.alloc(12);
      const fd = fs.openSync(req.file.path, 'r');
      fs.readSync(fd, header, 0, 12, 0);
      fs.closeSync(fd);
      const isJpeg = header[0] === 0xff && header[1] === 0xd8 && header[2] === 0xff;
      const isPng = header.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
      const isWebp = header.toString('ascii', 0, 4) === 'RIFF' && header.toString('ascii', 8, 12) === 'WEBP';
      if (!isJpeg && !isPng && !isWebp) { fs.unlink(req.file.path, () => {}); return res.status(400).json({ error: 'The uploaded file is not a supported image' }); }
      const productId = req.params.productId;
      const productRef = db.collection('products').doc(productId);
      const product = await productRef.get();
      if (!product.exists) { fs.unlinkSync(req.file.path); return res.status(404).json({ error: 'Product not found. Save product details before uploading its image.' }); }
      const imageUrl = `${publicBaseUrl}/uploads/${encodeURIComponent(req.file.filename)}`;
      await productRef.update({ imageUrl, imageUpdatedAt: admin.firestore.FieldValue.serverTimestamp() });
      res.json({ imageUrl });
    } catch (e) { fs.unlink(req.file.path, () => {}); res.status(500).json({ error: 'Could not save product image' }); }
  });
});

// Prices and availability are loaded from Firestore on the server; app-supplied totals are ignored.
app.post('/api/payments/create', authenticate, async (req, res) => {
  try {
    const { items, customer } = req.body || {};
    if (!Array.isArray(items) || items.length < 1 || items.length > 50) return res.status(400).json({ error: 'Cart items are required' });
    if (!publicBaseUrl.startsWith('https://')) return res.status(503).json({ error: 'Backend HTTPS URL is not configured' });
    if (!process.env.PAYFAST_MERCHANT_ID || !process.env.PAYFAST_MERCHANT_KEY || !process.env.PAYFAST_PASSPHRASE) return res.status(503).json({ error: 'PayFast credentials are not configured on the server' });

    const normalized = [];
    let totalCents = 0;
    for (const line of items) {
      const productId = String(line.productId || '');
      const quantity = Number(line.quantity);
      if (!productId || !Number.isInteger(quantity) || quantity < 1 || quantity > 99) return res.status(400).json({ error: 'Invalid cart line' });
      const productSnap = await db.collection('products').doc(productId).get();
      if (!productSnap.exists) return res.status(409).json({ error: `Product ${productId} is no longer available` });
      const product = productSnap.data();
      const priceCents = Number(product.priceCents || 0);
      const stock = Number(product.stockQuantity ?? 0);
      if (product.available === false || stock < quantity || !Number.isSafeInteger(priceCents) || priceCents < 1) return res.status(409).json({ error: `${product.name || 'A product'} is unavailable or has insufficient stock` });
      totalCents += priceCents * quantity;
      normalized.push({ productId, name: String(product.name || 'Gift').slice(0, 100), quantity, priceCents });
    }
    if (totalCents < 500 || !Number.isSafeInteger(totalCents)) return res.status(400).json({ error: 'PayFast requires a valid total of at least R5.00' });

    const userSnap = await db.collection('users').doc(req.user.uid).get();
    const user = userSnap.data() || {};
    const email = String(user.email || req.user.email || '').trim();
    if (!email) return res.status(400).json({ error: 'Your account needs a valid email address before checkout' });
    const displayName = String(user.name || user.fullName || req.user.name || '').trim().split(/\s+/);
    const firstName = String(customer?.firstName || displayName[0] || 'Gift4Joy').slice(0, 50);
    const lastName = String(customer?.lastName || displayName.slice(1).join(' ') || 'Customer').slice(0, 50);
    const orderRef = db.collection('orders').doc();
    const orderId = orderRef.id;
    const amount = (totalCents / 100).toFixed(2);
    const itemName = normalized.length === 1 ? normalized[0].name : `Gift4Joy order (${normalized.length} items)`;
    const fields = {
      merchant_id: process.env.PAYFAST_MERCHANT_ID,
      merchant_key: process.env.PAYFAST_MERCHANT_KEY,
      return_url: `${publicBaseUrl}/payment/return?orderId=${encodeURIComponent(orderId)}`,
      cancel_url: `${publicBaseUrl}/payment/cancel?orderId=${encodeURIComponent(orderId)}`,
      notify_url: `${publicBaseUrl}/api/payments/itn`,
      name_first: firstName,
      name_last: lastName,
      email_address: email,
      m_payment_id: orderId,
      amount,
      item_name: itemName
    };
    const signature = signatureFor(fields, process.env.PAYFAST_PASSPHRASE);
    await orderRef.set({
      userId: req.user.uid, items: normalized, amountCents: totalCents,
      amount: Number(amount), currency: 'ZAR', paymentMethod: 'PAYFAST',
      paymentStatus: 'PENDING', orderStatus: 'AWAITING_PAYMENT',
      payfastEnvironment: sandbox ? 'sandbox' : 'live', createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    res.json({ orderId, checkoutHtml: checkoutHtml(fields, signature), environment: sandbox ? 'sandbox' : 'live' });
  } catch (e) { console.error('payment create failed', e); res.status(500).json({ error: 'Could not create secure checkout' }); }
});

app.get('/api/payments/:orderId', authenticate, async (req, res) => {
  try {
    const snap = await db.collection('orders').doc(req.params.orderId).get();
    if (!snap.exists || snap.get('userId') !== req.user.uid) return res.status(404).json({ error: 'Order not found' });
    res.json({ orderId: snap.id, paymentStatus: snap.get('paymentStatus'), orderStatus: snap.get('orderStatus') });
  } catch (_e) { res.status(500).json({ error: 'Could not retrieve payment status' }); }
});

app.post('/api/payments/itn', express.urlencoded({ extended: false, limit: '32kb' }), async (req, res) => {
  try {
    const data = req.body || {};
    const orderId = String(data.m_payment_id || '');
    const orderRef = db.collection('orders').doc(orderId);
    const orderSnap = await orderRef.get();
    if (!orderSnap.exists) return res.status(400).send('Unknown order');
    const order = orderSnap.data();
    const receivedSignature = String(data.signature || '');
    const fields = {};
    for (const [key, value] of Object.entries(data)) { if (key === 'signature') break; fields[key] = String(value); }
    if (!safeEqual(signatureFor(fields, process.env.PAYFAST_PASSPHRASE), receivedSignature)) return res.status(400).send('Invalid signature');

    // PayFast recommends resolving its published hosts and comparing the sender IP.
    const senderIp = String(req.ip || req.socket.remoteAddress || '').replace(/^::ffff:/, '');
    if (!sandbox) {
      const hosts = ['www.payfast.co.za', 'w1w.payfast.co.za', 'w2w.payfast.co.za'];
      const allowed = new Set();
      for (const host of hosts) { try { (await dns.lookup(host, { all: true })).forEach(x => allowed.add(x.address)); } catch (_) {} }
      if (!allowed.has(senderIp)) return res.status(403).send('Untrusted notification source');
    }

    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(data)) if (key !== 'signature') params.append(key, String(value));
    const validation = await fetch(`https://${payfastHost}/eng/query/validate`, {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString()
    });
    const validationText = (await validation.text()).trim();
    if (!validation.ok || validationText !== 'VALID') return res.status(400).send('PayFast validation failed');
    const gross = Number(data.amount_gross);
    if (!Number.isFinite(gross) || Math.round(gross * 100) !== Number(order.amountCents)) return res.status(400).send('Amount mismatch');
    if (String(data.merchant_id) !== String(process.env.PAYFAST_MERCHANT_ID)) return res.status(400).send('Merchant mismatch');
    if (String(data.payment_status).toUpperCase() !== 'COMPLETE') return res.status(200).send('Notification received; payment not complete');
    await db.runTransaction(async transaction => {
      const freshOrder = await transaction.get(orderRef);
      if (!freshOrder.exists) throw new Error('Order disappeared');
      if (freshOrder.get('paymentStatus') === 'PAID') return; // PayFast may retry ITNs.
      const lines = freshOrder.get('items') || [];
      const productRefs = lines.map(line => db.collection('products').doc(String(line.productId)));
      const productSnaps = [];
      for (const ref of productRefs) productSnaps.push(await transaction.get(ref));
      const enoughStock = productSnaps.every((snap, index) => snap.exists &&
        snap.get('available') !== false && Number(snap.get('stockQuantity') || 0) >= Number(lines[index].quantity));
      if (enoughStock) {
        productSnaps.forEach((snap, index) => {
          transaction.update(productRefs[index], { stockQuantity: Number(snap.get('stockQuantity')) - Number(lines[index].quantity) });
        });
      }
      transaction.update(orderRef, {
        paymentStatus: 'PAID',
        orderStatus: enoughStock ? 'PROCESSING' : 'MANUAL_REVIEW',
        payfastPaymentId: String(data.pf_payment_id || ''),
        paidAt: admin.firestore.FieldValue.serverTimestamp()
      });
    });
    res.status(200).send('OK');
  } catch (e) { console.error('ITN processing failed', e); res.status(500).send('ITN processing error'); }
});

app.get('/payment/return', (req, res) => {
  const deepLink = `gift4joy://payment-return?orderId=${encodeURIComponent(req.query.orderId || '')}`;
  res.type('html').send(`<html><body><h2>Payment submitted</h2><p>Gift4Joy is checking the verified PayFast notification.</p><a href="${deepLink}">Return to Gift4Joy</a><script>setTimeout(function(){location.href=${JSON.stringify(deepLink)}},500)</script></body></html>`);
});
app.get('/payment/cancel', (req, res) => {
  const deepLink = `gift4joy://payment-cancel?orderId=${encodeURIComponent(req.query.orderId || '')}`;
  res.type('html').send(`<html><body><h2>Payment cancelled</h2><p>Your order has not been marked as paid.</p><a href="${deepLink}">Return to Gift4Joy</a><script>setTimeout(function(){location.href=${JSON.stringify(deepLink)}},500)</script></body></html>`);
});

app.use((err, _req, res, _next) => { console.error(err); res.status(500).json({ error: 'Unexpected server error' }); });
app.listen(port, () => console.log(`Gift4Joy API listening on ${port} (${sandbox ? 'PayFast sandbox' : 'PayFast live'})`));
