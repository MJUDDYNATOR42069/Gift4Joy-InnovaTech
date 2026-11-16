# Gift4Joy revision notes — Firebase Spark architecture

## Changes in this revision

- Replaced the product-photo upload dependency on the hosted Node backend with direct Cloud Firestore Blob storage.
- Owner-selected photos are decoded safely, resized to a maximum 1200 px edge and JPEG-compressed to a maximum 650 KiB before upload.
- Product image bytes are stored in separate `productImages/{imageDocumentId}` documents; product records store a `firestore:{imageDocumentId}` reference.
- Each replacement photo receives a new image document ID so customer-device caches cannot display a stale previous photo.
- Customer and owner devices cache downloaded images in app-private storage.
- Added Firestore rules that allow public product-image reads but restrict image writes/deletes to users whose Firestore profile has `role: "owner"`.
- Updated Firebase setup documentation for the no-billing Spark plan.
- Disabled PayFast in the Spark-only checkout UI. Secure dynamic PayFast still requires a trusted HTTPS service for signatures and ITN verification; the merchant passphrase is not placed in the Android app.
- Kept Pay Upon Arrival available.

## Validation status

- ZIP archive integrity was checked successfully.
- Android Gradle compilation could not be completed in this environment because the Gradle distribution could not be downloaded (`services.gradle.org` DNS/network unavailable).
- The Firestore rules and image upload flow still need to be published/configured in the user's Firebase project and tested on two Android devices/accounts.
