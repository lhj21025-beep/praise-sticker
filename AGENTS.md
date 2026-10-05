# Praise Sticker maintenance

The user requires every future feature improvement and bug fix to be evaluated and implemented for BOTH the web app (`index.html`) and the fully native Android app (`android/`). Do not ship a web-only or Android-only behavior change without explicitly explaining the reason and obtaining a product decision where needed.

- Android must not use WebView, a browser, or an HTML rendering wrapper. Preserve the web layout and feature set as closely as native controls allow.
- Both clients share the existing Firestore project and collection/document schema. Do not reset, migrate away, duplicate the family, or replace production data.
- Preserve roulette snapshot probabilities, retry item exchange, atomic redemption, completion reward idempotency, game rollover dates, split balances, and lifetime score rules.
- Before future feature releases, update `android/PARITY.md` and run `android/test.sh` plus the corresponding web checks. A passed compile is not an on-device test.
- The Android package is `kr.family.praisesticker`. Increase versionCode for each release and retain the original signing identity. Never generate a replacement signing key for an update.
- Signing key and password are private Library artifacts, outside this public repository. Never commit them, authentication tokens, or exported family data.
- Do not consume real rewards or change real stickers as a test. Use isolated fixtures. A user-authorized recovery is a separate operation.
