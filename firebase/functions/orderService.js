/**
 * Lógica de pedidos compartida por las funciones y por la migración.
 * El stock solo cambia dentro de checkoutForUser y cancelForUser.
 */

function shippingFor(city) {
  const normalized = String(city || '').trim().toLowerCase();
  return normalized.includes('bogot') ? 8000 : 12000;
}

function sellerIdsFromItems(items) {
  const ids = [];
  for (const item of Array.isArray(items) ? items : []) {
    const sellerId = item && item.sellerId ? String(item.sellerId) : '';
    if (sellerId && !ids.includes(sellerId)) ids.push(sellerId);
  }
  return ids;
}

/**
 * Plan de migración: solo calcula sellerIds.
 * No toca buyerId, items, status ni totales.
 * skip es true cuando el arreglo ya coincide, para poder repetir el proceso.
 */
function planSellerIdsMigration(order) {
  const sellerIds = sellerIdsFromItems(order && order.items);
  const current = Array.isArray(order && order.sellerIds) ? order.sellerIds.map(String) : null;
  const same = current !== null
    && current.length === sellerIds.length
    && current.every((value, index) => value === sellerIds[index]);
  const warnings = [];
  if (!Array.isArray(order && order.items) || order.items.length === 0 || sellerIds.length === 0) {
    warnings.push('sin-vendedores');
  }
  return { sellerIds, skip: same, warnings };
}

function paymentRef() {
  return `EPAYCO-${100000 + Math.floor(Math.random() * 900000)}`;
}

async function checkoutForUser(db, uid, city) {
  if (!uid) throw new Error('Debes iniciar sesión.');
  const cityClean = String(city || '').trim();
  if (!cityClean) throw new Error('Escribe la ciudad de entrega.');

  const cartSnap = await db.collection('carts').doc(uid).collection('items').get();
  const lines = [];
  cartSnap.forEach((doc) => {
    const productId = doc.get('productId');
    const quantity = Number(doc.get('quantity') || 0);
    if (productId && quantity > 0) lines.push({ productId: String(productId), quantity });
  });
  if (lines.length === 0) throw new Error('El carrito está vacío.');

  const buyerSnap = await db.collection('users').doc(uid).get();
  const buyerName = buyerSnap.exists ? String(buyerSnap.get('name') || '') : '';
  const orderRef = db.collection('orders').doc();
  let created = null;

  await db.runTransaction(async (tx) => {
    let subtotal = 0;
    const items = [];
    const sellerIds = [];
    const stockWrites = [];
    for (const line of lines) {
      const pref = db.collection('products').doc(line.productId);
      const snap = await tx.get(pref);
      if (!snap.exists) throw new Error('Un producto ya no está disponible.');
      const stock = Number(snap.get('stock') || 0);
      const active = snap.get('active') !== false;
      if (!active || stock < line.quantity) {
        throw new Error(`No hay existencias suficientes de ${snap.get('name') || 'un producto'}.`);
      }
      const price = Number(snap.get('price') || 0);
      const sellerId = String(snap.get('sellerId') || '');
      subtotal += price * line.quantity;
      if (sellerId && !sellerIds.includes(sellerId)) sellerIds.push(sellerId);
      items.push({
        productId: line.productId,
        sellerId,
        productName: String(snap.get('name') || ''),
        quantity: line.quantity,
        unitPrice: price,
      });
      stockWrites.push({ pref, stock: stock - line.quantity });
    }
    const shipping = shippingFor(cityClean);
    const orderData = {
      code: `CM-${orderRef.id.slice(-6).toUpperCase()}`,
      buyerId: uid,
      buyerName,
      total: subtotal + shipping,
      shipping,
      status: 'PAGADO',
      paymentRef: paymentRef(),
      paymentGateway: 'ePayco',
      city: cityClean,
      createdAt: Date.now(),
      items,
      sellerIds,
    };
    for (const write of stockWrites) tx.update(write.pref, { stock: write.stock });
    tx.set(orderRef, orderData);
    for (const line of lines) {
      tx.delete(db.collection('carts').doc(uid).collection('items').doc(line.productId));
    }
    created = { id: orderRef.id, ...orderData };
  });
  return created;
}

async function cancelForUser(db, uid, orderId) {
  if (!uid) throw new Error('Debes iniciar sesión.');
  if (!orderId) throw new Error('Pedido no encontrado.');
  const orderRef = db.collection('orders').doc(String(orderId));
  await db.runTransaction(async (tx) => {
    const order = await tx.get(orderRef);
    if (!order.exists) throw new Error('Pedido no encontrado.');
    if (order.get('buyerId') !== uid) throw new Error('Solo puedes cancelar tus pedidos.');
    if (order.get('status') !== 'PAGADO') {
      throw new Error('Solo se cancela un pedido pagado que todavía no fue enviado. El registro se conserva.');
    }
    const items = Array.isArray(order.get('items')) ? order.get('items') : [];
    const rows = [];
    for (const item of items) {
      const productId = item && item.productId ? String(item.productId) : '';
      if (!productId) continue;
      const pref = db.collection('products').doc(productId);
      const snap = await tx.get(pref);
      rows.push({ pref, snap, quantity: Number(item.quantity || 0) });
    }
    for (const row of rows) {
      if (!row.snap.exists) continue;
      const stock = Number(row.snap.get('stock') || 0);
      tx.update(row.pref, { stock: stock + row.quantity });
    }
    tx.update(orderRef, { status: 'CANCELADO' });
  });
  return { ok: true };
}

module.exports = {
  shippingFor,
  sellerIdsFromItems,
  planSellerIdsMigration,
  checkoutForUser,
  cancelForUser,
};
