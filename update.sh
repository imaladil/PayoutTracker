#!/usr/bin/env bash
# Sends an update to GitHub from Termux.
#
#   bash update.sh                    newest PayoutTracker*.zip in your Downloads folder is copied
#                                     over this project, then committed and pushed
#   bash update.sh /path/to/new.zip   same, with a zip you choose
#   bash update.sh --push "message"   only commit and push what is already in this folder
#
# Everything is inside main() so the script can safely be replaced while it runs.
main() {
  set -e
  DIR="$(cd "$(dirname "$0")" && pwd)"
  cd "$DIR"

  MSG="Update"
  if [ "$1" = "--push" ]; then
    [ -n "$2" ] && MSG="$2"
  else
    ZIP="$1"
    if [ -z "$ZIP" ]; then
      ZIP="$(ls -t "$HOME"/storage/downloads/PayoutTracker*.zip 2>/dev/null | head -1 || true)"
    fi
    if [ -z "$ZIP" ] || [ ! -f "$ZIP" ]; then
      echo "No zip found. Save the new PayoutTracker.zip to your phone's Downloads folder"
      echo "(run 'termux-setup-storage' once if ~/storage does not exist), or pass its path."
      exit 1
    fi
    echo "Using: $ZIP"
    TMP="$(mktemp -d)"
    unzip -q "$ZIP" -d "$TMP"
    SRC="$TMP/PayoutTracker"
    [ -d "$SRC" ] || SRC="$TMP"
    if [ ! -f "$SRC/app/build.gradle.kts" ]; then
      echo "This zip does not look like the Payout Tracker project. Nothing was changed."
      rm -rf "$TMP"
      exit 1
    fi
    # Never let the zip touch the signing key or git data
    rm -rf "$SRC/.git"
    find "$SRC" -name '*.jks' -delete

    # 1) remove files that are tracked here but no longer exist in the new version
    git ls-files -z | while IFS= read -r -d '' f; do
      [ -e "$SRC/$f" ] || rm -f "$f"
    done
    # 2) copy the new version in
    cp -a "$SRC"/. "$DIR"/
    # 3) tidy up folders that became empty
    find "$DIR" -type d -empty -not -path "$DIR/.git/*" -not -path "$DIR" -delete 2>/dev/null || true
    rm -rf "$TMP"
    [ -n "$2" ] && MSG="$2"
  fi

  git add -A
  if git diff --cached --quiet; then
    echo "Nothing changed, nothing to push."
    return 0
  fi
  git commit -m "$MSG"
  git push -u origin HEAD
  echo "Pushed. A build starts on GitHub (Actions tab)."
  echo "To publish it as a release that installs over the old app, run:  bash release.sh"
}

main "$@"
exit $?
