# Claude Notes

For Google Play releases, follow the repo guide and local Codex skill:

`docs/play-console-release.md`

`/Users/pjw/.codex/skills/play-console-release`

Short version:

- Build a signed AAB without committing secrets.
- Use Google Play Android Developer API edits to upload, update the production track, validate, and commit.
- CapyWarp package name is `org.parkjw.capywarp`.
- If a 16KB page-size error appears, check AGP and bump `versionCode` before retrying. `v1.1.4` fixed this with AGP `8.5.2`.
