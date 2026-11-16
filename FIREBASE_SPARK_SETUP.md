# Gift4Joy — Firebase Spark setup

This build uses Firebase Authentication and Cloud Firestore only for shared app data. It does **not** use Firebase Cloud Storage, Firebase Cloud Functions, or a paid hosting service for product photos.

## Product image storage on Spark

- Product details remain in `products/{productId}`.
- Each shared photo is stored separately in `productImages/{imageDocumentId}` (with the related product ID stored in the document) as a Firestore `Blob` field named `imageData`.
- The app converts the selected photo to JPEG, resizes it to a maximum 1200 px edge, and compresses it to no more than 650 KiB before upload.
- Product documents store `imageUrl: "firestore:{imageDocumentId}"` as the image reference.
- Customers download the Blob from Firestore; the app caches it in its private app storage for subsequent views/offline display.
- Individual image documents are publicly readable by document ID (collection listing is blocked). Only accounts whose Firestore `users/{uid}.role` is `owner` can create, replace, or delete shared image documents.
- Existing products whose photos were only saved on the previous owner's device need their photo selected and uploaded again once; those local-only files were never available to other customers.

Firestore documents have a 1 MiB maximum size. Keep the image limit in the app; do not upload original full-resolution camera photos or arbitrary files to Firestore. This is a small-catalogue/student-project workaround, not a scalable image CDN. Monitor Firestore usage and keep image sizes modest.

## Firestore rules

Review and publish `backend/firestore.rules.example` in Firebase Console → Firestore Database → Rules. Confirm that the owner account has `role: "owner"` in `users/{uid}`. Do not allow customers to write to `productImages` or change their own role.

## Required Firebase setup

1. Keep the existing Firebase project on the Spark plan.
2. In Firebase Console → Authentication → Sign-in method, enable the sign-in providers used by the app.
3. Create/confirm the Cloud Firestore database.
4. Publish the rules above.
5. Ensure `app/google-services.json` belongs to the Gift4Joy Firebase project. Do not share service-account private keys or upload them to source control.
6. Sign in as the owner and upload a small product photo. Then sign in on a second device/account and confirm that the photo loads from Firestore.

## PayFast limitation on Spark

A secure dynamic PayFast checkout requires a trusted server to generate the payment signature using the merchant passphrase and to validate PayFast ITN notifications before an order is marked paid. The merchant passphrase must never be embedded in the Android APK. Firebase Spark does not provide Cloud Functions, so this project leaves PayFast visibly unavailable in Spark-only mode and keeps Pay Upon Arrival available. Do not treat a browser return URL as proof of payment.

To enable live PayFast later, add a trusted server endpoint (for example, a separately deployed serverless worker) or move to a Firebase plan that supports a trusted backend. Use PayFast Sandbox for development; it does not require a real customer payment. Only switch to live credentials after ITN verification is tested.
