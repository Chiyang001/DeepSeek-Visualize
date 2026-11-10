import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


SOURCE = Path(__file__).resolve().parent
ROOT = SOURCE.parent
APK = ROOT / "DeepSeek-2.7.5-295.apk"
REVERSE = ROOT / "reverse"
DECODED = REVERSE / "apktool"
BUILD = REVERSE / "visualize-build"
PACKAGE = "com.deepseek.chat.visualize"
APKTOOL = Path(os.environ.get("APKTOOL_JAR", r"C:\Program Files\AndroidReverseTool\apktool\apktool.jar"))
SDK = Path(os.environ.get("ANDROID_HOME", Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk"))
TOOLS = SDK / "build-tools" / "37.0.0"
PLATFORM = SDK / "platforms" / "android-36" / "android.jar"
APK_SHA256 = "ddc52ef868b991fd33e634de834a3e2c022b0cd7ff9e854f4f141996aaeadb70"
RELEASE_PASSWORD_ENV = "DEEPSEEK_VISUALIZE_RELEASE_STOREPASS"


def run(*command):
    subprocess.run([str(arg) for arg in command], check=True, cwd=ROOT)


def patch(path, before, after, previous=None):
    content = path.read_text(encoding="utf-8")
    if content.count(after) == 1:
        return
    if previous and content.count(previous) == 1:
        path.write_text(content.replace(previous, after), encoding="utf-8")
        return
    if content.count(before) != 1:
        raise ValueError(f"Patch anchor in {path} must occur exactly once: {before!r}")
    path.write_text(content.replace(before, after), encoding="utf-8")


def patch_apk():
    version_file = DECODED / "apktool.yml"
    version_296 = "  versionCode: 296\n  versionName: 2.7.5.1\n"
    version_297 = "  versionCode: 297\n  versionName: 2.7.5.2\n"
    patch(version_file, "  versionCode: 295\n  versionName: 2.7.5\n",
          "  versionCode: 298\n  versionName: 2.7.5.3\n",
          previous=version_297 if version_297 in version_file.read_text(encoding="utf-8") else version_296)
    manifest = DECODED / "AndroidManifest.xml"
    patch(manifest, 'package="com.deepseek.chat"', f'package="{PACKAGE}"')
    for suffix in ["provider", "cn.fly.FlyProvider", "androidx-startup", "jlatexmathinitprovider", "apm"]:
        patch(manifest, f'android:authorities="com.deepseek.chat.{suffix}"',
              f'android:authorities="{PACKAGE}.{suffix}"')
    for identity in ["permission", "uses-permission"]:
        patch(manifest, f'<{identity} android:name="com.deepseek.chat.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"',
              f'<{identity} android:name="{PACKAGE}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"')
    patch(manifest, 'android:taskAffinity="com.deepseek.chat"', f'android:taskAffinity="{PACKAGE}"')
    patch(manifest, 'android:taskAffinity="com.deepseek.chat.entry_proxy"',
          f'android:taskAffinity="{PACKAGE}.entry_proxy"')
    patch(manifest, 'android:scheme="com.deepseek.chat"', f'android:scheme="{PACKAGE}"')
    patch(manifest, 'android:scheme="dpsk"', 'android:scheme="dpsk-visualize"')
    patch(manifest, 'android:label="@string/app_name" android:largeHeap',
          'android:label="DeepSeek Visualize" android:largeHeap')
    patch(manifest, 'android:label="@string/app_name" android:launchMode="singleTask"',
          'android:label="DeepSeek Visualize" android:launchMode="singleTask"')
    patch(manifest, '    </application>',
          '        <activity android:name="local.visualize.PreviewActivity" android:exported="true" '
          'android:label="可视化示例" android:theme="@style/Theme.DeepSeek"/>\n'
          '    </application>')
    strings = DECODED / "res" / "values" / "strings.xml"
    patch(strings, '</resources>', '    <string name="visualize_preview">可视化示例</string>\n</resources>')
    shortcuts = DECODED / "res" / "xml" / "shortcuts.xml"
    patch(shortcuts, '    <shortcut android:icon="@drawable/ic_shortcut_new_chat"',
          '    <shortcut android:icon="@drawable/ic_shortcut_new_chat" android:enabled="true" '
          'android:shortcutId="visualize_preview" android:shortcutShortLabel="@string/visualize_preview">\n'
          f'        <intent android:action="android.intent.action.VIEW" android:targetPackage="{PACKAGE}" '
          'android:targetClass="local.visualize.PreviewActivity" />\n'
          '    </shortcut>\n'
          '    <shortcut android:icon="@drawable/ic_shortcut_new_chat"')

    smali = DECODED / "smali"
    # This clone's package and signing certificate cannot use DeepSeek's registered WeChat OAuth identity.
    patch(smali / "ju6.smali", '    if-ne v3, v6, :cond_f',
          '    # Do not offer WeChat sign-in in the independently signed build.\n    goto :cond_f')
    anchor = '    const-string v5, "mermaid"'
    previous_hook = '''    # Render only completed fences where the assistant's renderer enables rich blocks.
    if-eqz v9, :visualize_fallback
    iget-boolean v5, v4, Ll07;->b:Z
    if-eqz v5, :visualize_fallback
    iget-boolean v5, v4, Ll07;->d:Z
    if-nez v5, :visualize_fallback
    invoke-static {v10, v14}, Llocal/visualize/VisualSpec;->parse(Ljava/lang/String;Ljava/lang/String;)Llocal/visualize/VisualSpec;
    move-result-object v5
    if-eqz v5, :visualize_fallback
    const v8, 0x43b1882a
    invoke-virtual {v7, v8}, Lv15;->f0(I)V
    invoke-static {v5, v11, v7, v2}, Llocal/visualize/VisualCompose;->render(Llocal/visualize/VisualSpec;Lfk7;Lv15;Lk;)V
    invoke-virtual {v7, v13}, Lv15;->q(Z)V
    invoke-virtual {v7, v13}, Lv15;->q(Z)V
    goto/16 :goto_10

    :visualize_fallback
''' + anchor
    hook = previous_hook.replace(
        "    # Render only completed fences where the assistant's renderer enables rich blocks.\n"
        "    if-eqz v9, :visualize_fallback\n",
        "    # Only the assistant's rich renderer replaces visualize fences.\n").replace(
        "{v10, v14}, Llocal/visualize/VisualSpec;->parse(Ljava/lang/String;Ljava/lang/String;)",
        "{v10, v14, v9}, Llocal/visualize/VisualSpec;->forReply(Ljava/lang/String;Ljava/lang/String;Z)")
    target = smali / "g07.smali"
    legacy = previous_hook.replace("{v5, v11, v7, v2}", "{v5, v11, v7}").replace(
        "Lfk7;Lv15;Lk;)V", "Lfk7;Lv15;)V")
    patch(target, anchor, hook, previous=previous_hook if previous_hook in target.read_text(encoding="utf-8") else legacy)
    anchor = "    iget-object v8, p2, Lqy1;->c:Ljava/lang/String;"
    patch(smali / "oy1.smali", anchor, anchor + "\n\n"
          "    invoke-static {v8, p2}, Llocal/visualize/VisualRequest;->expand(Ljava/lang/String;Lqy1;)Ljava/lang/String;\n"
          "    move-result-object v8", previous=anchor + "\n\n"
          "    invoke-static {v8}, Llocal/visualize/VisualPrompt;->expand(Ljava/lang/String;)Ljava/lang/String;\n"
          "    move-result-object v8")
    anchor = "    iget-object v5, p2, Lrx1;->c:Ljava/lang/String;"
    patch(smali / "px1.smali", anchor, anchor + "\n\n"
          "    invoke-static {v5}, Llocal/visualize/VisualPrompt;->expand(Ljava/lang/String;)Ljava/lang/String;\n"
          "    move-result-object v5")
    # Request fragments are also used when the service echoes a prompt in history.
    for register in ("p3", "p4"):
        anchor = f"    iput-object {register}, p0, Llfa;->c:Ljava/lang/String;"
        patch(smali / "lfa.smali", anchor,
              f"    invoke-static {{{register}}}, Llocal/visualize/VisualPrompt;->original(Ljava/lang/String;)Ljava/lang/String;\n"
              f"    move-result-object {register}\n\n" + anchor)
    bridge = smali / "local" / "visualize"
    bridge.mkdir(parents=True, exist_ok=True)
    for source in SOURCE.glob("*.smali"):
        shutil.copyfile(source, bridge / source.name)


def compile_dex():
    assets = BUILD / "VisualAssets.java"
    data = {"PROTOCOL": (SOURCE / "protocol.txt").read_text(encoding="utf-8")}
    for name in ("forecast", "quiz", "table", "gallery", "static", "media", "reference", "workspace"):
        example = json.loads((SOURCE / "examples" / f"{name}.json").read_text(encoding="utf-8"))
        data[name.upper()] = json.dumps(example, ensure_ascii=False, separators=(",", ":"))
    constants = "\n".join(f"    static final String {key} = {json.dumps(value, ensure_ascii=False)};"
                          for key, value in data.items())
    assets.write_text(f"package local.visualize;\nfinal class VisualAssets {{\n{constants}\n}}\n", encoding="utf-8")
    dex = BUILD / "visualize-dex.zip"
    with tempfile.TemporaryDirectory(prefix="classes-", dir=BUILD) as output:
        classes = Path(output)
        run("javac", "--release", "8", "-encoding", "UTF-8", "-classpath", PLATFORM, "-d", classes,
            *sorted((SOURCE / "src").glob("*.java")), assets)
        run("java", "-cp", TOOLS / "lib" / "d8.jar", "com.android.tools.r8.D8", "--min-api", "24",
            "--lib", PLATFORM, "--output", dex, *sorted(classes.rglob("*.class")))
    return dex


def sign_apk(aligned, signed, key, alias, password):
    signer = TOOLS / "lib" / "apksigner.jar"
    run("java", "-jar", signer, "sign", "--ks", key, "--ks-key-alias", alias,
        "--ks-pass", password, "--key-pass", password, "--v4-signing-enabled", "false",
        "--out", signed, aligned)
    run("java", "-jar", signer, "verify", "--verbose", signed)


def main():
    parser = argparse.ArgumentParser(description="Build DeepSeek Visualize from the extracted APK")
    parser.add_argument("--release-keystore", type=Path, help="PKCS12 keystore with the visualize-release alias")
    parser.add_argument("--output", type=Path, help="Destination APK for a release build")
    args = parser.parse_args()
    if bool(args.release_keystore) != bool(args.output):
        parser.error("--release-keystore and --output must be used together")
    if args.release_keystore:
        release_key = args.release_keystore.resolve()
        release_output = args.output.resolve()
        if not release_key.is_file() or not os.environ.get(RELEASE_PASSWORD_ENV):
            parser.error(f"Release keystore and {RELEASE_PASSWORD_ENV} are required")
        if release_output.exists() or not release_output.parent.is_dir():
            parser.error("Release output must be a new file in an existing directory")
    if hashlib.sha256(APK.read_bytes()).hexdigest() != APK_SHA256:
        raise ValueError("Original APK does not match the extracted DeepSeek 2.7.5 build")
    BUILD.mkdir(parents=True, exist_ok=True)
    if not (DECODED / "apktool.yml").is_file():
        run("java", "-jar", APKTOOL, "d", "-o", DECODED.relative_to(ROOT), APK.relative_to(ROOT))
    dex = compile_dex()
    patch_apk()
    unsigned = BUILD / "unsigned.apk"
    # Apktool's incremental build can reuse a manifest after apktool.yml changes.
    run("java", "-jar", APKTOOL, "b", "--force", "-o", unsigned.relative_to(ROOT), DECODED.relative_to(ROOT))
    with zipfile.ZipFile(dex) as new_dex, zipfile.ZipFile(unsigned, "a") as apk:
        if "classes4.dex" in apk.namelist():
            raise ValueError("Unexpected classes4.dex in rebuilt APK")
        apk.writestr("classes4.dex", new_dex.read("classes.dex"), compress_type=zipfile.ZIP_DEFLATED)
        apk.write(SOURCE / "assets" / "world-land.bin", "assets/visualize-world-land.bin", zipfile.ZIP_DEFLATED)
    aligned = BUILD / "aligned.apk"
    run(TOOLS / "zipalign.exe", "-P", "16", "-f", "4", unsigned.relative_to(ROOT), aligned.relative_to(ROOT))
    if args.release_keystore:
        with tempfile.TemporaryDirectory(prefix=".visualize-release-", dir=release_output.parent) as temp:
            staged = Path(temp) / release_output.name
            sign_apk(aligned, staged, release_key, "visualize-release", f"env:{RELEASE_PASSWORD_ENV}")
            staged.rename(release_output)
        print(release_output)
        return

    key = BUILD / "local-test.p12"
    if not key.exists():
        run("keytool", "-genkeypair", "-storetype", "PKCS12", "-keystore", key, "-alias", "visualize-local",
            "-storepass", "android", "-keypass", "android", "-dname", "CN=DeepSeek Visualize Local Test",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650")
    sign_apk(aligned, ROOT / "DeepSeek-Visualize-2.7.5-local.apk", key, "visualize-local", "pass:android")


if __name__ == "__main__":
    main()
