#!/usr/bin/env bash
# One-time setup for Termux: installs git + GitHub CLI, signs in to GitHub, creates the
# repository, stores the app signing key in GitHub secrets and pushes the project.
#
# Run it inside the project folder:   bash setup.sh
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "== 1/5  Installing tools =="
pkg install -y git gh unzip

echo
echo "== 2/5  Git identity =="
if [ -z "$(git config --global user.name || true)" ]; then
  read -r -p "Your name: " NAME
  git config --global user.name "$NAME"
fi
if [ -z "$(git config --global user.email || true)" ]; then
  read -r -p "Your GitHub e-mail: " EMAIL
  git config --global user.email "$EMAIL"
fi

echo
echo "== 3/5  GitHub sign-in =="
if ! gh auth status >/dev/null 2>&1; then
  echo "A code will be shown. Open https://github.com/login/device in your browser,"
  echo "enter the code and approve. Then come back to Termux."
  gh auth login --hostname github.com --git-protocol https --web --scopes "repo,workflow"
fi
gh auth setup-git

echo
echo "== 4/5  Repository =="
[ -d .git ] || git init -b main
git add -A
if ! git diff --cached --quiet; then
  git commit -m "Initial commit"
fi

if git remote get-url origin >/dev/null 2>&1; then
  echo "Remote 'origin' already exists, skipping repository creation."
else
  read -r -p "Repository name [PayoutTracker]: " REPO
  REPO="${REPO:-PayoutTracker}"
  read -r -p "Make the repository private? [Y/n]: " PRIV
  VIS="--private"
  case "$PRIV" in [Nn]*) VIS="--public" ;; esac
  gh repo create "$REPO" $VIS --source=. --remote=origin
fi

echo
echo "== 5/5  Signing key (GitHub secrets) =="
if [ -f app/release.jks ]; then
  echo "Keystore password (the one you used when the key was made):"
  read -r -s -p "> " KSPASS
  echo
  read -r -p "Key alias [payout]: " ALIAS
  ALIAS="${ALIAS:-payout}"
  base64 -w0 app/release.jks | gh secret set KEYSTORE_BASE64
  printf '%s' "$KSPASS" | gh secret set KEYSTORE_PASSWORD
  printf '%s' "$KSPASS" | gh secret set KEY_PASSWORD
  printf '%s' "$ALIAS" | gh secret set KEY_ALIAS
  echo "Secrets saved. The key itself is NOT in the repository."
else
  echo "app/release.jks was not found, so no secrets were added."
  echo "Without it GitHub signs with a temporary key and updates will not install over the old app."
  echo "Put your release.jks in the app/ folder and run:  bash setup.sh"
fi

echo
echo "Pushing the project..."
git push -u origin main

echo
echo "Done. Open the Actions tab of your repository to watch the build."
echo "IMPORTANT: keep a backup copy of app/release.jks somewhere safe."
echo "If you lose it you can never update the installed app, only reinstall it."
