# Releasing Ren

## 1. Create an upload key (once)

```bash
keytool -genkeypair -v -keystore ren-upload.jks -alias ren -keyalg RSA -keysize 4096 -validity 10000
```

Keep the keystore out of git (`*.jks` is ignored). Play App Signing holds the real app-signing key;
this is only the upload key.

## 2. Point the build at it

Either create `ren-android/keystore.properties` (git-ignored):

```properties
storeFile=ren-upload.jks
storePassword=…
keyAlias=ren
keyPassword=…
```

or set `REN_KEYSTORE`, `REN_KEYSTORE_PASSWORD`, `REN_KEY_ALIAS` and `REN_KEY_PASSWORD` in CI.
Without either, release builds are produced unsigned.

## 3. Bump the version

In `app/build.gradle.kts`, raise `versionCode` by one for every upload and set `versionName`.

## 4. Build the bundle

```bash
./gradlew :app:testDebugUnitTest :app:bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

R8 minification and resource shrinking are on. Upload
`app/build/outputs/mapping/release/mapping.txt` with the bundle so Play can deobfuscate stack traces.

## 5. Before the first upload

- Host `PRIVACY.md` at a public URL and set it in the listing and in `PRIVACY_URL`
  (`feature/settings/Settings.kt`).
- Replace the contact placeholder in `PRIVACY.md`.
- Run the app on at least one physical phone (see *Verification status* in the README).
- Start with an internal testing track; new personal developer accounts need a closed test with
  testers before production access.

## Database upgrades

Learner progress lives in the Room database. Any change to an entity must bump the database
version and add a `Migration` (the exported schemas in `app/schemas` make this mechanical).
The builder only rebuilds from the bundled asset on a downgrade, which Play never does.
