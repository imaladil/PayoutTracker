# Payout Tracker: GitHub from Termux

## First time

1. Install Termux (from F-Droid) and open it.
2. `termux-setup-storage` and allow access to files.
3. `pkg install -y unzip`
4. `unzip ~/storage/downloads/PayoutTracker.zip -d ~`
5. `cd ~/PayoutTracker`
6. `bash setup.sh`

`setup.sh` installs git and the GitHub CLI, signs you in (a code is shown, enter it at
https://github.com/login/device), creates the repository, saves the signing key in GitHub
secrets and pushes the project.

## Every later update

1. Save the new `PayoutTracker.zip` to your phone's Downloads folder.
2. `cd ~/PayoutTracker`
3. `bash update.sh`  (copies the zip over the project, commits and pushes)
4. To publish a release: `bash release.sh`  (or `bash release.sh 2.0` for a chosen version)

Edited a file inside Termux yourself? Use `bash update.sh --push "what I changed"`.

## If google-services.json is missing

`update.sh` now never deletes `app/google-services.json` and keeps a backup in `~/.payouttracker-keep`.
GitHub also refuses to build when the file is missing, so a build without login can no longer be made by accident.

First time with this version, use the new script before it runs the old one:

    cd ~/PayoutTracker
    cp app/google-services.json ~/ 2>/dev/null
    unzip -p ~/storage/downloads/PayoutTracker.zip PayoutTracker/update.sh > update.sh
    bash update.sh

If the file is already gone, bring it back from git history:

    git checkout "$(git rev-list -n 1 HEAD -- app/google-services.json)^" -- app/google-services.json
    bash update.sh --push "Restore Firebase config"

Optional extra safety: `base64 -w0 app/google-services.json | gh secret set GOOGLE_SERVICES_BASE64`

## Where is the APK?

- Every push to `main`: Actions tab -> the latest run -> Artifacts -> `PayoutTracker-apk`.
- Every `release.sh`: Releases page of the repository -> the `.apk` file.

## Keep safe

`app/release.jks` is the signing key. It is never uploaded to the repository (it is in
`.gitignore`). Keep a backup copy of it, because without it an installed app can never be
updated, only reinstalled.

## Forced updates and online features

Gmail login, the one-device rule and forced updates need Firebase: follow `FIREBASE.md` once.
There is no App Update button anymore. When you raise `minVersionCode` in Firebase, older
versions show an "Update required" screen with a Download button that opens the APK of the latest
GitHub Release (the repository must be **public**). Every update must come from `release.sh`, and
every build is signed with the same key, so it installs over the old app and keeps the data.

## Moving from the old `com.example.payouttracker` build

Version 1.3 uses the package name `com.payouttracker.app`, so Android treats it as a new app.
Install it, then uninstall the old Payout Tracker. Data from the old app is not carried over.
