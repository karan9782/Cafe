const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_cafe_owner";
const BOB_UID = "bob_other_cafe";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validOrderPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    orderNumber: "#101",
    tableOrCustomer: "QR • Table 1",
    orderType: "QR_SCAN",
    status: "NEW",
    paymentStatus: "UNPAID",
    paymentMethod: "NONE",
    items: [
      {
        menuItemId: "item_chai",
        name: "Masala Chai",
        quantity: 2,
        unitPriceCents: 6000,
        notes: "Less sugar",
      },
    ],
    subtotalCents: 12000,
    taxCents: 600,
    tipCents: 0,
    discountCents: 0,
    totalCents: 12600,
    notes: "QR Scan Order",
    paymentReference: "",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validMenuItemPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    name: "Masala Chai",
    category: "COFFEE",
    priceCents: 6000,
    prepMinutes: 4,
    available: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validQrTablePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    tableCode: "T1",
    label: "Table 1",
    linkedUrl: "https://gdp4fzx3f5u6.ample.run/#table-1",
    active: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSettingsPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    cafeName: "Cafe Kitchen",
    websiteUrl: "https://gdp4fzx3f5u6.ample.run/kitchen#kitchen",
    upiId: "cafekitchen@upi",
    currencySymbol: "₹",
    taxRatePercent: 5.0,
    alertsEnabled: true,
    soundAlertsEnabled: true,
    nextOrderCounter: 101,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated users cannot read or write orders, menu items, qr_tables, or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("orders").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("orders").doc("order_1").set(validOrderPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("menu_items").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("qr_tables").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("settings").doc("profile").get()
  );
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's orders, qr_tables, and menu", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("orders").doc("order_1").set(validOrderPayload(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("menu_items").doc("item_1").set(validMenuItemPayload(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("qr_tables").doc("table_1").set(validQrTablePayload(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("settings").doc("profile").set(validSettingsPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("orders").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("orders").doc("order_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("orders").doc("order_1").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("qr_tables").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("menu_items").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("settings").doc("profile").get());
});

test("Owner can list empty collections and create/update/pay QR_SCAN orders in Rupees", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ordersCol = aliceDb.collection("users").doc(ALICE_UID).collection("orders");

  await assertSucceeds(ordersCol.get());
  await assertSucceeds(ordersCol.doc("order_1").set(validOrderPayload(ALICE_UID)));

  await assertFails(
    ordersCol.doc("order_2").set(validOrderPayload(ALICE_UID, { id: "order_2" }))
  );
  await assertFails(
    ordersCol.doc("order_3").set(validOrderPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  await assertSucceeds(
    ordersCol.doc("order_1").update({
      status: "PREPARING",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    ordersCol.doc("order_1").update({
      status: "READY",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  await assertFails(
    ordersCol.doc("order_1").update({
      createdAt: new Date(Date.now() - 300),
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    ordersCol.doc("order_1").update({
      status: "COMPLETED",
      ghostField: true,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  await assertSucceeds(
    ordersCol.doc("order_1").update({
      paymentStatus: "PAID",
      paymentMethod: "UPI",
      tipCents: 0,
      discountCents: 0,
      totalCents: 12600,
      paymentReference: "UPI-QR-9921",
      paidAt: new Date(Date.now() - 200),
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertFails(
    ordersCol.doc("order_1").update({
      status: "CANCELLED",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(ordersCol.doc("order_1").delete());
});

test("QR tables, menu items, and CafeSettings enforce schema bounds and valid updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const menuCol = aliceDb.collection("users").doc(ALICE_UID).collection("menu_items");
  const qrCol = aliceDb.collection("users").doc(ALICE_UID).collection("qr_tables");
  const settingsDoc = aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("profile");

  await assertSucceeds(menuCol.get());
  await assertSucceeds(menuCol.doc("item_chai").set(validMenuItemPayload(ALICE_UID)));
  await assertSucceeds(
    menuCol.doc("item_chai").update({
      available: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(qrCol.get());
  await assertSucceeds(qrCol.doc("table_1").set(validQrTablePayload(ALICE_UID)));
  await assertSucceeds(
    qrCol.doc("table_1").update({
      active: false,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    qrCol.doc("table_bad").set(validQrTablePayload(ALICE_UID, { tableCode: "" }))
  );

  await assertSucceeds(settingsDoc.set(validSettingsPayload(ALICE_UID)));
  await assertSucceeds(
    settingsDoc.update({
      cafeName: "Chai & Roast",
      currencySymbol: "₹",
      taxRatePercent: 5,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(settingsDoc.delete());
});
