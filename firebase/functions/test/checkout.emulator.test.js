const test = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { checkoutForUser, cancelForUser } = require('../orderService');

test('checkout descuenta stock y cancelOrder lo devuelve sin tocar el resto del pedido', async (t) => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    t.skip('sin emulador');
    return;
  }
  if (getApps().length === 0) initializeApp({ projectId: 'clickmovil-rules' });
  const db = getFirestore();
  await db.collection('users').doc('buyer').set({ name: 'Ana', role: 'COMPRADOR', active: true });
  await db.collection('products').doc('cable').set({
    sellerId: 'seller', name: 'Cable', stock: 5, active: true, price: 19900, description: 'Cable',
  });
  await db.collection('carts').doc('buyer').collection('items').doc('cable').set({ productId: 'cable', quantity: 2 });

  const order = await checkoutForUser(db, 'buyer', 'Bogotá');
  const product = await db.collection('products').doc('cable').get();
  const saved = await db.collection('orders').doc(order.id).get();
  const cart = await db.collection('carts').doc('buyer').collection('items').doc('cable').get();

  assert.equal(product.get('stock'), 3);
  assert.equal(product.get('name'), 'Cable');
  assert.equal(saved.get('status'), 'PAGADO');
  assert.equal(saved.get('buyerId'), 'buyer');
  assert.equal(saved.get('shipping'), 8000);
  assert.deepEqual(saved.get('sellerIds'), ['seller']);
  assert.equal(saved.get('items')[0].productName, 'Cable');
  assert.equal(cart.exists, false);

  await cancelForUser(db, 'buyer', order.id);
  const after = await db.collection('orders').doc(order.id).get();
  const restocked = await db.collection('products').doc('cable').get();
  assert.equal(after.get('status'), 'CANCELADO');
  assert.equal(after.get('buyerId'), 'buyer');
  assert.equal(after.get('paymentRef'), saved.get('paymentRef'));
  assert.equal(restocked.get('stock'), 5);
  await assert.rejects(() => cancelForUser(db, 'seller', order.id));
});
