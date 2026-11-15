#!/bin/sh
# Packages the CouchSubs LG webOS TV app (third_party/couchsubs/tv-webos) with LG's ares-package
# (npm install -g @webos-tools/cli) into build/tv-app, where the Android build picks it up.
set -e
cd "$(dirname "$0")/.."
rm -rf build/tv-app
mkdir -p build/tv-app
ares-package third_party/couchsubs/tv-webos -o build/tv-app -n
ipk=$(ls build/tv-app/com.couchsubs.tv_*_all.ipk)
mv "$ipk" build/tv-app/couchsubs-lg.ipk
printf 'couchsubs-lg.ipk=%s\n' "$(basename "$ipk")" > build/tv-app/tv-packages.properties
cat build/tv-app/tv-packages.properties
