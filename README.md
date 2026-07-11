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

# Vygga — Decentralized P2P Messenger / VoIP over Yggdrasil

> **⚠️ Alpha / Experimental** — Vygga is in early development and alpha testing. Features may be incomplete, unstable, or change significantly. Use at your own risk.

Vygga is a **serverless, peer-to-peer messaging app for Android** that communicates over the [Yggdrasil](https://yggdrasil-network.github.io/) IPv6 mesh network. No central servers, no internet access, no phone numbers — just direct peer-to-peer messaging with Ed25519-signed messages.

Built with **ClojureScript** (Reagent + re-frame) on React Native/Expo, with a **Go native module** (yggstack via gomobile) providing full Yggdrasil node capabilities directly on the device.

## Features

- **P2P Messaging** — Send and receive messages directly between devices over the Yggdrasil mesh (messages are ephemeral — only last 5 messages persisted across app restart)
- **Ed25519 Signing** — All messages are signed with tweetnacl detached signatures; unknown senders auto-create contacts; public key mismatch detection prevents impersonation
- **Audio Calls** — Full peer-to-peer audio calls: Ed25519-signed signaling (offer/accept/reject/end), 16kHz PCM audio captured via expo-audio, binary audio frames over SOCKS5 through Yggdrasil to port 7778
- **Contact Management** — Add contacts by Yggdrasil IPv6 address; contacts persisted via expo-secure-store; unread message count badges; message resend on failure
- **Cryptographic Identity** — Ed25519 keypair generated on-device (via yggstack Go bindings); identity (private key) persisted via expo-secure-store (Android Keystore); ability to regenerate identity
- **Background Operation** — Android foreground service (`YggdrasilService.java`) keeps the messenger running to receive messages and calls
- **Push Notifications** — expo-notifications alerts for incoming messages and calls with separate notification channels
- **Battery Optimization** — Settings screen to disable battery saving for the app
- **Dark/Light Theme** — Toggle between dark and light color schemes in settings
- **Clipboard Integration** — Tap to copy your Yggdrasil IPv6 address
- **Network Resilience** — Monitors connectivity via `@react-native-community/netinfo` and auto-retries peers when network is restored
- **Gracious Exit** — Cleanly stops Yggdrasil and foreground service on app exit
- **Navigation Persistence** — Navigation state saved/restored across app restarts
- **REPL-Driven Development** — Live code iteration via shadow-cljs nREPL against the running Android device

## Tech Stack

| Layer | Technology | Role |
|---|---|---|
| Application Logic | [ClojureScript](https://clojurescript.org/) 1.12 | Reagent + re-frame |
| UI Framework | [Reagent](https://reagent-project.github.io/) 2.0.1 | Declarative React Native components |
| State Management | [re-frame](https://github.com/Day8/re-frame) 1.4.7 | Event-driven state |
| CLJS Build | [shadow-cljs](https://shadow-cljs.github.io/) 3.4.11 | Hot-reload, nREPL, release builds |
| Mobile Shell | [React Native](https://reactnative.dev/) 0.86 + [Expo](https://expo.io/) SDK 57 | Cross-platform runtime |
| Navigation | [React Navigation](https://reactnavigation.org/) 7 | Screen navigation |
| Crypto | [tweetnacl](https://tweetnacl.js.org/) 1.0.3 | Ed25519 signing/verification |
| Network | [yggstack](https://github.com/DrewCyber/yggstack) (Go/gomobile AAR) | Yggdrasil node + SOCKS5 proxy |
| Audio Calls | [expo-audio](https://docs.expo.dev/versions/latest/sdk/audio/) | 16kHz PCM capture + native playback |
| TCP Sockets | [react-native-tcp-socket](https://github.com/aprock/react-native-tcp-socket) | SOCKS5 client + TCP/audio servers |
| Connectivity | [@react-native-community/netinfo](https://github.com/react-native-netinfo/react-native-netinfo) | Network state monitoring |
| Secure Storage | expo-secure-store | Key/contacts persistence (Android Keystore) |
| Notifications | expo-notifications | Incoming message/call alerts |
| Clipboard | expo-clipboard | Copy IPv6 address |
| Battery | react-native-battery-optimization-check | Battery optimization settings |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ClojureScript                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐ │
│  │ re-frame │  │  Reagent │  │  VoIP        │  │  CLJS Bridge     │ │
│  │ events/  │  │  Views   │  │  voip.cljs   │  │  yggstack.cljs   │ │
│  │ subs     │  │          │  │  voip_conn   │  │  messenger.cljs  │ │
│  └────┬─────┘  └──────────┘  └──────┬───────┘  └─────-──┬─────────┘ │
│       │                             │                   │           │
├───────┼─────────────────────────────┼───────────────────┼───────────┤
│       │         React Native        │                   │           │
│       │    ┌────────────────────────▼───────────────────▼───────┐   │
│       │    │  Native Modules (Java)                             │   │
│       │    │  YggstackModule.java — JNI to Go bindings          │   │
│       │    │  YggdrasilService.java — foreground service        │   │
│       │    │  AudioTrackModule.java — PCM audio playback        │   │
│       │    │  YggdrasilManager.java — singleton core manager    │   │
│       │    └─────────────────────┬──────────────────────────────┘   │
├───────┼──────────────────────────┼──────────────────────────────────┤
│       │      Native Layer        │                                  │
│       │ ┌─────────────────-──────▼──────────────────────────────┐   │
│       │ │  yggstack.aar (gomobile Go bindings)                  │   │
│       │ │  - Yggdrasil P2P node                                 │   │
│       │ │  - SOCKS5 proxy (127.0.0.1:1080)                      │   │
│       │ │  - Remote TCP port forwarding                         │   │
│       │ └────────────────────────────────────────────────────-──┘   │
└───────┴─────────────────────────────────────────────────────-───────┘
```

### Two TCP Servers

| Port | Server | Purpose |
|---|---|---|
| 7777 | `tcp_server.cljs` | Receives EDN-encoded messages and call signals from the mesh |
| 7778 | `audio_server.cljs` | Receives binary audio frames (PCM) during active calls |

Both are exposed via Yggdrasil remote TCP port forwarding and accessed by peers through the SOCKS5 proxy.

## Message Protocol

Messages are **EDN-serialized** (not JSON), delimited by newlines, and signed with Ed25519 detached signatures via tweetnacl:

```clojure
{:type    "message"       ;; "message" | "call-signal"
 :from    "201:1234::1"   ;; sender's Yggdrasil IPv6
 :text    "Hello!"        ;; message body
 :id      "uuid-str"      ;; unique message id
 :ts      1234567890      ;; unix timestamp
 :pubkey  "hex-string"    ;; sender's Ed25519 public key
 :sig     "base64-str"}   ;; detached signature over text|id|ts
```

Call signals carry additional fields:

```clojure
{:type      "call-signal"
 :call-type "offer"        ;; "offer" | "accept" | "reject" | "end"
 :call-id   "uuid-str"
 :to        "201:1234::1"  ;; recipient
 :from      "201:5678::1"  ;; sender
 :ts        1234567890
 :pubkey    "hex-string"
 :sig       "base64-string"}  ;; signed over call-signal|call-type|call-id|ts
```

Audio frames use a binary protocol on port 7778:

```
┌─────────┬─────────┬──────────────────┐
│4-byte BE│4-byte BE│    PCM data      │
│payload  │sequence │  (variable len)  │
│length   │number   │   (int16 mono)   │
└─────────┴─────────┴──────────────────┘
```

Audio is captured at 16 kHz, 16-bit signed PCM (mono) via expo-audio, and played back through native `AudioTrackModule.java`.

## Prerequisites

- **Java** 11+ (for shadow-cljs)
- **Node.js** 18+ and npm
- **Android SDK** (API 24+) and NDK — via [Android command-line tools](https://developer.android.com/tools) or Android Studio
- **Go** 1.20+ (only if rebuilding the yggstack AAR; ensure `$(go env GOPATH)/bin` is on your `$PATH` for `gomobile`)
- **Clojure CLI tools** (optional, for linting/formatting)

## Getting Started

```bash
# 1. Install JS dependencies
npm i

# 2. Build yggstack AAR (one-time, or after yggstack changes)
cd vendor/yggstack
export PATH="$(go env GOPATH)/bin:$PATH"
ANDROID_HOME=$HOME/Library/Android/sdk ./mobile/build-android.sh
cd ../..

# 3. Generate native Android project + copy AAR
npx expo prebuild

# 4. Start shadow-cljs watch (hot-reload + nREPL)
npx shadow-cljs watch app

# 5. Build and install on device (in another terminal)
npx expo run:android
```

The app will launch on your connected Android device. Open the app and follow the prompts to generate an identity, then tap **Start Yggdrasil** on the Settings screen.

## Expo Plugins

Vygga includes 4 custom Expo plugins applied during prebuild:

| Plugin | Purpose |
|---|---|
| `plugins/withYggstack.js` | Copies yggstack.aar into android/libs, registers permissions and YggdrasilService in AndroidManifest |
| `plugins/withAudioTrack.js` | Registers AudioTrackModule Java native module |
| `plugins/withNotificationSound.js` | Copies custom notification sound assets |
| `plugins/withBuildVariants.js` | Configures build variant support |

## REPL-Driven Development

Vygga supports live code evaluation against a running Android device via shadow-cljs nREPL. See [AGENTS.md](./AGENTS.md) for the full workflow, including multi-device targeting.

Quick check:
```bash
echo '@(re-frame.core/subscribe [:yggstack/status])' | bb scripts/nrepl_eval.clj
```

## Production Build

```bash
npx shadow-cljs release app
npx expo run:android --no-build
```

## Acknowledgments

This project is built on the [rn-rf-shadow](https://github.com/PEZ/rn-rf-shadow) template — the fastest way to get ClojureScript + React Native + Reagent + re-frame up and running. The Yggdrasil integration uses [yggstack](https://github.com/DrewCyber/yggstack/tree/mobile-bindings-ai), Go bindings for the [Yggdrasil Network](https://yggdrasil-network.github.io/).
