```
   *     .  .  .     *    .  .  .   *   . .  .  .
      .  .  *  .  .  .  *   . .  .   . .  * .  .
 .  .  .  .  .  *  .  .  .  .  *    .  . .  .  .
      *  . ❄ .  .  *  .  .  *  .  .  .  *  . .  *
   .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .
  .  *  .  .  .  *  .  .  ❄ .  .  *  .  .  .  * .
      .  . ❄ .  .  .  *  .  .  .  *   .  .  ❄ .
.  *  .  .  .  .  .  .  .  .  .  .  .  .  .  .  .  *
   .  .  .  *  .  ❄ .  .  *  .  .  ❄ .  .  *  .
      ❄ .  .  .  *  .  .  .  .  .  .  *  .  .  .
 .  .  .  .  .  .  .  ❄ .  .  *  .  .  .  .  ❄ .
   .  *  .  .  .  .  .  .  .  .  .  .  .  *  .  .
```

# Vygga — Decentralized P2P Messenger over Yggdrasil

Vygga is a **serverless, peer-to-peer messaging app for Android** that communicates over the [Yggdrasil](https://yggdrasil-network.github.io/) IPv6 mesh network. No central servers, no internet access, no phone numbers — just direct encrypted peer-to-peer messaging.

Built with **ClojureScript** (Reagent + re-frame) on React Native/Expo, with a **Go native module** (yggstack via gomobile) providing full Yggdrasil node capabilities directly on the device.

## Features

- **P2P Messaging** — Send and receive messages directly between devices over the Yggdrasil mesh
- **Cryptographic Identity** — Ed25519 keypair generated on-device; messages are signed and verified via tweetnacl
- **Contact Management** — Add contacts by Yggdrasil IPv6 address; unknown senders auto-create contacts
- **Secure Storage** — Identity (private key) persisted via expo-secure-store (Android Keystore)
- **Background Operation** — Android foreground service keeps the messenger running to receive messages
- **Push Notifications** — expo-notifications alerts for incoming messages
- **Battery Optimization** — Settings screen to disable battery saving for the app
- **REPL-Driven Development** — Live code iteration via shadow-cljs nREPL against the running Android device

## Tech Stack

| Layer | Technology | Role |
|---|---|---|
| Application Logic | [ClojureScript](https://clojurescript.org/) 1.12 | Reagent + re-frame |
| UI Framework | [Reagent](https://reagent-project.github.io/) 2.0.1 | Declarative React Native components |
| State Management | [re-frame](https://github.com/Day8/re-frame) 1.4.7 | Event-driven state |
| CLJS Build | [shadow-cljs](https://shadow-cljs.github.io/) 3.4 | Hot-reload, nREPL, release builds |
| Mobile Shell | [React Native](https://reactnative.dev/) 0.86 + [Expo](https://expo.io/) SDK 57 | Cross-platform runtime |
| Navigation | [React Navigation](https://reactnavigation.org/) 7 | Screen navigation |
| Crypto | [tweetnacl](https://tweetnacl.js.org/) 1.0.3 | Ed25519 signing/verification |
| Network | [yggstack](https://github.com/DrewCyber/yggstack) (Go/gomobile AAR) | Yggdrasil node + SOCKS5 proxy |

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   ClojureScript                     │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ re-frame │  │  Reagent │  │ CLJS Bridge       │  │
│  │ events/  │  │  Views   │  │ yggstack.cljs     │  │
│  │ subs     │  │          │  │ messenger.cljs    │  │
│  └────┬─────┘  └──────────┘  └───────┬───────────┘  │
│       │                              │              │
├───────┼──────────────────────────────┼──────────────┤
│       │          React Native        │              │
│       │     ┌────────────────────────▼──────────┐   │
│       │     │   YggstackModule.java (JNI bridge)│   │
│       │     │   YggstackPackage.java            │   │
│       │     └─────────────────────┬─────────────┘   │
├───────┼───────────────────────────┼─────────────────┤
│       │       Native Layer        │                 │
│       │  ┌────────────────────────▼───────────────┐ │
│       │  │  yggstack.aar (gomobile Go bindings)   │ │
│       │  │  - Yggdrasil P2P node                  │ │
│       │  │  - SOCKS5 proxy (127.0.0.1:1080)       │ │
│       │  │  - Remote TCP port forwarding          │ │
│       │  │  - Messenger TCP server (port 7777)    │ │
│       │  └────────────────────────────────────────┘ │
└───────┴─────────────────────────────────────────────┘
```

Messages are JSON-serialized, Ed25519-signed, and sent through the SOCKS5 proxy to the recipient's Yggdrasil IPv6 address on port 7777. The receiving device exposes a local TCP server via Yggdrasil remote port forwarding and emits incoming messages as React Native events.

## Prerequisites

- **Java** 11+ (for shadow-cljs)
- **Node.js** 18+ and npm
- **Android Studio** with SDK (API 24+) and NDK
- **Go** 1.20+ (only if rebuilding the yggstack AAR)
- **Clojure CLI tools** (optional, for linting/formatting)

## Getting Started

```bash
# 1. Install JS dependencies
npm i

# 2. Build yggstack AAR (one-time, or after yggstack changes)
cd vendor/yggstack
ANDROID_HOME=$HOME/Library/Android/sdk ./mobile/build-android.sh
cd ../..

# 3. Generate native Android project + copy AAR
npx expo prebuild

# 4. Start shadow-cljs watch (hot-reload + nREPL)
npx shadow-cljs watch app

# 5. Build and install on device (in another terminal)
npx expo run:android
```

The app will launch on your connected Android device. Open the app and tap **Start** on the Settings screen to begin the Yggdrasil node.

## REPL-Driven Development

Vygga supports live code evaluation against a running Android device via shadow-cljs nREPL. See [AGENTS.md](./AGENTS.md) for the full workflow, including multi-device targeting.

Quick check:
```bash
echo '@(re-frame.core/subscribe [:yggstack/status])' | bb scripts/nrepl_eval.clj
```

## Project Structure

```
src/main/example/      # ClojureScript application source
  app.cljs             # Entry point, screens, navigation
  db.cljs              # re-frame app-db schema
  events.cljs          # Event handlers (Yggdrasil + messenger)
  subs.cljs            # Subscriptions
  widgets.cljs         # Reusable UI components
  yggstack.cljs        # CLJS bridge to Yggdrasil native module
  messenger.cljs       # CLJS bridge for TCP message server
  crypto.cljs          # Ed25519 signing/verification
  storage.cljs         # expo-secure-store key persistence
  notifications.cljs   # expo-notifications setup
vendor/yggstack/       # Yggdrasil Go library + build scripts
plugins/               # Expo config plugin (native module generation)
scripts/nrepl_eval.clj # Babashka nREPL eval client
```

## Production Build

```bash
npx shadow-cljs release app
npx expo run:android --no-build
```

Or use EAS Build with the included `eas-build-pre-install.sh` script (installs JDK 11 on macOS runners for shadow-cljs).

## Acknowledgments

This project is built on the [rn-rf-shadow](https://github.com/PEZ/rn-rf-shadow) template — the fastest way to get ClojureScript + React Native + Reagent + re-frame up and running. The Yggdrasil integration uses [yggstack](https://github.com/DrewCyber/yggstack/tree/mobile-bindings-ai), Go bindings for the [Yggdrasil Network](https://yggdrasil-network.github.io/).
