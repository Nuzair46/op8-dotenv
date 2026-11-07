#!/usr/bin/env python3
"""Build CustomPhone with an existing Android SDK/JDK; never connects to a phone."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent


def run(arguments):
    subprocess.run([str(arg) for arg in arguments], cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-jar', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--d8-jar', type=Path, help='Optional D8/R8 jar; defaults to Build Tools lib/d8.jar')
    parser.add_argument('--work-dir', type=Path, required=True,
                        help='Private output/signing directory outside this repository')
    args = parser.parse_args()
    if args.java_home is None:
        parser.error('Supply --java-home or JAVA_HOME (JDK 21 was used for validation)')
    sdk, bt, java = args.android_jar.resolve(), args.build_tools.resolve(), args.java_home.resolve() / 'bin'
    d8 = args.d8_jar.resolve() if args.d8_jar else bt / 'lib/d8.jar'
    work = args.work_dir.resolve()
    if work == Path(work.anchor) or work.is_relative_to(REPO) or REPO.is_relative_to(work):
        parser.error('--work-dir must be separate from the repository and its parent directories')
    for path in [sdk, d8, bt/'aapt2', bt/'zipalign', bt/'lib/apksigner.jar',
                 *[java/name for name in ['java', 'javac', 'jar', 'keytool']]]:
        if not path.is_file():
            parser.error('Missing tool: ' + str(path))
    assets = json.loads((REPO/'PUBLIC_ASSETS.json').read_text(encoding='utf-8'))['files']
    for asset in assets:
        path = (REPO/asset['path']).resolve()
        if not path.is_relative_to(ROOT) or hashlib.sha256(path.read_bytes()).hexdigest() != asset['sha256']:
            raise RuntimeError('Wallpaper asset mismatch; review provenance before building')

    marker = work/'.customphone-workspace'
    marker_text = 'CustomPhone private build workspace\n'
    if work.exists() and any(work.iterdir()) and (not marker.is_file() or marker.read_text() != marker_text):
        raise RuntimeError('Refusing to use an unrelated nonempty output directory')
    work.mkdir(parents=True, exist_ok=True)
    marker.write_text(marker_text)
    build, signing = work/'build', work/'signing'
    for directory in [build, signing, *[build/name for name in ['classes', 'generated', 'dex']]]:
        if not directory.resolve().is_relative_to(work) or directory.is_symlink():
            raise RuntimeError('Unsafe build directory')
        directory.mkdir(parents=True, exist_ok=True)
    for name in ['classes', 'generated', 'dex']:
        for path in (build/name).rglob('*'):
            if path.is_symlink():
                raise RuntimeError('Symlink in generated outputs')
            if path.is_file():
                path.unlink()

    run([bt/'aapt2', 'compile', '--dir', ROOT/'app/src/main/res', '-o', build/'resources.zip'])
    run([bt/'aapt2', 'link', '-o', build/'unsigned.apk', '--manifest', ROOT/'app/src/main/AndroidManifest.xml',
         '-I', sdk, '--java', build/'generated', '--auto-add-overlay', '-0', 'mp4', build/'resources.zip'])
    sources = sorted((ROOT/'app/src/main/java').rglob('*.java')) + sorted((build/'generated').rglob('*.java'))
    run([java/'javac', '--release', '8', '-Xlint:-options', '-encoding', 'UTF-8', '-classpath', sdk,
         '-d', build/'classes', *sources])
    run([java/'jar', 'cf', build/'classes.jar', '-C', build/'classes', '.'])
    run([java/'java', '-cp', d8, 'com.android.tools.r8.D8', '--release', '--lib', sdk,
         '--min-api', '30', '--output', build/'dex', build/'classes.jar'])
    with zipfile.ZipFile(build/'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
        for file in (build/'dex').glob('*.dex'):
            apk.write(file, file.name)

    key, password = signing/'customphone.jks', signing/'.signing-password'
    if key.exists() != password.exists():
        raise RuntimeError('Signing key/password pair is incomplete; restore the original pair')
    if not key.exists():
        with password.open('x', encoding='utf-8') as stream:
            stream.write(secrets.token_hex(24))
        password.chmod(0o600)
        run([java/'keytool', '-genkeypair', '-keystore', key, '-storepass:file', password,
             '-keypass:file', password, '-alias', 'customphone', '-keyalg', 'RSA', '-keysize', '3072',
             '-validity', '10000', '-dname', 'CN=CustomPhone, OU=Local Launcher'])
        key.chmod(0o600)
    run([bt/'zipalign', '-f', '-p', '4', build/'unsigned.apk', build/'aligned.apk'])
    run([java/'java', '-jar', bt/'lib/apksigner.jar', 'sign', '--ks', key,
         '--ks-pass', 'file:'+str(password), '--out', build/'customphone.apk', build/'aligned.apk'])
    run([java/'java', '-jar', bt/'lib/apksigner.jar', 'verify', '--verbose', build/'customphone.apk'])
    output = build/'customphone.apk'
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    (build/'customphone.apk.sha256').write_text(digest+'  customphone.apk\n')
    print('Built:', output)
    print('SHA256:', digest)
    print('Keep the signing directory private and backed up; updates require the same key.')


if __name__ == '__main__':
    main()
