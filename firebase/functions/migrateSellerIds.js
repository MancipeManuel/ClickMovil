/**
 * Migración reversible de orders.sellerIds.
 *
 * Respaldo, cuando haya autorización humana:
 * 1. Exportar la colección orders desde la consola de Firebase o con
 *    gcloud firestore export. Este script no exporta producción.
 * 2. Ensayo sin escritura: node migrateSellerIds.js
 * 3. Escritura: node migrateSellerIds.js --apply
 * 4. Reversa: node migrateSellerIds.js --rollback --apply
 *
 * Solo escribe sellerIds y sellerIdsMigratedAt.
 * La reversa borra esos dos campos y solo en documentos marcados por esta migración.
 * No corre si FIRESTORE_EMULATOR_HOST no está definido.
 */

const { FieldValue } = require('firebase-admin/firestore');
const { planSellerIdsMigration } = require('./orderService');

function assertEmulator() {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error('Abortado: la migración solo se ejecuta contra el emulador. No se tocaron datos reales.');
  }
}

async function migrateOrders(db, { apply = false, rollback = false } = {}) {
  assertEmulator();
  const snap = await db.collection('orders').get();
  const report = [];
  for (const doc of snap.docs) {
    const data = doc.data();
    if (rollback) {
      if (!data.sellerIdsMigratedAt) {
        report.push({ id: doc.id, action: 'omitido', reason: 'no lo escribió esta migración' });
        continue;
      }
      report.push({ id: doc.id, action: apply ? 'reversa' : 'ensayo-reversa' });
      if (apply) {
        await doc.ref.update({
          sellerIds: FieldValue.delete(),
          sellerIdsMigratedAt: FieldValue.delete(),
        });
      }
      continue;
    }
    const plan = planSellerIdsMigration(data);
    if (plan.skip) {
      report.push({ id: doc.id, action: 'sin-cambios', sellerIds: plan.sellerIds });
      continue;
    }
    report.push({
      id: doc.id,
      action: apply ? 'actualizado' : 'ensayo',
      sellerIds: plan.sellerIds,
      warnings: plan.warnings,
    });
    if (apply) {
      await doc.ref.update({
        sellerIds: plan.sellerIds,
        sellerIdsMigratedAt: FieldValue.serverTimestamp(),
      });
    }
  }
  return report;
}

async function main() {
  try {
    assertEmulator();
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
  const { initializeApp } = require('firebase-admin/app');
  const { getFirestore } = require('firebase-admin/firestore');
  initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'clickmovil-rules' });
  const apply = process.argv.includes('--apply');
  const rollback = process.argv.includes('--rollback');
  const report = await migrateOrders(getFirestore(), { apply, rollback });
  console.log(JSON.stringify(report, null, 2));
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message || error);
    process.exit(1);
  });
}

module.exports = { migrateOrders, assertEmulator };
