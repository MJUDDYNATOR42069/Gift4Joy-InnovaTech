# PayFast — future secure backend setup

PayFast is intentionally unavailable in the Spark-only Android build. Do not put the PayFast merchant passphrase or signing logic in the Android APK.

The optional `backend/` folder contains the secure hosted-checkout and ITN verification service. To enable PayFast later, deploy that service to a trusted HTTPS endpoint, configure the merchant credentials as server environment variables, and set the Android `GIFT4JOY_API_BASE_URL` Gradle property to that endpoint.

Product images no longer use this backend. They are shared through Firestore Blob documents on the Spark plan.

Use PayFast Sandbox for test transactions before switching to live credentials. A customer returning from the PayFast page is not proof of payment; only a verified PayFast ITN may mark an order paid.
