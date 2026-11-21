const test = require('node:test');
const assert = require('node:assert/strict');
const { shippingFor, sellerIdsFromItems, planSellerIdsMigration } = require('../orderService');

test('el envío a Bogotá es menor', () => {
  assert.equal(shippingFor('Bogotá'), 8000);
  assert.equal(shippingFor('Medellín'), 12000);
});

test('sellerIds sale de los ítems sin repetir vendedores', () => {
  const ids = sellerIdsFromItems([
    { sellerId: 's1' },
    { sellerId: 's1' },
    { sellerId: 's2' },
    { sellerId: '' },
  ]);
  assert.deepEqual(ids, ['s1', 's2']);
});

test('la migración no reescribe un pedido que ya tiene el mismo arreglo', () => {
  const plan = planSellerIdsMigration({
    buyerId: 'b1',
    status: 'PAGADO',
    items: [{ sellerId: 's1', productId: 'p1' }],
    sellerIds: ['s1'],
  });
  assert.equal(plan.skip, true);
  assert.deepEqual(plan.sellerIds, ['s1']);
});

test('la migración solo propone sellerIds y avisa si no hay vendedor', () => {
  const plan = planSellerIdsMigration({
    buyerId: 'b1',
    status: 'ENTREGADO',
    total: 1000,
    items: [],
  });
  assert.deepEqual(plan.sellerIds, []);
  assert.equal(plan.skip, false);
  assert.deepEqual(plan.warnings, ['sin-vendedores']);
});
