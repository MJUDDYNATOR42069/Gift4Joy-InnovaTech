# Gift4Joy backend

This is an optional future backend for secure PayFast checkout. The current Spark-only Android build does not call it for product photos: shared photos are stored as compressed Firestore Blob documents in `productImages/{productId}` and cached locally on customer devices.

- **PayFast hosted checkout and ITN verification.** Merchant secrets remain on the server. The app never receives the passphrase or signs payments. Prices and stock are reloaded from Firestore before checkout; the app's displayed total is not trusted.
- The legacy server-disk image endpoint remains for deployments that choose to use this backend later, but it is not part of the Spark-only image flow.

## Requirements

- Node.js 20+
- A Firebase service-account JSON for the same Firebase project as the Android app, with Firestore read/write access
- A publicly reachable HTTPS hostname
- PayFast merchant credentials (use Sandbox credentials for development; switch to live credentials only after sandbox testing)

## Local setup

1. Copy `.env.example` to `.env`.
2. Put your Firebase Admin service-account JSON in this directory as `service-account.json` (do not commit it), or set `GOOGLE_APPLICATION_CREDENTIALS` to its absolute path.
3. Set `PUBLIC_BASE_URL` to the public HTTPS URL of this server. PayFast must be able to reach the ITN URL.
4. Set `PAYFAST_ENV=sandbox`, and enter the sandbox merchant ID, key and passphrase in `.env`.
5. Install and run:

   ```bash
   npm install
   npm start
   ```

6. Confirm `GET /health` returns `{ "ok": true }`.

For local development behind a trusted reverse proxy, set `TRUST_PROXY=1`. Do not enable this unless exactly one trusted proxy sits in front of the service.

## Android configuration

Build the Android app with the backend HTTPS URL:

```bash
./gradlew assembleDebug -PGIFT4JOY_API_BASE_URL=https://YOUR-BACKEND-DOMAIN
```

In Android Studio, the same property can be added to the Gradle command-line arguments for the build. The default URL is deliberately a placeholder; payment and photo upload will return a clear connection error until it is replaced.

## API routes

- `GET /health` — service status and sandbox/live mode
- `POST /api/products/:productId/image` — Firebase-authenticated owner-only multipart image upload (`image` field)
- `POST /api/payments/create` — authenticated customer checkout creation; accepts `{ "items": [{ "productId": "...", "quantity": 1 }] }`
- `GET /api/payments/:orderId` — authenticated owner of the order can read payment status
- `POST /api/payments/itn` — public PayFast ITN receiver; validates signature, sender IP in live mode, PayFast validation response, merchant ID and exact gross amount before marking paid

Successful payments are stored in Firestore `orders`. Stock is decremented transactionally once, on the first verified COMPLETE ITN. If stock is no longer sufficient when PayFast confirms payment, the order is marked `MANUAL_REVIEW` rather than silently fulfilling it.

## Legacy persistent photo storage (optional)

The current Android Spark-only build uses Firestore Blob documents for shared product photos. This disk-backed image endpoint is retained only for a future deployment that intentionally chooses server-hosted images. If enabled, mount a persistent volume at `/var/data` and keep `PUBLIC_BASE_URL` stable.

Example Docker run (mount your secrets and persistent disk):

```bash
docker build -t gift4joy-backend .
docker run -p 3000:3000 \
  --env-file .env \
  -v /absolute/path/to/service-account.json:/run/secrets/firebase.json:ro \
  -v /absolute/path/to/product-images:/var/data/product-images \
  -e GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/firebase.json \
  gift4joy-backend
```

## PayFast test checklist

1. Use Sandbox credentials and `PAYFAST_ENV=sandbox`.
2. Confirm checkout opens the PayFast Sandbox hosted payment page.
3. Complete a Sandbox wallet payment.
4. Confirm PayFast sends an ITN to `/api/payments/itn` and the backend records `paymentStatus=PAID`.
5. Confirm the app only clears the cart after `GET /api/payments/:orderId` reports `PAID`.
6. Test cancel, invalid signature, amount mismatch, duplicate ITN and out-of-stock/manual-review cases.
7. Only then change to live credentials and `PAYFAST_ENV=live`.

Never put a merchant passphrase, service-account JSON or live merchant key in Android source, Gradle files, Firestore documents, or a public repository.

## Firestore security rules

Review `firestore.rules.example` and merge the equivalent protections into the project's existing Firestore rules before production. It allows public product reads, owner-only product writes, users to read/update their own profile without changing their role, and blocks direct client writes to orders. The backend uses Firebase Admin and is not restricted by client Firestore rules. Do not blindly replace existing rules if the app uses other collections; merge and test the full ruleset.
