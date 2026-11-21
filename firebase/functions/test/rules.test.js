const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');

const root = path.join(__dirname, '..', '..');
let testEnv;

test.before(async () => {
  const [host, portText] = (process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080').split(':');
  testEnv = await initializeTestEnvironment({
    projectId: 'clickmovil-rules',
    firestore: {
      rules: fs.readFileSync(path.join(root, 'firestore.rules'), 'utf8'),
      host,
      port: Number(portText),
    },
    storage: {
      rules: fs.readFileSync(path.join(root, 'storage.rules'), 'utf8'),
    },
  });
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection('users').doc('admin').set({ role: 'ADMIN', active: true, name: 'Admin', email: 'admin@correo.com', phone: '1', city: 'Bogotá' });
    await db.collection('users').doc('buyer').set({ role: 'COMPRADOR', active: true, name: 'Ana', email: 'ana@correo.com', phone: '2', city: 'Bogotá' });
    await db.collection('users').doc('seller').set({ role: 'VENDEDOR', active: true, name: 'Tienda', email: 'vendedor@correo.com', phone: '3', city: 'Bogotá' });
    await db.collection('products').doc('cable').set({ sellerId: 'seller', name: 'Cable', stock: 5, active: true, price: 1000 });
    await db.collection('orders').doc('own').set({ buyerId: 'buyer', status: 'PAGADO', sellerIds: ['seller'], city: 'Bogotá', items: [{ sellerId: 'seller', productId: 'cable', quantity: 1 }] });
    await db.collection('orders').doc('other').set({ buyerId: 'buyer2', status: 'PAGADO', sellerIds: ['seller2'], city: 'Cali', items: [] });
    await db.collection('meta').doc('seed').set({ done: true });
  });
});

test.after(async () => {
  if (testEnv) await testEnv.cleanup();
});

function db(uid) {
  return uid ? testEnv.authenticatedContext(uid).firestore() : testEnv.unauthenticatedContext().firestore();
}

test('un anónimo no lee datos privados ni el catálogo', async () => {
  const anon = db();
  await assertFails(anon.collection('users').doc('buyer').get());
  await assertFails(anon.collection('orders').doc('own').get());
  await assertFails(anon.collection('products').doc('cable').get());
  await assertFails(anon.collection('meta').doc('seed').get());
  await assertFails(anon.collection('carts').doc('buyer').collection('items').doc('cable').get());
});

test('el comprador solo ve su perfil, su carrito y sus pedidos', async () => {
  const buyer = db('buyer');
  await assertSucceeds(buyer.collection('users').doc('buyer').get());
  await assertFails(buyer.collection('users').doc('seller').get());
  await assertFails(buyer.collection('users').get());
  await assertSucceeds(buyer.collection('products').doc('cable').get());
  await assertSucceeds(buyer.collection('orders').where('buyerId', '==', 'buyer').get());
  await assertFails(buyer.collection('orders').doc('other').get());
  await assertFails(buyer.collection('orders').get());
  await assertSucceeds(buyer.collection('carts').doc('buyer').collection('items').doc('cable').set({ productId: 'cable', quantity: 1 }));
  await assertFails(buyer.collection('carts').doc('seller').collection('items').doc('cable').set({ productId: 'cable', quantity: 1 }));
});

test('el comprador no cambia su rol ni el stock', async () => {
  const buyer = db('buyer');
  await assertFails(buyer.collection('users').doc('buyer').update({ role: 'ADMIN' }));
  await assertFails(buyer.collection('users').doc('buyer2').set({ role: 'ADMIN', active: true, name: 'X', email: 'x@x.com' }));
  await assertSucceeds(buyer.collection('users').doc('buyer').update({ name: 'Ana López' }));
  await assertFails(buyer.collection('products').doc('cable').update({ stock: 99 }));
  await assertFails(buyer.collection('orders').doc('new').set({ buyerId: 'buyer', status: 'PAGADO', sellerIds: [] }));
});

test('el registro propio acepta comprador y rechaza administrador', async () => {
  const newbie = db('newbie');
  await assertSucceeds(newbie.collection('users').doc('newbie').set({
    role: 'COMPRADOR', active: true, name: 'Nuevo', email: 'nuevo@correo.com', phone: '4', city: 'Cali',
  }));
  await assertFails(newbie.collection('users').doc('newbie2').set({
    role: 'ADMIN', active: true, name: 'Trampa', email: 'trampa@correo.com',
  }));
});

test('el vendedor administra sus productos y solo sus pedidos', async () => {
  const seller = db('seller');
  await assertSucceeds(seller.collection('products').doc('cable').update({ stock: 4, name: 'Cable' }));
  await assertFails(seller.collection('products').doc('ajeno').set({ sellerId: 'otro', name: 'X', stock: 1, active: true, price: 1 }));
  await assertSucceeds(seller.collection('orders').doc('own').get());
  await assertFails(seller.collection('orders').doc('other').get());
  await assertSucceeds(seller.collection('orders').where('sellerIds', 'array-contains', 'seller').get());
  await assertFails(seller.collection('orders').get());
  await assertSucceeds(seller.collection('orders').doc('own').update({ status: 'ENVIADO' }));
  await assertFails(seller.collection('users').doc('seller').update({ role: 'ADMIN' }));
});

test('la foto del producto solo la sube el vendedor dueño o el administrador', async () => {
  const buyer = testEnv.authenticatedContext('buyer').storage();
  const seller = testEnv.authenticatedContext('seller').storage();
  const anon = testEnv.unauthenticatedContext().storage();
  await assertFails(anon.ref('products/cable/foto.jpg').putString('x'));
  await assertFails(buyer.ref('products/cable/foto.jpg').putString('x'));
  await assertSucceeds(seller.ref('products/cable/foto.jpg').putString('x'));
  await assertSucceeds(seller.ref('products/nuevo/foto.jpg').putString('x'));
});

test('el administrador gestiona usuarios, productos y pedidos', async () => {
  const admin = db('admin');
  await assertSucceeds(admin.collection('users').get());
  await assertSucceeds(admin.collection('orders').get());
  await assertSucceeds(admin.collection('users').doc('buyer').update({ role: 'VENDEDOR' }));
  await assertSucceeds(admin.collection('products').doc('cable').update({ stock: 3 }));
  await assertSucceeds(admin.collection('orders').doc('own').update({ status: 'ENTREGADO' }));
  await assertSucceeds(admin.collection('meta').doc('seed').get());
});
