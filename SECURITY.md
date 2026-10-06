# Security policy

## Reporting a vulnerability

Please report it privately instead of opening a public issue: on the repository's
**Security** tab, choose **Report a vulnerability**
([direct link](https://github.com/srjohnson1986/soundboard/security/advisories/new)).

Include what you found, how to reproduce it, and which version or platform (Android, web
or iOS) it affects. You can expect a first reply within a week. This is a small project
maintained by one person, so fixes come as quickly as that allows.

## What's worth reporting

Soundboard keeps everything on the device (or in the browser's own storage), has no
account and no server, and asks for the microphone only when you record. The things
that would matter are:

- A backup zip, or a board file, that can write outside where it should, or crash or
  hang the app when imported (the app reads zips that other people can send).
- Anything that makes a board lose its data, or that lifts the caregiver lock without
  the 3-second hold.
- Secrets or personal data committed to the repository, or a CI workflow that can be
  made to leak the release signing key.

## Supported versions

Only the [latest release](https://github.com/srjohnson1986/soundboard/releases/latest)
gets fixes.
