# Firebase setup (phase 1: Gmail login, one device, forced updates)

Do this once. Everything is on the free Spark plan; no card needed.

## 1. Create the project
1. Open https://console.firebase.google.com and sign in with your admin Gmail.
2. **Add project** -> name it `PayoutTracker` -> turn Google Analytics off -> Create.

## 2. Turn on Google sign-in
1. Left menu **Build -> Authentication -> Get started**.
2. **Sign-in method -> Google -> Enable**, choose your email as the support email, Save.

## 3. Create the database
1. **Build -> Firestore Database -> Create database**.
2. Choose a location near you (for example `asia-south1`), start in **production mode**.
3. Open the **Rules** tab, delete everything, paste the content of `firestore.rules` from this
   repository (first change `change_me@gmail.com` to your admin Gmail, lower case), press **Publish**.

## 4. Get the signing-key fingerprint (SHA-1)
Google sign-in only works for an app whose signing key is registered.
1. In GitHub open the **Actions** tab, open the latest run of "Build APK".
2. Open the step **Print signing fingerprints** and copy the `SHA1:` line.

## 5. Register the Android app
1. Firebase **Project settings (gear icon) -> Your apps -> Add app -> Android**.
2. Package name: `com.payouttracker.app`. Paste the SHA-1 from step 4. Register.
3. Download **google-services.json**. (If you add the SHA-1 later, download the file again.)

## 6. Put the file into the project (Termux)
```
cp ~/storage/downloads/google-services.json ~/PayoutTracker/app/
cd ~/PayoutTracker
bash update.sh --push "Add Firebase config"
bash release.sh
```

## 7. Admin Gmail in the code
Edit `app/src/main/java/com/payouttracker/app/Config.kt` and set `ADMIN_EMAIL` to your admin Gmail
(same address as in the rules). Push it with `bash update.sh --push "Set admin email"`.

## 8. Forced updates
In Firestore create a collection `config` with a document `app` and a number field
`minVersionCode`. Every installed version whose versionCode is lower than this number is blocked
until the person downloads the newest release. (versionCode of v1.4 is 5.)
Raise it only after the new release is published on GitHub.

## Notes
- The `PayoutTracker-debug-apk` build uses a random key on every run, so Google sign-in does not
  work in it. It only exists to compare Play Protect behaviour.
- Signing in on a new device signs the old device out (it happens as soon as the old device is online).
