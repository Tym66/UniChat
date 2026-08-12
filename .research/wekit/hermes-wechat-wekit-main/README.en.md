# hermes-wechat-wekit

中文: [README.md](README.md)

A [Hermes Agent](https://github.com/NousResearch) platform plugin that gives the agent a **real WeChat account** — private chats, group chats, images, contacts — by talking to the [WeKit](https://github.com/Ujhhgtg/WeKit) Xposed module running inside WeChat on a rooted Android phone. No UI automation, no notification scraping.

> **Read this before you run it.**
> 1. **Automating a personal WeChat account violates WeChat's Terms of Service, and a ban also freezes WeChat Pay.** Use a dedicated secondary account. See [Legal / Terms of Service](#legal--terms-of-service).
> 2. **Inbound is edge-triggered and messages can be lost permanently.** This is upstream WeKit behaviour that no plugin can fully fix. See [Known limitations](#known-limitations).

---

## Why this exists

Every other route to "agent on WeChat" is slow, dead, or dangerous:

| Approach | Status |
|---|---|
| UI automation (screenshots + taps, accessibility tree) | Works, but slow, brittle, and structurally lossy — WeChat suppresses notifications for the conversation currently open in the foreground, so messages get silently dropped |
| WeChatFerry / desktop-client hooks | Upstream archived; recent WeChat desktop builds refuse to log in for hooked clients |
| iPad / third-party protocol implementations | Highest ban rate of any route; treated as unusable by the research behind this project |
| **WeKit (this project)** | Hooks WeChat's own **WCDB database-insert layer** on Android. Messages are read where WeChat itself writes them, so nothing depends on notifications or on-screen state |

WeKit exposes an HTTP REST API plus a native MCP server (default port `3001`). This plugin registers `wechat-wekit` as a first-class Hermes platform, next to Telegram and Discord, with zero changes to Hermes core.

## Architecture

```
┌────────────────────────────────────────────────┐
│ Hermes Agent (gateway)                         │
│   ~/.hermes/plugins/wechat-wekit/              │
│     outbound → REST  POST /api/messages/text   │
│                      POST /api/messages/image  │
│     inbound  ← MCP   POST /mcp                 │
│                      tools/call                │
│                        wait-for-new-message    │  (long poll)
└───────────────────┬────────────────────────────┘
                    │  plaintext HTTP + Bearer token, over your LAN
                    │  (different subnets? router DNAT — see Transport)
                    v
┌────────────────────────────────────────────────┐
│ Rooted Android phone                           │
│                                                │
│   WeChat (com.tencent.mm)                      │
│     └── WeKit Xposed module                    │
│           ├── REST  :3001/api/...              │
│           ├── MCP   :3001/mcp                  │
│           └── hooks WCDB insertWithOnConflict  │
│                        ▲                       │
│                        └── every inbound message
│                            lands here first    │
└────────────────────────────────────────────────┘
```

Inbound flow: the poll loop calls `wait-for-new-message`, parses WeKit's formatted result (`ConvId='…',Sender='…',Type=N,Content='…'`), and hands the message to Hermes as a **background asyncio task** so the loop can immediately re-arm the listener. Outbound flow: plain REST calls.

## Repository layout

| Path | What it is |
|---|---|
| `plugin/` | `__init__.py`, `adapter.py`, `actions.py`, `plugin.yaml` — the plugin itself; copy into `~/.hermes/plugins/wechat-wekit/`. `adapter.py` is the message stream, `actions.py` the [action tools](#action-tools) |
| `phone-script/hermes-media-bridge.js` | WeKit JS-engine script that runs on the phone; required to receive the actual files/images/voice (see below) |
| `transport/router-dnat/wekit-dnat.sh` | Router-side DNAT script for the recommended WiFi transport |
| `ops/wechat_watchdog.py` | Optional keepalive for the phone side (HTTP probe, adb only on failure) |
| `.env.example` | Every environment variable with comments |
| `docs/` | Phone setup, architecture, troubleshooting |

## Requirements

| Component | Requirement |
|---|---|
| Phone | Android device with **root** (reference deployment: Pixel 9 Pro, Android 16, Magisk 30.7 with Zygisk) |
| Xposed framework | LSPosed or a maintained successor. On Android 16 the reference deployment used **Vector** (`JingMatrix/Vector`) — upstream LSPosed's last release predates Android 16 |
| Module | [WeKit](https://github.com/Ujhhgtg/WeKit), enabled with WeChat in scope, with its **API + MCP server** turned on (port `3001`, your own bearer token) |
| WeChat | A version WeKit supports — check `WeChatVersions.kt` upstream. The reference deployment ran **8.0.72** (the highest constant present there at the time) |
| Agent | A working Hermes Agent install with the plugin system (`gateway.platforms.base` must be importable) |
| Python | `httpx` in the Hermes venv |
| Account | **A dedicated secondary WeChat account.** WeChat allows one active session per account, so whatever account the agent uses will log your own phone out of it |

WeKit publishes no tagged releases (CI artifacts only) and is effectively a single-maintainer project. A WeChat update can break it at any time. Plan accordingly.

## Install

### 1. Set up the phone

Root the device, install an Xposed framework, install the WeKit module, enable it with WeChat in scope, apply, and restart WeChat. Confirm from `logcat` that the module actually loaded into `com.tencent.mm` (you should see the framework loading Xposed for that package and WeKit's own hook lines) — a module that is enabled in the manager but not loaded looks identical from the outside.

Three settings matter and are easy to miss:

- **Disable WeChat's hot-update (tinker).** A hot-patched WeChat can make the module fail to load *silently*.
- **Enable WeKit's anti-Xposed-detection option** if your build offers one.
- **Change the default API token.** WeKit ships a placeholder literal; leaving it is equivalent to no auth at all.

Then, inside WeChat, open WeKit's settings and enable the **API + MCP server**: set the port (`3001`) and your token.

> The first start after enabling the module runs a full DexKit scan and can take **several minutes** before `:3001` begins listening. That is normal — don't call it a failure yet.

Also exempt WeChat from battery optimisation / doze on that device, so the OS doesn't kill the process holding the server.

### 2. Make `:3001` reachable from the agent host

| Transport | Recommendation |
|---|---|
| **Same subnet** | Simplest. Point `WEKIT_BASE_URL` straight at the phone's IP (`http://192.168.1.60:3001`). Nothing else to install |
| **Different subnets → router DNAT** | **Recommended when needed.** See [Transport](#transport) |
| **USB (`adb forward`)** | **Not recommended.** See [Transport](#transport) for the measurements |

### 3. Install the plugin

```bash
mkdir -p ~/.hermes/plugins/wechat-wekit
cp plugin/__init__.py plugin/adapter.py plugin/actions.py plugin/plugin.yaml \
   ~/.hermes/plugins/wechat-wekit/
```

Enable it in `~/.hermes/config.yaml`:

```yaml
plugins:
  enabled:
    - wechat-wekit-platform      # ← the plugin.yaml `name`
```

Note the two identifiers: the **plugin** is `wechat-wekit-platform`; the **platform** it registers is `wechat-wekit` (that is what appears in logs and what you use as a cron `deliver=` target). If you previously ran a phone-UI WeChat adapter against the same account, disable it — two adapters on one account produce double sends and echo loops.

### 4. Configure

Put these in your Hermes `.env` (see `.env.example`):

```bash
WEKIT_TOKEN=YOUR_TOKEN
WEKIT_BASE_URL=http://192.168.1.50:3001
WEKIT_ALLOWED_USERS=wxid_xxxxxxxx
WEKIT_ALLOW_ALL_USERS=false
```

### 5. Verify

Before restarting the gateway, prove the link **from the agent host** (not from your laptop — the agent host is the one that matters):

```bash
curl -s http://192.168.1.50:3001/api/self/info \
     -H "Authorization: Bearer YOUR_TOKEN"
# → {"wxId":"wxid_xxxxxxxx","customWxId":"..."}
```

A `200` with the logged-in account's ids means the whole path — router, phone, WeChat process, WeKit server, token — is good. This is the same endpoint the adapter probes on connect (4 attempts, 1.5 s apart, then the platform gives up with `cannot reach WeKit API at …`).

Then restart the gateway and watch for `wechat-wekit: connected to …`, followed by `wechat-wekit: poll alive (N rounds, no new msg)` — emitted once every 5 poll rounds. Healthy means recurring `poll alive` and **no** `poll error` lines.

> **Plugin logs may not be in `gateway.log`.** In the reference Hermes build the gateway log filter only accepts `gateway.*` loggers, while plugin adapters log under `hermes_plugins.*` — everything from this adapter went to `agent.log` instead. Grep both.

## Environment variables

| Variable | Required | Meaning |
|---|---|---|
| `WEKIT_TOKEN` | **Yes** | Bearer token configured in WeKit's API + MCP server settings. Also gates `hermes gateway status` detection of this platform |
| `WEKIT_BASE_URL` | **Yes** | Where WeKit's API is reachable **from the agent host**, e.g. `http://192.168.1.50:3001`. There is deliberately no default: if it is unset the platform refuses to connect and tells you so, rather than guessing an address and leaving you with a confusing retry loop |
| `WEKIT_ALLOWED_USERS` | Recommended | Comma-separated wxids allowed to talk to the agent. **Inbound filter only — outbound is unrestricted** |
| `WEKIT_ALLOWED_LABEL` | No | Name of a WeChat contact label whose members may talk to the agent; merged into `WEKIT_ALLOWED_USERS` at connect time. See [Labels as the allow-list](#labels-as-the-allow-list) |
| `WEKIT_ALLOW_ALL_USERS` | No | `1` / `true` / `yes` (case-insensitive) disables the whitelist entirely. Unsafe: anyone who can message the account can drive your agent |
| `WEKIT_ENABLE_WRITE_ACTIONS` | No | `1` / `true` / `yes` allows the action tools that change the account or are seen by others (friend requests, group membership, contact labels, Moments). Off by default |
| `WEKIT_PLAIN_TEXT` | No | On by default: outbound text is rewritten from markdown into plain prose before sending (see [Outbound text](#outbound-text)). `false` / `0` / `no` / `off` sends it raw |
| `WEKIT_POLL_TIMEOUT_MS` | No | Long-poll duration in ms. Default `30000`, values below `5000` are clamped up |
| `WEKIT_HOME_CHANNEL` | No | convId that scheduled/cron deliveries are sent to |
| `WEKIT_MEDIA_ADB_PATH` | No | Path to `adb`. Setting it turns on retrieval of received files and images off the phone (see below). Unset = disabled |
| `WEKIT_MEDIA_DIR` | No | Where retrieved media is written on the agent host. Default `/tmp/wekit-media` |
| `WEKIT_CAPTURE_ARTICLES` | No | `true` lets a link message open the article on the phone and read its text from the WebView disk cache (screenshots as a fallback). Takes over the screen briefly — see [Official-account articles](#official-account-articles) |
| `WEKIT_ADB_SERIAL` / `WEKIT_ADB_PATH` / `WEKIT_LOG_PATH` | No | Device serial for media retrieval; the last two are used by the optional keepalive watchdog |
| `WEKIT_ROUTER_WAN` / `WEKIT_PHONE_HOSTNAME` | No | Used by the router DNAT script only (its WAN-side address and the phone's DHCP hostname) |

**Populate `WEKIT_ALLOWED_USERS` from wxids you have actually seen in the log**, i.e. the id in `wechat-wekit: inbound from <id> …` — **not** from the contact list. In the reference deployment the whitelist was filled from the friend list and silently dropped every message the user sent, because they were writing from a different account than the one in that list. The drop is logged at `debug` level, so with default log settings the channel simply looks dead.

Two caveats on that log line: it prints the **conversation id**, which equals the peer's wxid in a DM but is the `@chatroom` id in a group; and the whitelist is matched against the conversation id *or* the sender id.

## Transport

### USB (`adb forward`) — not recommended

On the reference host, the Windows adb server crashed and respawned **on its own every 10–30 seconds**. The device never dropped (`adb devices` reported `device` throughout, with only one `adb.exe` on the machine and no commands being issued at the time). Because `adb forward` rules live in adb-server memory, every crash vaporised the forward and killed the in-flight long poll.

Measured `poll error` counts per hour on that host: `163, 175, 169, 168, 169` — **roughly one break every 21 seconds, all night**. Every message that arrived during a break was lost, which presents to the user as "it answers sometimes". After switching the same host to WiFi via router DNAT: 55-second long polls completing intact, **0 poll errors**.

If your adb happens to be stable, USB can work. Just don't assume it is — and never build a long-lived service on top of an `adb forward` you haven't watched for an hour.

### WiFi via router DNAT — recommended

Use this when the agent host and the phone are on different subnets — for example an agent host on the upstream network and the phone on a second router's LAN. `transport/router-dnat/wekit-dnat.sh` runs **on the router** (OpenWrt-family, BusyBox `sh`, `iptables`) and forwards `<router-WAN-address>:3001` to the phone's LAN IP:

```
Agent host (192.168.1.0/24) → 192.168.1.50:3001   (router WAN side)
                            → DNAT → 192.168.20.60:3001 (phone WiFi, WeKit API)
```

Then set `WEKIT_BASE_URL=http://192.168.1.50:3001`.

What the script does, and what you need to know about it:

- Resolves the phone's current IP from `/var/dhcp.leases` **by DHCP hostname** (`NAME` / `WEKIT_PHONE_HOSTNAME`). Lease changes self-heal on the next run.
- **If the hostname doesn't match a lease, it exits silently and installs nothing.** Check your lease file first if nothing happens.
- Tags every rule it creates with an iptables comment and only ever deletes rules carrying that tag — it will not touch other tools' rules (e.g. a transparent proxy).
- Is idempotent: if the DNAT already points at the current IP it exits without rebuilding, so it never cuts an active long poll.
- Installs three rules: `nat/PREROUTING` DNAT, `FORWARD` ACCEPT, `nat/POSTROUTING` MASQUERADE — each inserted at position 1.
- Logs via `logger -t wekit-dnat`.

Edit the config block at the top (`PORT`, `WAN`, `NAME`) or supply `WEKIT_ROUTER_WAN` / `WEKIT_PHONE_HOSTNAME`. iptables rules are not persistent, so run it from `/etc/rc.local` at boot (after a short sleep, so DHCP has leases) **and** from cron every couple of minutes. Before deploying, check that port `3001` isn't already intercepted by an existing transparent-proxy ruleset on that router.

Routers that are nftables-only, or that don't keep dnsmasq leases at `/var/dhcp.leases`, need the script adapted.

### Anything else

Any route that makes the phone's `:3001` reachable from the agent host works — a WireGuard tunnel, an SSH tunnel, a `netsh portproxy`. The plugin only cares about `WEKIT_BASE_URL`.

## Keeping the phone side alive

`ops/wechat_watchdog.py` is an **example**, not a required component. It runs on a machine that has adb access to the phone and:

1. Probes `GET /api/self/info` over the same path Hermes uses, on an interval — **pure HTTP, no adb**.
2. Only after several consecutive failures does it touch adb, to check whether WeChat is running.
3. If WeChat is not running, it launches it with `monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1`.

Two hard rules baked into it:

- **Never `force-stop` WeChat as a recovery action.** In the reference deployment that broke the Xposed injection state and required a full phone reboot to recover. The watchdog only ever launches, never stops.
- **Don't poll over adb.** Even a periodic `adb shell pidof` perturbs the adb server and can knock over forwards; an HTTP probe tells you what you actually care about anyway.

Useful facts behind this design: WeKit's API+MCP server toggle **persists across reboots**, and the server comes up automatically once WeChat starts and the module loads — but there is no "start on boot" option, and WeChat itself does not necessarily restart after a phone reboot. Remember the multi-minute DexKit scan on that first start before `:3001` answers.

## What works, what doesn't

| Capability | Status | Notes |
|---|---|---|
| Receive DM text | ✅ | MCP `wait-for-new-message`, WCDB-layer hook |
| Receive group text | ✅ | Group conv ids end in `@chatroom` or `@im.chatroom`; the sending member is reported separately from the conversation |
| Send text | ✅ | `POST /api/messages/text` — `{type, convId, content}` |
| Send image | ✅ | `POST /api/messages/image`, multipart: form field `convId` + file part named exactly `file`. Bytes are uploaded from the agent host; accepts a local path, `http(s)://` URL, `file://`, or `data:` URI. A caption, if given, is sent afterwards as a **separate text message** |
| Contact display names | ✅ | `GET /api/contacts/{wxid}`, cached in-process (prefers remark name, then nickname); falls back to the raw id if the lookup fails |
| Scheduled / cron delivery | ✅ | Target set by `WEKIT_HOME_CHANNEL` |
| Inbound whitelist | ✅ | Matched against the conversation id **or** the sender id |
| No echo loop | ✅ | WeKit does not report the agent's own outgoing messages |
| Receive non-text (image/voice/file/link/sticker/location/quote) | ✅ | Every payload is decoded into a short, actionable line — filename and size for files, duration for voice, the actual URL for links, the quoted text for replies. The raw XML never reaches the model |
| Receive a file (any extension) | ✅ | Fetched off the phone as a real local file in `media_urls`. WeKit's download is format-agnostic — it pulls the raw bytes, so xlsx / docx / pdf / zip / anything works. Needs the companion WeKit script — see [Receiving the actual files](#receiving-the-actual-files) |
| Receive an image | ✅ | Same path; de-obfuscated from WeChat's stored form to a real JPEG/PNG |
| Receive a voice note | ✅ | WeKit decodes it to mp3; attached as a real file |
| Receive a sticker | ⚠️ | Standard stickers are converted to GIF and attached; a custom-emoji sticker may fail to decode (a text `[Sticker]` label is always given) |
| Receive a video | ⚠️ | Metadata + a text label always; the file itself only if WeChat has already downloaded it (WeKit exposes no video download endpoint) |
| Receive an official-account article | ✅ | With `WEKIT_CAPTURE_ARTICLES=true`, a link is opened on the phone and its full text is read from the WebView disk cache (structured text, not just a summary); screenshots as a fallback. See [Official-account articles](#official-account-articles) |
| Read chat history on demand | ✅ | `wechat_pull_history` tool — paged, oldest-first, group senders resolved to names. Not used for inbound backfill (see [Known limitations](#1-inbound-is-edge-triggered--messages-can-be-lost-permanently)) |
| Re-download media from the CDN | ✅ | The companion script asks WeChat to cache an image/file from the CDN before fetching it, so media the phone never auto-downloaded still reaches the agent |
| Send a voice message | ✅ | `wechat_send_voice` tool — text is synthesized with edge-tts, converted to SILK on the phone, and sent as a real voice bubble |
| Send a video | ✅ | `wechat_send_video` tool — multipart to `POST /api/messages/video` |
| Group member management | ✅ | `wechat_group_members` tool — list / add / remove / invite. Everything but `list` needs `WEKIT_ENABLE_WRITE_ACTIONS` |
| Accept friend requests | ✅ | `wechat_accept_friend` tool, needs userId + ticket + scene. Gated behind `WEKIT_ENABLE_WRITE_ACTIONS` |
| Post to Moments (朋友圈) | ✅ | `wechat_post_moment` tool — text or text + images. Gated behind `WEKIT_ENABLE_WRITE_ACTIONS` |
| Contact labels (标签) | ✅ | `wechat_labels` tool — list labels, read a label's members, assign a contact's labels. A label can also drive the inbound allow-list via `WEKIT_ALLOWED_LABEL` |
| Quote / reply threading | ⚠️ | Inbound quotes are decoded (the quoted text is surfaced); **outbound** `reply_to` is ignored — replies are ordinary messages, not WeChat quotes |
| Typing indicator | ❌ | `send_typing` is a no-op |
| Set / remove group admin | ❌ | WeKit's REST API exposes no endpoint for it (add/remove/invite exist, promotion does not) |
| Send file / location / sticker as the reply | ❌ | WeKit has endpoints; the reply path is wired for text and image only. Voice and video are reachable through the action tools above |

The registered `max_message_length` is 2000.

## Outbound text

**WeChat has no markdown renderer.** `**important**` from the model reaches the user as those sixteen literal characters, and `### Title` arrives with its hashes. The registered platform hint does say so — write the way a person types in a chat app — but asking is not a guarantee: models are raised on markdown and will leak a `#` eventually. So `send()` carries a deterministic converter behind it (`WEKIT_PLAIN_TEXT`, on by default):

| What the model writes | What the user sees |
|---|---|
| `**bold**` / `*italic*` / `__bold__` | bold / italic / bold |
| `### Deployment result` | Deployment result, hashes gone, a blank line after it |
| `` `docker restart` `` / ```` ```fenced block``` ```` | the code itself, no backticks and no fences |
| `- back up first` | `· back up first` — the bullet a Chinese reader expects; `1.` numbering is kept |
| `[subscription](https://s.starq.me/…)` | subscription https://s.starq.me/… — and just the URL when the label only repeats it |
| a pipe-drawn table | two columns flatten to `key: value`, wider ones to a short block per row (a monospace grid always shatters on a phone) |
| `> quote` / `---` / three or more blank lines | the quote's text, the rule dropped, the blank run collapsed to one |

The harder half is **not damaging text that merely looks like markdown**. None of these are touched: the underscores in `wxid_xxxxxxxx`, a path like `/root/.hermes/plugins/wechat_wekit/my_file.py`, `__init__.py`, `2*3` and `2**3`, a line starting `#1`, `C#`, a lone `*` in prose, `rm *.log`, a URL with parentheses (`…/wiki/Foo_(bar)#history`), and prose that merely happens to contain pipes with no delimiter row. Every rule bails out when unsure: a stray asterisk on screen is only ugly, while a damaged wxid is wrong in a way the reader cannot detect.

The converter never raises. A bug in it may at worst ship the markdown; it can never cost the message.

## Action tools

Beyond the message stream, the plugin registers seven tools the agent can call
during a conversation. They land in the `hermes-wechat-wekit` toolset — a name
chosen so that Hermes, which derives a plugin platform's default toolset as
`hermes-{platform key}`, enables them for WeChat sessions with no config to edit.

| Tool | What it does | Write gate |
|---|---|---|
| `wechat_pull_history` | Read recent messages from a conversation, oldest first | — |
| `wechat_send_voice` | Send a native voice bubble (from text via edge-tts, or a ready mp3) | — |
| `wechat_send_video` | Send a local video file | — |
| `wechat_group_members` | `list` members, or `add` / `remove` / `invite` them | writes only |
| `wechat_accept_friend` | Accept a pending friend request | yes |
| `wechat_post_moment` | Post text or text + images to Moments | yes |
| `wechat_labels` | `list` labels, read a label's `members`, or `set` a contact's labels | `set` only |

Sending a message — text, voice, video — is no riskier than the reply the agent
already sends, so those tools are always live. Anything that changes the
account's social graph or is visible to other people stays inert until
`WEKIT_ENABLE_WRITE_ACTIONS=1`: the tools are still registered and still
described to the model, but they refuse and say what to set. That way the
account cannot be quietly reshaped by a prompt injected into a message.

`wechat_send_voice` needs `edge-tts` installed on the agent host when called
with `text`; with `audio_path` it has no extra dependency. The mp3 → SILK
conversion runs on the phone, in WeKit.

**These tools are not confined to WeChat sessions.** Hermes treats a plugin
toolset it has not been told about as on-by-default for *every* platform, so a
CLI or Telegram session gets all seven as well — verified, not assumed. Usually
that is welcome (asking from the CLI to pull a WeChat conversation is a
reasonable thing to want), but it does mean the boundary is the write gate and
your own prompt, not the channel you happen to be in: `wechat_send_voice` and
`wechat_send_video` take a free-form `conv_id` and are **not** gated, so on any
platform the model can send audio or video to any contact. `WEKIT_ALLOWED_USERS`
does not constrain this — it filters inbound only. Run `hermes tools` and turn
the toolset off for the platforms that should not have it if that matters to you.

### Labels as the allow-list

`WEKIT_ALLOWED_USERS` is a list of wxids in a file. `WEKIT_ALLOWED_LABEL` is the
same thing managed from the phone: put every contact the agent should answer
under one WeChat label, name that label here, and its members are resolved at
connect time and merged into the allow-list. Adding someone is then a couple of
taps in WeChat rather than an `.env` edit and a restart.

Two properties worth knowing: the merge is additive, so anything in
`WEKIT_ALLOWED_USERS` still applies; and a label that fails to resolve is
logged and ignored, never treated as "allow everyone" — a lookup failure must
not silently open the account up. Membership is re-read on connect and
then roughly every ten minutes, so granting or revoking on the phone takes
effect on its own — no restart, and no HTTP call on the path an inbound
message travels.

**Create the label in WeChat first** (Me → Contacts → Tags). WeKit can read
labels and assign existing ones, but has no endpoint for creating one — WeChat
does that over a separate CGI that is not on the REST surface. Assigning a name
that does not exist used to look like it worked (WeChat skips the unknown name
and still answers 200); `wechat_labels` now checks the name against the label
list and fails with the labels that do exist, rather than reporting a write that
did nothing.

## Receiving files and images

Every incoming message type is decoded into a short line the agent can act on,
rather than the XML blob WeChat actually sends:

| Sent | The agent sees |
|---|---|
| Image | `[Image] — 871.2 KB` |
| Voice | `[Voice message] — 2.8s` |
| File | `[File] report.xlsx — XLSX, 11.0 KB` |
| Article / link | `[Link] <title>` + description + **the real URL**, so the agent can go read it |
| Reply | `[Reply to Alice] <the reply>`, with the quoted text in `reply_to_text` |
| Sticker / location / mini program / transfer / … | a matching one-line summary |

Getting the **actual bytes** is harder, and the reason is upstream: every WeKit
download endpoint takes a `msgSvrId`, and no WeKit API ever returns one —
`wait-for-new-message` reports only ConvId, Sender, Type and Content. So the
media cannot be pulled through WeKit itself.

Set `WEKIT_MEDIA_ADB_PATH` and the plugin will instead copy the file WeChat
already wrote to its own storage, over adb:

```bash
WEKIT_MEDIA_ADB_PATH=/path/to/adb
WEKIT_ADB_SERIAL=YOURSERIAL          # optional when only one device is attached
WEKIT_MEDIA_DIR=/var/lib/hermes/wechat-media
```

Retrieved media is written there and passed to the agent in `media_urls`, so
vision tools can open an image and file tools can read a document. Know the
edges before relying on it:

- **The media has to be on the phone already.** WeChat downloads a file only
  when someone taps it, unless auto-download is enabled (WeChat → Me → Settings
  → General → Photos, Videos, Files and Calls). Without that, a file message is
  metadata only and there is nothing to copy.
- **Files are matched by exact filename**, which is unambiguous. **Images have
  no usable name in the payload**, so the newest image written near the time the
  message arrived is taken — a heuristic, bounded to a short window.
- Images WeChat stored in its own `wxgf` container are **discarded rather than
  attached**, because nothing downstream can open them. Ordinary JPEG/PNG/GIF —
  including the XOR-obfuscated variants — come through fine.
- adb is flaky enough that calls are retried; a failure is never fatal, the
  agent just keeps the text description.

If you want this to be reliable rather than best-effort, the fix belongs
upstream: `wait-for-new-message` already holds `msgSvrId` in the row it reads
and would only need to include it in the response, after which the documented
`download-file` / `download-image` endpoints would do the job properly.

## Receiving the actual files

Out of the box this plugin can *describe* an incoming file but not open it, and
the reason is worth stating plainly: **WeKit's download endpoints are all keyed
by `msgSvrId`, and no WeKit API ever returns one.** `wait-for-new-message` gives
ConvId/Sender/Type/Content; `get-chat-history` gives sender/content. The id
needed to fetch an attachment is simply never exposed.

`phone-script/hermes-media-bridge.js` closes that gap from inside WeChat,
without patching WeKit. WeKit ships a JavaScript engine that can hook arbitrary
methods, so the script hooks the same WCDB insert WeKit itself hooks, reads
`msgSvrId` straight off the `ContentValues`, and calls WeKit's own local API to
download the attachment into `/sdcard/Download/WeKit/`. The plugin then pulls it
to the agent host over adb and passes the local path in `media_urls`.

### Install

```bash
adb push phone-script/hermes-media-bridge.js \
  /sdcard/Android/data/com.tencent.mm/WeKit/scripts_js/
```

Edit `TOKEN` at the top of the script to match your WeKit API token, then enable
**脚本引擎 (JS)** in WeKit (Features → search `javascript`). Enabling it loads
the script immediately — no WeChat restart. Finally, point the plugin at adb:

```bash
WEKIT_MEDIA_ADB_PATH=/path/to/adb
WEKIT_ADB_SERIAL=<serial>        # optional with a single device
WEKIT_MEDIA_DIR=/var/lib/hermes/wechat-media
```

### Things that will bite you

- **Editing the script does not reload it.** Toggle the JS feature off and on;
  the log line `loaded script, name=...` confirms the new copy is live.
- **`msgSvrId` must be read as a string.** It exceeds 2^53, so reading it as a
  JavaScript number silently rounds it and the download then asks for an id that
  does not exist. The script uses `getAsString` / `Cursor.getString`.
- **Never do network I/O in the hook.** It runs on WeChat's database thread;
  downloading a large attachment there would freeze the app. The script queues
  the id and lets a worker thread fetch it.
- **Messages you send yourself have no `msgSvrId` at insert time** (the server
  assigns it on send), so self-sent messages cannot be used to test the path.
- The script also backfills: on startup it queues recently received media that
  arrived before it was installed.

### Official-account articles

An article link cannot be fetched by the agent, and that is not a proxy or
network problem: `mp.weixin.qq.com` serves the page only to a real WeChat
client and redirects everything else to a captcha ("环境异常"). But the article
renders in WeChat's own system Chromium WebView, and once it has rendered its
HTML sits in that WebView's on-disk HTTP cache.

With `WEKIT_CAPTURE_ARTICLES=true`, a link message causes the phone to open the
article; the plugin then reads the rendered document straight out of the
WebView's **Chromium disk cache** (Simple Cache format), decompresses it, and
extracts clean structured text — title and body — in one shot. Nothing on the
device is modified: the cache files are only read, no screen scrolling, no
injection into WeChat, no accessibility settings touched.

If the document is not in the cache (a rare `no-store` response), it falls back
to opening the article, scrolling, and attaching screenshots for the agent to
read with vision.

Both paths take over the phone's display for a few seconds, which is why the
feature is off by default. Set it only on a phone dedicated to the agent.

## Known limitations

### 1. Inbound is edge-triggered — messages can be lost permanently

This is the single most important thing to understand about this project.

WeKit's `wait-for-new-message` MCP tool registers a WCDB listener **only for the duration of the call**, and removes it in a `finally` block. There is no queue, no buffer, and no cursor. **Any WeChat message that arrives while the poll loop is not currently inside a wait call is gone — it cannot be recovered, by this plugin or by anything else.** This is upstream behaviour, not a bug introduced here.

The plugin minimises the window but cannot close it:

- Each inbound message is dispatched as a **background asyncio task**, so the poll loop re-arms the listener immediately instead of waiting for the LLM. Blocking on the reply would leave the channel deaf for tens of seconds every single turn — exactly when a user is most likely to send a follow-up.
- Long polls are re-issued back to back with no idle gap.
- Failures inside a background dispatch are logged at `error` level, so a dropped message leaves a trace instead of vanishing.
- On shutdown, in-flight dispatches get up to 10 seconds to finish sending before they are cancelled.

Windows where messages are still lost:

- Gateway startup, shutdown, restart, or reload
- The exponential backoff after a poll error (1 s, doubling to a 30 s cap) — this is why transport stability matters so much
- MCP session expiry and re-initialisation
- Any result that fails the inbound regex (logged at `debug`, then dropped)

Practically: one-question-one-answer conversation is reliable; rapid-fire bursts may drop messages, and **neither you nor the agent gets a "message missing" signal**. A history-diff backfill is not implemented — WeKit's history endpoint returns `sender: content` pairs with no timestamps and no message ids, so a correct diff is genuinely hard, especially against a user who legitimately sends the same text twice.

### 2. Everything else

- **Inbound parsing is regex-based** over WeKit's formatted `ConvId='…',Sender='…',Type=N,Content='…'` string. Unusual content can fail to parse and be dropped.
- **Message ids are local timestamps**, not WeChat message ids. They are not stable identifiers and can collide.
- **There is no content-based deduplication**, by design: `wait-for-new-message` fires once per DB insert, and deduping on text would swallow a user legitimately repeating themselves.
- **The whitelist matches conversation id *or* sender id.** In a group, whitelisting one member lets that member's messages through the group. Whitelist deliberately, and remember outbound is never filtered.
- **Single account, single device.** No multi-account or multi-device support.
- **WeChat's one-session rule** means the phone running the agent's account logs your own phone out of that account.
- **Upstream risk.** WeKit has no stable releases and one maintainer.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `cannot reach WeKit API at … after retries` on startup | Wrong `WEKIT_BASE_URL`, wrong token, DNAT not installed, or WeChat/WeKit server not running. Reproduce with the `curl /api/self/info` check from the agent host |
| `:3001` refuses connections right after enabling the module | First-start DexKit scan; wait several minutes |
| Module enabled in the manager but no API at all | WeChat hot-update (tinker) not disabled → module silently not loaded. Check `logcat` for the module load lines |
| Recurring `poll error` lines | Transport instability. If you are on USB `adb forward`, that is almost certainly it — switch to WiFi |
| Channel is silent but `poll alive` keeps ticking | Inbound is arriving and being dropped by the whitelist. Raise log level to `debug` and look for `drop msg from unlisted`; fix `WEKIT_ALLOWED_USERS` using ids from `inbound from …` |
| No `wechat-wekit` lines in `gateway.log` | Plugin loggers are `hermes_plugins.*`; look in `agent.log` |
| Channel dead after a phone reboot | WeChat did not restart. Launch it (`monkey`), never `force-stop` it |
| Everything looks connected but replies are error text | Not a channel problem — check the agent's own model/provider configuration |

## Security notes

- **The transport is plaintext HTTP with a bearer token.** Anyone on the same LAN segment who can reach port 3001 can read and send messages as that WeChat account. Treat the token as a full account credential.
- **WeKit's API server binds `0.0.0.0` and this is not configurable upstream.** Any device on the phone's network can reach it. Keep the phone on a trusted network; consider an isolated VLAN or guest-network segmentation.
- **Never port-forward 3001 to the internet.** The DNAT script deliberately maps a router's *internal* WAN-side address on a private LAN — it is not an internet exposure, and you should not adapt it into one. To cross an untrusted network, tunnel it (WireGuard, SSH) instead of opening the port.
- **Always set `WEKIT_ALLOWED_USERS`.** `WEKIT_ALLOW_ALL_USERS=true` is for bring-up only; leaving it on means any stranger who messages the account is driving your agent, its tools, and its token budget.
- **Inbound message content is untrusted input, never instructions.** The platform hint registered by this plugin says so explicitly, but your agent's own prompt and tool permissions are the real boundary.
- **Leave `WEKIT_ENABLE_WRITE_ACTIONS` off unless you need it.** It is what stands between a message saying "add me to your group" and the agent actually doing it. With the gate off, a prompt injected into an incoming message can still make the agent *try* — the tool refuses. With it on, the tool obeys.
- **Outbound is not filtered.** The agent can message anyone in the account's contacts.
- **Moments posts are public to the account's contacts** and are not deleted by anything here. Nothing rate-limits the action tools either: a looping agent can post repeatedly, or churn group membership, faster than a human would.
- The image sender will fetch any `http(s)://` URL it is handed and read any local path — keep that in mind when an agent chooses the argument.
- Don't commit your `.env`. The token, your wxids, and your LAN topology are all in it.

## Legal / Terms of Service

**Automating a personal WeChat account violates WeChat's Terms of Service.** Accounts have been banned for less. A ban is not limited to messaging: it also **freezes WeChat Pay**, including balance and linked-card functionality on that account.

- Use a **dedicated secondary account** holding no money, no important contacts, and no history you would miss. This is not a suggestion — it is the only responsible way to run this.
- Published for **research and personal use**. Do not use it for bulk messaging, unsolicited outreach, scraping other people's data, or commercial automation.
- Everyone in a conversation with the agent is a real person who has not consented to being processed by an LLM. Behave accordingly.
- Running this is your decision and your risk. The authors provide no warranty and accept no liability for banned accounts, frozen funds, lost messages, or anything else.

## License and credits

MIT licensed — see `LICENSE`.

This project depends on, but does not redistribute, **[WeKit](https://github.com/Ujhhgtg/WeKit)** by Ujhhgtg — the Xposed module that does the actual WeChat hooking and exposes the REST + MCP API this plugin talks to. All the hard parts of touching WeChat live there. See `NOTICE` for upstream attribution, and check WeKit's own license before redistributing any part of it.

Built as a platform plugin for [Hermes Agent](https://github.com/NousResearch) by Nous Research.
