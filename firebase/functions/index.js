const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { checkoutForUser, cancelForUser } = require('./orderService');

initializeApp();

const region = 'us-central1';

function failure(error) {
  const message = error && error.message ? error.message : 'No se pudo completar la operación.';
  throw new HttpsError('failed-precondition', message);
}

/** Crea el pedido, descuenta stock y vacía el carrito. El cliente no escribe stock. */
exports.checkout = onCall({ region }, async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Debes iniciar sesión.');
  }
  try {
    return await checkoutForUser(getFirestore(), request.auth.uid, request.data && request.data.city);
  } catch (error) {
    failure(error);
  }
});

/** Devuelve el stock y marca CANCELADO. Solo el comprador del pedido. */
exports.cancelOrder = onCall({ region }, async (request) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Debes iniciar sesión.');
  }
  try {
    return await cancelForUser(
      getFirestore(),
      request.auth.uid,
      request.data && request.data.orderId
    );
  } catch (error) {
    failure(error);
  }
});
