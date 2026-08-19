# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Android application using Kotlin, Jetpack Compose, Material 3, Coroutines, Flow, Navigation Compose, Hilt, Room, DataStore, Foreground Service, Android Keystore, and a Go Mobile AAR built from the existing WARPSCOUT codebase.

## Users

Technical Android users who need to register a Cloudflare WARP account, find working endpoints, inspect scan quality, generate client configuration, or run a local SOCKS endpoint without using a desktop CLI.

## Product Purpose

WARPSCOUT for Android exposes the existing WARPSCOUT operations through a native Android interface. Registration, scanning, discovery, configuration rendering, and SOCKS operation run locally on the phone. Success means that the Android results and exports match the CLI for the same inputs while remaining usable during background execution.

## Positioning

The application is an independent OpenWarpKit project that reuses the WARPSCOUT scanning engine rather than reimplementing its network behavior in Kotlin or delegating scans to a server.

## Operating Context

Users work on unreliable or filtered networks, may switch between direct registration and a relay fallback, and need visible progress during operations that continue after the application is minimized. They may import an existing account JSON or create a new account on first launch.

## Capabilities and Constraints

- Sections: Scan, History, Tools, and Settings.
- Operations: register, scan, find-junk, find-sni, SOCKS, and WARP-in-WARP.
- Protocols: WireGuard, AmneziaWG, MASQUE H3, and MASQUE H2.
- Presets: Standard, Durable, and Full, with a separate Expert mode.
- Local storage only. Accounts and saved configurations use Android Keystore backed encryption.
- Secrets must not enter Room, DataStore, logs, analytics, or error reports.
- One active operation at a time through a foreground service.
- Russian and English user interfaces.
- Android 8.0 and later, with arm64-v8a, armeabi-v7a, and x86_64 native libraries.
- Application ID: `io.github.openwarpkit.warpscout`.
- The base relay behavior and registration chain remain compatible with upstream.
- Android releases use `android-vMAJOR.MINOR.PATCH` tags independently of CLI releases.

## Brand Commitments

The product name is WARPSCOUT for Android. The interface and documentation are strict, direct, and technical. No emoji, decorative badges, inflated claims, or language implying official maintenance by the upstream author. OpenWarpKit is credited for the Android application and changes. Nikita S. (@vernette) remains credited as the original WARPSCOUT author.

## Evidence on Hand

The repository contains the original Go CLI, its tests, MIT License, reports, configuration renderers, protocol implementations, and upstream credits. No Android screenshots or public mobile release evidence exists yet and none may be fabricated.

## Product Principles

- Keep the phone as the execution boundary for accounts, scans, and results.
- Preserve CLI behavior and configuration parity through one Go core.
- Show operation state, recovery action, and data ownership clearly.
- Keep common scans simple without hiding expert controls.
- Attribute upstream work precisely and avoid implying endorsement.

## Accessibility & Inclusion

Use Material 3 semantics, 48 dp minimum touch targets, system font scaling, edge-to-edge insets, predictive back, dark theme, and adaptive navigation for phones and tablets.
