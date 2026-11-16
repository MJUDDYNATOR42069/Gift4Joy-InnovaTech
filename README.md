# Gift4Joy Android App

Gift4Joy is an Android shopping application built with Kotlin, Firebase Authentication and Cloud Firestore.

## Firebase Spark architecture

- Firebase Authentication: customer and owner sign-in.
- Cloud Firestore: products, user records, and shared product images.
- Product photos: compressed JPEG Blob documents in `productImages/{imageDocumentId}` (with the related product ID stored in the document). This avoids Firebase Storage and a separately hosted image backend, but is intended only for a small catalogue because Firestore documents are limited to 1 MiB.
- Local cache: downloaded product photos are cached in each app installation. Existing products with owner-only photos from the previous version need their photos uploaded again once.
- Payments: Pay Upon Arrival remains available. Dynamic PayFast is disabled in Spark-only mode until a trusted server is configured to sign checkout requests and verify ITN notifications.

See `FIREBASE_SPARK_SETUP.md` for Firebase configuration, Firestore rules and the PayFast security limitation.
