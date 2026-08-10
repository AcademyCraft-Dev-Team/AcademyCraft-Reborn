#!/usr/bin/env bash
set -euo pipefail

MISSING=0
FIXED=0
BAD=0

while IFS= read -r dir; do
    pkg=$(echo "$dir" | sed -E 's#^src/(main|test)/java/##; s#/#.#g')
    if [ ! -f "$dir/package-info.java" ]; then
        MISSING=$((MISSING + 1))
        if [ "${AUTO:-0}" = "1" ]; then
            cat > "$dir/package-info.java" <<EOF
@NullMarked
package $pkg;

import org.jspecify.annotations.NullMarked;
EOF
            echo "CREATED: $dir/package-info.java"
            FIXED=$((FIXED + 1))
        else
            echo "MISSING: $dir -> package $pkg"
        fi
    elif ! grep -q '@NullMarked' "$dir/package-info.java"; then
        BAD=$((BAD + 1))
        echo "NOT-NULLMARKED: $dir/package-info.java"
    fi
done < <(
    for root in src/main/java src/test/java; do
        [ -d "$root" ] || continue
        find "$root" -type d
    done | while read -r d; do
        [ "$(find "$d" -maxdepth 1 -name '*.java' | wc -l)" -gt 0 ] && echo "$d"
    done
)

echo "----------------------------------------"
echo "missing: $MISSING  bad(non-nullmarked): $BAD  fixed: $FIXED"
[ "$MISSING" -eq 0 ] && [ "$BAD" -eq 0 ]
