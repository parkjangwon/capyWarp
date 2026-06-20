# Agent Notes

For Google Play releases, use the local Codex skill and repo guide:

`/Users/pjw/.codex/skills/play-console-release`

`docs/play-console-release.md`

Important CapyWarp release facts:

- Package name: `org.parkjw.capywarp`
- Play release can be done through the Google Play Android Developer API when browser upload is blocked.
- Keep release signing secrets out of git. This project supports `CAPYWARP_SIGNING_PROPERTIES_FILE` for reading an external local properties file during release builds.
- If Play Console reports a 16KB memory page size blocker, update/verify AGP first. `v1.1.4` fixed the blocker with AGP `8.5.2` and `versionCode 7`.
