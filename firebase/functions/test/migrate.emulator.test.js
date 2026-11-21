const test = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { migrateOrders } = require('../migrateSellerIds');

test('la migración agrega sellerIds y la reversa solo borra esos campos', async (t) => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    t.skip('sin emulador');
    return;
  }
  if (getApps().length === 0) initializeApp({ projectId: 'clickmovil-rules' });
  const db = getFirestore();
  const ref = db.collection('orders').doc('historico');
  await ref.set({
    buyerId: 'buyer',
    buyerName: 'Ana',
    status: 'ENTREGADO',
    total: 27900,
    shipping: 8000,
    city: 'Bogotá',
    paymentRef: 'EPAYCO-111111',
    items: [
      { sellerId: 'seller', productId: 'cable', productName: 'Cable', quantity: 1, unitPrice: 19900 },
    ],
  });

  const dry = await migrateOrders(db, { apply: false });
  const untouched = await ref.get();
  assert.equal(untouched.get('sellerIds'), undefined);
  assert.equal(dry.find((row) => row.id === 'historico').action, 'ensayo');

  await migrateOrders(db, { apply: true });
  const migrated = await ref.get();
  assert.deepEqual(migrated.get('sellerIds'), ['seller']);
  assert.ok(migrated.get('sellerIdsMigratedAt'));
  assert.equal(migrated.get('status'), 'ENTREGADO');
  assert.equal(migrated.get('buyerId'), 'buyer');
  assert.equal(migrated.get('paymentRef'), 'EPAYCO-111111');
  assert.equal(migrated.get('total'), 27900);
  assert.equal(migrated.get('items')[0].productName, 'Cable');

  await migrateOrders(db, { rollback: true, apply: true });
  const rolled = await ref.get();
  assert.equal(rolled.get('sellerIds'), undefined);
  assert.equal(rolled.get('sellerIdsMigratedAt'), undefined);
  assert.equal(rolled.get('status'), 'ENTREGADO');
  assert.equal(rolled.get('buyerName'), 'Ana');
  assert.equal(rolled.get('paymentRef'), 'EPAYCO-111111');
});
