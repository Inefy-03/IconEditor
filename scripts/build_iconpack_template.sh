#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_APK="$ROOT_DIR/app/src/main/assets/archive_templates/apk/template.apk"
BT="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools/36.1.0"
PLATFORM="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platforms/android-34/android.jar"
TMP="$(mktemp -d)"

cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

mkdir -p "$TMP/res/xml" "$TMP/res/drawable-nodpi" "$TMP/res/values"
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xdb\x00\x00\x00\x00IEND\xaeB`\x82' > "$TMP/res/drawable-nodpi/ie_template.png"

cat > "$TMP/res/values/strings.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Icon Pack</string>
</resources>
EOF

cat > "$TMP/res/xml/appfilter.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources />
EOF

cat > "$TMP/res/xml/drawable.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <version>1</version>
</resources>
EOF

cat > "$TMP/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.iconeditor.iconpack">
    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="34" />
    <application
        android:hasCode="false"
        android:label="@string/app_name">
        <activity
            android:name="com.bocchi.iconeditor.iconpack.IconPackActivity"
            android:exported="true"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="com.novalauncher.THEME" />
            </intent-filter>
            <intent-filter>
                <action android:name="org.adw.launcher.THEMES" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <intent-filter>
                <action android:name="org.adw.launcher.icons.ACTION_PICK_ICON" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <intent-filter>
                <action android:name="com.gau.go.launcherex.theme" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="com.fede.launcher.THEME_ICONPACK" />
            </intent-filter>
            <intent-filter>
                <action android:name="app.lawnchair.icons.THEMED_ICON" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

"$BT/aapt2" compile --dir "$TMP/res" -o "$TMP/res.zip"
"$BT/aapt2" link \
  -o "$TMP/template.apk" \
  --manifest "$TMP/AndroidManifest.xml" \
  -I "$PLATFORM" \
  "$TMP/res.zip"

mkdir -p "$(dirname "$OUT_APK")"
cp "$TMP/template.apk" "$OUT_APK"
"$BT/aapt2" dump badging "$OUT_APK" | head -5
echo "Wrote $OUT_APK"
