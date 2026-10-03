#!/usr/bin/env bash
# Renders the Play Store graphics and writes them, flattened to opaque RGB, to docs/play-store/graphics.
# Needs ImageMagick. Usage: tools/store/export.sh [extra gradle args]
set -euo pipefail
cd "$(dirname "$0")/../.."
rm -rf app/build/store-graphics
./gradlew "$@" :app:testDebugUnitTest --tests "app.ren.screens.StoreGraphicsTest"
src=app/build/store-graphics
dst=docs/play-store/graphics
mkdir -p "$dst"
for name in $(ls "$src" | sed -E 's/(\.tile-[0-9]+-[0-9]+)?\.png$//' | sort -u); do
  if [ -f "$src/$name.png" ]; then
    # Play wants the icon as 32-bit PNG; everything else as opaque 24-bit.
    if [ "$name" = icon-512 ]; then out="PNG32:$dst/$name.png"; else out="$dst/$name.png"; fi
    convert "$src/$name.png" -background white -alpha remove -alpha off "$out"
  else
    rows=()
    for r in $(ls "$src" | grep -oE "^$name\.tile-[0-9]+" | sed 's/.*tile-//' | sort -un); do
      convert $(ls "$src"/"$name".tile-"$r"-*.png | sort -V) +append "$src/$name.row-$r.png"
      rows+=("$src/$name.row-$r.png")
    done
    convert "${rows[@]}" -append -background white -alpha remove -alpha off "$dst/$name.png"
  fi
done
file "$dst"/*.png
