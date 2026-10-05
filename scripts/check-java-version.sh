#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import pathlib, re
checks = {
    'build.gradle': r'languageVersion\s*=\s*JavaLanguageVersion\.of\(25\)',
    '.github/workflows/ci.yaml': r'JAVA_VERSION:\s*"25"',
}
for filename, pattern in checks.items():
    if not re.search(pattern, pathlib.Path(filename).read_text()):
        raise SystemExit(f'{filename}: expected Java 25')
if not re.search(r'vendor\s*=\s*JvmVendorSpec\.ADOPTIUM', pathlib.Path('build.gradle').read_text()):
    raise SystemExit('build.gradle: expected Eclipse Temurin toolchain')
images = re.findall(r'^FROM\s+(\S+)', pathlib.Path('Dockerfile').read_text(), re.M)
if images != ['eclipse-temurin:25-jdk-noble', 'eclipse-temurin:25-jre-noble']:
    raise SystemExit('Dockerfile: expected Temurin 25 build and runtime stages')
for filename in pathlib.Path('gradle').rglob('*daemon*jvm*.properties'):
    if not re.search(r'^toolchainVersion=25$', filename.read_text(), re.M):
        raise SystemExit(f'{filename}: expected daemon Java 25')
print('Java 25 configuration is consistent')
PY
java -version
./gradlew --version
