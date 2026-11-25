# Releasing

Releases are signed APKs attached to a GitHub release. The signing key never enters the repository.

## One-time: create the signing key

Keep the key file and its passwords somewhere safe and backed up (a password manager). If the key is lost,
installed copies can no longer be updated; users would have to uninstall and reinstall.

```bash
keytool -genkeypair -v -keystore ~/keys/liveisland-release.jks -alias liveisland \
    -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the project root (ignored by Git):

```properties
storeFile=/Users/you/keys/liveisland-release.jks
storePassword=…
keyAlias=liveisland
keyPassword=…
```

## Each release

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`; move the `Unreleased` section of
   `CHANGELOG.md` under the new version.
2. `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`
3. Install `app/build/outputs/apk/release/app-release.apk` on a phone and check the home screen, an app's
   page and the test Live Update.
4. Tag and publish:

   ```bash
   cp app/build/outputs/apk/release/app-release.apk LiveIsland-0.1.0.apk
   git tag v0.1.0 && git push origin v0.1.0
   gh release create v0.1.0 LiveIsland-0.1.0.apk --title "LiveIsland 0.1.0" --notes-file <notes>
   ```

5. Publish the APK's SHA-256 (`shasum -a 256 LiveIsland-0.1.0.apk`) in the release notes.
