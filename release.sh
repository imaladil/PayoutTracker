#!/usr/bin/env bash
# Publishes a new version: raises versionCode/versionName, commits, tags and pushes.
# GitHub then builds the APK and attaches it to a new Release.
#
#   bash release.sh          1.1 -> 1.2   (last number goes up by one)
#   bash release.sh 2.0      sets the version name to 2.0
#
# versionCode always goes up by one, which Android needs to accept an update.
main() {
  set -e
  DIR="$(cd "$(dirname "$0")" && pwd)"
  cd "$DIR"
  FILE="app/build.gradle.kts"

  CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$FILE" | head -1)"
  NAME="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$FILE" | head -1)"
  if [ -z "$CODE" ] || [ -z "$NAME" ]; then
    echo "Could not read the version from $FILE"
    exit 1
  fi

  NEW_CODE=$((CODE + 1))
  if [ -n "$1" ]; then
    NEW_NAME="$1"
  else
    IFS=. read -r A B C <<EOF
$NAME
EOF
    if [ -n "$C" ]; then
      NEW_NAME="$A.$B.$((C + 1))"
    else
      NEW_NAME="$A.$((B + 1))"
    fi
  fi

  if git rev-parse "v$NEW_NAME" >/dev/null 2>&1; then
    echo "Version v$NEW_NAME already exists. Pass a different version, e.g.  bash release.sh 2.0"
    exit 1
  fi

  echo "Version $NAME ($CODE)  ->  $NEW_NAME ($NEW_CODE)"
  sed -i "s/versionCode = $CODE/versionCode = $NEW_CODE/" "$FILE"
  sed -i "s/versionName = \"$NAME\"/versionName = \"$NEW_NAME\"/" "$FILE"

  git add -A
  git commit -m "Release v$NEW_NAME"
  git tag "v$NEW_NAME"
  git push -u origin HEAD
  git push origin "v$NEW_NAME"

  echo
  echo "Released v$NEW_NAME. GitHub is building it now (Actions tab)."
  echo "When it finishes, the APK is under the repository's Releases."
}

main "$@"
exit $?
