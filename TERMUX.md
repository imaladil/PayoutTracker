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

## Where is the APK?

- Every push to `main`: Actions tab -> the latest run -> Artifacts -> `PayoutTracker-apk`.
- Every `release.sh`: Releases page of the repository -> the `.apk` file.

## Keep safe

`app/release.jks` is the signing key. It is never uploaded to the repository (it is in
`.gitignore`). Keep a backup copy of it, because without it an installed app can never be
updated, only reinstalled.

## App Update inside the app

The **App Update** button checks the latest GitHub Release of `imaladil/PayoutTracker`, downloads
its APK and opens Android's installer (you tap Install). For this to work:

- the repository must be **public** (the app reads releases without a login),
- at least one release must exist: run `bash release.sh`,
- the first time, Android asks you to allow Payout Tracker to install apps.

Every update must come from `release.sh` (it raises the version number), and every build is
signed with the same key from the GitHub secrets, so it installs over the old app and keeps
your data.
