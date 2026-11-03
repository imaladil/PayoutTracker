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

## Phase 2 additions
- Publish the updated `firestore.rules` again (Firestore -> Rules): it now covers `events` and `strategies`.
- Open the app as the admin (the Gmail in `ADMIN_EMAIL`): the home screen shows an **Admin Panel** button.
  From there: Add Event, move events Go Live / Won / Lost, post a Strategy (title, text, picture),
  and set the minimum app version.
- Strategy pictures are shrunk to about 450 KB and stored inside the database (no paid plan needed).


## Version 1.5: subscriptions, mailbox, notifications

1. **Replace the Firestore rules.** Firestore -> Rules, delete everything, paste the new `firestore.rules`
   (it already has the admin Gmail in it), press **Publish**. Without this step events, messages and
   payments do not work.
2. **Payment numbers.** Sign in with the admin Gmail -> menu -> Admin Panel -> "Payment numbers", enter the
   bKash, Nagad and Rocket personal numbers, Save numbers.
3. **Minimum version.** After the new release is published on GitHub, Admin Panel -> "Minimum app version"
   -> enter the new versionCode (v1.5 = 6) -> Set. Older apps then show "Update required".

How it works
- New users get a 7 calendar day free trial (counted in Bangladesh time, registration day not counted).
  After that Live and Pre-Match are locked ("Subscribe"). Results are open to everyone; Strategy, Track Payout,
  Summary and Brief Report need no subscription (Strategy needs login).
- Subscribe: the user sends money to the number shown, then enters their own number and the Transaction ID.
  Admin Panel -> Users -> "Activate by Transaction ID": choose 30 days / 6 months / 1 year, enter the ID the
  user submitted. The matching user becomes premium (time is added after any time they already have).
  Check the payment in your bKash/Nagad/Rocket app first; the app cannot check it for you.
- A Transaction ID can be used once, ever. The user can edit it for 1 hour. After 2 hours they can send a
  report; it appears in Admin Panel -> Reports (oldest first) where you can Activate, Decline or Terminate.
- Admin Panel -> Users: search by email or Transaction ID, select users and give 3/7/15/30 bonus days or
  remove the bonus, terminate or restore an account, tap "Spent" to see the payment history.
- Inbox: reply to Contact Us messages (the reply goes only to that user) and send a message to everyone.
- Notifications (new event, event goes live, new message, new app version) appear while the app is open or
  still running in the background. A fully closed app cannot be notified on the free Firebase plan.
