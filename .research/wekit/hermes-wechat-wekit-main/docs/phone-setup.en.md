# Phone Setup

中文: [phone-setup.md](phone-setup.md)

This is the hard part. Everything else in this project — the Hermes plugin, the transport, the watchdog — is ordinary software. This page is where you turn a stock Android phone into a WeChat host that a program can talk to: unlock the bootloader, root it, install an Xposed framework, load the WeKit module into WeChat, and turn on WeKit's HTTP + MCP server.

Budget an evening. Several steps wipe the device or require a reboot, and one step (the first DexKit scan) is just minutes of waiting.

---

## Read this before you buy hardware or install anything

**Use a dedicated secondary WeChat account. This is not optional.**

- Automating a WeChat account violates WeChat's Terms of Service.
- A WeChat ban also freezes WeChat Pay and any balance attached to the account. Do not put your primary identity, your payment method, or money you care about behind this.
- **WeChat allows one active mobile session per account.** If you log the agent's phone into *your* account, your own phone gets kicked off. A separate account is a structural requirement, not just a safety measure.
- WeChat does actively check for Xposed. Publicly documented enforcement against Xposed modules specifically is mostly historical (the last well-evidenced mass ban wave hit a *different* technique — local-database decryption — in March 2026), but "no recent wave" is not a guarantee. Assume the account can disappear.
- This project is for research and personal use. Never use it for bulk, unsolicited, or commercial messaging.

**Understand what you are giving up on the phone:** unlocking the bootloader wipes the device, trips hardware attestation, and will make some banking / DRM apps refuse to run. Use a phone you are willing to dedicate to this.

> ⚠️ **Know the channel's central limitation before you invest an evening in it.** Inbound delivery is **edge-triggered**: WeKit's `wait-for-new-message` registers a database listener only for the duration of each call and removes it when the call returns. There is no queue, no buffer, no cursor. Any message that arrives while the poll loop is *between* calls is **lost permanently and cannot be recovered** — this is upstream behaviour, not something this plugin can fix. Ordinary back-and-forth conversation is reliable; rapid-fire bursts and any gap (gateway restart, error backoff) can drop messages. Read the architecture notes before you depend on this for anything.

---

## What you are building on the phone side

```
┌─ Android phone (rooted) ───────────────────────────────┐
│                                                        │
│  Magisk (root) → Zygisk                                │
│      └── Vector / LSPosed (Xposed framework)           │
│              └── WeKit module, scoped to WeChat        │
│                                                        │
│                   ├─ hooks the WCDB insert layer:      │
│                   │  sees messages as the database     │
│                   │  receives them — no notifications, │
│                   │  no UI automation                  │
│                   │                                    │
│                   └─ HTTP REST + MCP server on :3001,  │
│                      static bearer-token auth          │
└────────────────────────────────────────────────────────┘
                          ▲
              agent host reaches :3001 over the LAN
         (see the transport docs — WiFi strongly preferred)
```

---

## 0. Prerequisites

| Item | Requirement | Notes |
|---|---|---|
| Phone | Android 9+, **bootloader must be unlockable** | See §1 — the step that kills most attempts |
| Reference device | Pixel 9 Pro (`caiman`), Android 16 | The flow below is what was verified there |
| Computer | Windows / macOS / Linux with `adb` + `fastboot` | Android SDK platform-tools |
| Root | Magisk (30.x verified) with **Zygisk enabled** | §3 |
| Xposed framework | Vector on modern Android; LSPosed only on older releases | §4 |
| WeChat | **Official** APK, version **8.0.72** | §5 |
| WeChat account | A dedicated secondary account | Not your main one |
| Module | [github.com/Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit) | §6 |

WeKit is a third-party project with, in practice, a single maintainer and no tagged releases. This plugin only calls its HTTP API; it does not vendor or redistribute any WeKit code. Everything from §5 onward depends on that upstream project continuing to exist and continuing to support your WeChat version.

---

## 1. Device prerequisites: can this phone even be unlocked?

**Check this before anything else.** On a carrier-locked unit the "OEM unlocking" toggle is greyed out and **there is nothing you can do about it in software** — the restriction lives at the bootloader level and reflashing stock firmware does not clear it.

On the device:

1. Settings → About phone → tap *Build number* seven times to enable Developer options.
2. Settings → System → Developer options → look at **OEM unlocking**.

| What you see | Meaning |
|---|---|
| Toggle is available (on or off) | Good — proceed |
| Greyed out, subtitle *"Connect to the internet or contact your carrier"* | **Carrier-locked.** That exact subtitle is the tell. Dead end for this device |
| Greyed out, some other subtitle | Could be no network, a device-owner/MDM profile, or a locked build — investigate, but carrier lock is the usual answer |

The authoritative machine-readable check is from the bootloader:

```bash
adb reboot bootloader
fastboot flashing get_unlock_ability     # 1 = this device can be unlocked
```

(On builds that expose it, `adb shell dumpsys oem_lock` reports the underlying `isOemUnlockAllowedByCarrier` flag; `false` there is the carrier lock.)

Two things that are commonly and wrongly believed:

- **SIM unlock ≠ OEM unlock.** A device whose SIM lock has been released — it makes calls and uses data on any carrier — can still be OEM-locked forever. Advice from 2023 or earlier saying "get it SIM-unlocked and OEM unlocking lights up" no longer holds for the major US carriers. This was re-confirmed the hard way on a carrier unit: working calls and data, OEM unlocking still greyed out.
- **Reflashing the factory image does not remove a carrier lock.**

**Recommendation: buy a Google Store (unlocked) Pixel.** Carrier-channel units of the same model may or may not be unlockable, and you find out only after the phone is in your hand.

---

## 2. Unlock the bootloader

> ⚠️ **This wipes the device.** Do it before you log any account into the phone.

1. Developer options → enable **OEM unlocking** *and* **USB debugging**.
2. Reboot to the bootloader and unlock:

```bash
adb reboot bootloader
fastboot flashing unlock          # confirm on the device with the volume/power keys
```

3. The device wipes and reboots. Walk through setup again, re-enable Developer options and USB debugging.

Verify the *real* state at any later point with:

```bash
fastboot getvar unlocked          # ground truth
```

Note for later: once you install Magisk modules such as Play Integrity spoofers, `getprop ro.boot.flash.locked`, the vbmeta state, and `verifiedbootstate` may all report *locked / green* because a module rewrote them. Those properties stop being evidence of anything. `fastboot getvar unlocked` is the only reliable check.

---

## 3. Root: patch the right partition

**On Pixel 9-family devices (and generally anything that shipped with Android 13+), you patch `init_boot.img`, NOT `boot.img`.** Patching `boot.img` on these devices leaves you with a phone that either bootloops or boots without root, and you will waste an hour before you notice.

Quick test: if the factory image contains an `init_boot.img`, that is the partition Magisk patches.

1. **Install the Magisk APK on the phone.**

2. **Download the official factory image** for your exact device and build (Settings → About phone → Build number). Verify its SHA-256 against the checksum published next to the download. Do not skip this.

3. **Extract the nested image zip** and pull out `init_boot.img`. On Pixel 9-family this file is roughly 8 MB — if what you extracted is tens of MB, you grabbed `boot.img`:

```bash
unzip <device>-<build>-factory-*.zip
unzip image-<device>-<build>.zip init_boot.img
```

4. **Back up the stock `init_boot.img`.** You need it to un-root, to take an OTA cleanly, or to recover. Keep one per build you run.

5. **Patch it with the Magisk app:**

```bash
adb push init_boot.img /sdcard/Download/
```

Open Magisk → **Install** → *Select and Patch a File* → pick `/sdcard/Download/init_boot.img`. Magisk writes `magisk_patched-XXXXX_yyyyy.img` next to it.

6. **Pull it back and flash it:**

```bash
adb pull /sdcard/Download/magisk_patched-XXXXX_yyyyy.img
adb reboot bootloader
fastboot flash init_boot magisk_patched-XXXXX_yyyyy.img
fastboot reboot
```

7. Open the Magisk app — it should report an installed Magisk version, not "N/A".

8. **Enable Zygisk** (Magisk → Settings → Zygisk) and reboot. The Xposed framework in §4 requires it.

**Two gotchas worth knowing now:**

- **An OTA update overwrites `init_boot` and removes root.** After any system update you must re-patch and re-flash, using the *new* build's `init_boot.img`. The channel stays down until you do.
- **`adb shell su` may be rejected instantly** with `su: request rejected (2000)`. On recent Android, background-activity-start restrictions can stop Magisk from ever showing the authorization dialog; the request times out, is recorded as *deny* for the Shell UID, and that stored policy wins from then on — including after you set the default response to "Grant". The fix is **Magisk app → Superuser tab → enable the `[SharedUID] Shell` entry**, not changing the automatic-response setting. You do not strictly need `adb su` for this project (the Magisk and Vector apps can do everything from their own UIs), but you will want it while debugging.

---

## 4. Install an Xposed framework

WeKit is an Xposed module, so it needs a framework to load it, and the framework needs Zygisk.

| Framework | Status |
|---|---|
| **Vector** (`JingMatrix/Vector`) | The maintained LSPosed successor and what the reference deployment runs (v2.2 on Android 16) |
| **LSPosed** (`LSPosed/LSPosed`) | Official releases stopped at v1.9.2 (2023) and do not cover Android 16. Fine only on older Android |
| **NPatch** (rootless APK patching) | **Does not work for WeKit.** The patched WeChat installs and logs in, but the module never loads: `libdexkit.so` is not loaded, so WeKit's hooks never attach and :3001 never comes up. Verified first-hand. If you have root, do not go down this road |

Install the framework as a Magisk module. The easiest route — and the one that sidesteps the `su`-rejection trap above — is the **Magisk app → Modules → Install from storage**, pointed at the framework's release zip. From a root shell the equivalent is:

```
magisk --install-module /sdcard/Download/Vector-<version>.zip
```

**Reboot.**

Then install the framework's **manager app**. Vector ships its APK inside its module directory (`/data/adb/modules/zygisk_vector/manager.apk`). SELinux blocks reading files under `/data/adb` from the `su` domain, so copy it out with SELinux briefly permissive and put SELinux straight back. Run these **on the device**, in a root shell:

```bash
adb shell
su
setenforce 0
cp /data/adb/modules/zygisk_vector/manager.apk /data/local/tmp/
setenforce 1
pm install /data/local/tmp/manager.apk
exit
```

Open the manager (Vector's package is `org.matrix.vector.manager`). It should report the framework as **active**; you should also see `vectord` and `zygiskd64` running. If it says the framework is not installed, Zygisk is probably off — check Magisk → Settings and reboot.

While you are working on the device, stop the screen from sleeping mid-flow:

```bash
adb shell svc power stayon true
```

---

## 5. Install a supported WeChat, then stop it updating

WeKit hooks specific WeChat internals and is version-sensitive.

- **Target WeChat 8.0.72.** That is the highest build in WeKit's version table. The project's docs mention newer versions, but the download links for those resolve to the same version code, so treat anything above 8.0.72 as unverified.
- Install the **official** WeChat APK — not a patched or modified one. (With root + Xposed you inject into stock WeChat; you do not need a repackaged APK.)
- **Log in with the dedicated secondary account now**, before the module is active, so the first login is clean.
- **Turn off automatic updates for WeChat** wherever you installed it from. An update to an unsupported version silently breaks the hooks.
- **Disable WeChat's hot-update (tinker) mechanism** with WeKit's own setting as soon as the module is running (§7). This matters: a hot-patched WeChat can cause the module to *silently fail to load*, and the only documented cleanup is deleting the patch directory by hand with an on-device file manager — not something you can drive over adb.

---

## 6. Install the WeKit module and scope it to WeChat

Get WeKit from **[github.com/Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit)**.

Be aware of how it is distributed: **the repository publishes no releases.** Builds come from CI artifacts (which require a GitHub login and expire) or the project's community channels. Verify what you install; you are about to hand it your WeChat session.

Do not use the `Johnny520/wcx` fork — it is a rename of WeKit with a higher minimum Android version and nothing maintaining it.

Then:

1. Install the WeKit APK (`adb install -g wekit-standard.apk` grants its permissions up front).
2. Open the **Vector/LSPosed manager → Modules → WeKit**.
3. Turn the module **on** and set its **scope to WeChat only**. Do not scope it to other apps. Apply.
4. **Force-close WeChat and reopen it** so the framework injects into a fresh process. Until you do this, the module is enabled but not loaded.

Confirm the injection actually happened — do not assume. Right after WeChat starts:

```bash
adb logcat -d | grep -iE "vector|xposed|wekit|dexkit"
```

You are looking for lines equivalent to these (exact wording varies by version):

```
Vector: Loading Vector/Xposed for com.tencent.mm
Loaded module dev.ujhhgtg.wekit successfully
WeKit: hooking Application.attachBaseContext
Load libdexkit.so ... ok
```

If you see none of these, go to §Troubleshooting → "the module is enabled but nothing is hooked".

> **After this one-time force-close, stop force-stopping WeChat.** See the red warning in Troubleshooting — it is the single easiest way to break a working setup.

---

## 7. Enable WeKit's API + MCP server

Inside **WeChat itself**, WeKit adds its own settings entry (the exact label depends on module version and language — look for WeKit's settings / features screen, then the "API + MCP server" item).

1. **Set the bearer token.** WeKit's built-in default is the placeholder literal `your_token`. **Change it.** Anything that can reach the port and knows the token can read your messages and send as you.
2. **Set the port to `3001`** (the default, and what this project's docs and scripts assume).
3. Turn the feature **on**.

While you are in WeKit's settings, also enable its options for:

- **disabling WeChat's hot update / tinker** (§5), and
- **hiding Xposed from WeChat** (anti-detection).

Those three — a real token, hot-update off, Xposed hidden — are the deployment switches you should never skip.

**Security facts about this server:**

| | |
|---|---|
| Auth | A single static bearer token. That is the entire access-control model |
| Transport | Plain HTTP. The token and every message body cross your LAN in cleartext |
| Bind address | The server binds **all interfaces (0.0.0.0)** and this is not configurable | 

Treat :3001 as a LAN-only service. Never port-forward it to the internet, and if you use the router DNAT transport, make sure the interface you expose it on faces a private network you control — never an internet-facing WAN. Use a long random token.

The feature toggle **persists across reboots**, and the server starts automatically whenever WeChat starts with the module loaded. There is **no separate "start on boot" option** in the dialog (it exposes only the token and the port), so keeping the channel up is equivalent to keeping *WeChat* running — see §10.

---

## 8. The first launch is slow — do not panic

After you enable the module (and again after a WeChat update or reinstall), WeKit's first launch runs a **full DexKit scan** of the WeChat app.

**This takes minutes** — two to three on a Pixel 9 Pro, longer on slower hardware. During that window:

- :3001 does not answer.
- `curl` gets connection-refused or hangs.
- Nothing in the UI tells you a scan is in progress.

It looks exactly like a broken install. Wait it out before you start changing things. `adb logcat | grep -i dexkit` if you want to watch.

---

## 9. Find the phone's IP and verify from the agent host

Get the phone's WiFi address:

```bash
adb shell ip -4 addr show wlan0        # or Settings → About phone → Status
```

Two things worth doing now to keep this address stable, because the transport depends on it:

- Give the phone a recognisable device name in Android's settings — the router DNAT script finds it by **DHCP lease name** (`WEKIT_PHONE_HOSTNAME`), not by IP.
- Add a DHCP reservation for it on your router.

On an OpenWrt-family router you can read the lease table directly, which is exactly what the script does:

```sh
cat /var/dhcp.leases
```

Now — **from the machine that runs Hermes**, not from the phone and not from your laptop — verify the API:

```bash
curl -sS http://192.168.1.50:3001/api/self/info \
     -H "Authorization: Bearer YOUR_TOKEN"
```

A healthy response identifies the logged-in account:

```json
{"wxId":"wxid_xxxxxxxx","customWxId":"YourWeChatID"}
```

That is exactly the request the plugin makes at startup (it retries a few times before giving up), so if this works, the plugin's `connect()` will too.

A second useful probe — the contacts list:

```bash
curl -sS "http://192.168.1.50:3001/api/contacts?type=friends" \
     -H "Authorization: Bearer YOUR_TOKEN"
```

Valid `type` values are `all`, `friends`, `groups`, `official_accounts` — plural; `friend` is rejected.

**If the agent host and the phone are on different subnets**, this curl times out even though everything on the phone is perfect. That is a routing problem, not a setup problem — see the transport documentation and `wekit-dnat.sh`. If they share a subnet, point `WEKIT_BASE_URL` straight at the phone and skip the script entirely.

> **Whitelist tip — this one has bitten before.** When you set `WEKIT_ALLOWED_USERS`, take the id from the adapter's own log line (`wechat-wekit: inbound from <convId> type=…`) after the person has actually messaged the agent — **not** from the friends list. A contact's entry in your address book is not necessarily the account they message you from, and a mismatched whitelist drops their messages *silently*: the drop is logged at debug level, so at default log level you see nothing at all. Also note **plugin log lines go to Hermes' `agent.log`, not `gateway.log`** — looking in the wrong file makes a working poll loop look dead.

---

## 10. Keeping it up

The channel is alive exactly as long as WeChat is running with the module loaded. What helps:

- **Exempt WeChat from battery optimisation / Doze** so the system does not background-kill it.
- **Keep the phone on power.**
- **A watchdog on the agent side.** `ops/wechat_watchdog.py` in this repo is the reference implementation: it health-checks over **HTTP**, on the same path the agent uses, every couple of minutes, and only touches adb after several consecutive HTTP failures — at which point it checks whether WeChat is running and launches it with `monkey` if it is not. In the healthy case it makes **zero** adb calls. It **never** force-stops WeChat. Configure it with `WEKIT_BASE_URL` / `WEKIT_TOKEN`, plus `WEKIT_ADB_SERIAL`, `WEKIT_ADB_PATH` and `WEKIT_LOG_PATH` for the adb fallback.
- **After a phone reboot**, WeChat does not necessarily start on its own. The watchdog launching it is enough, and that whole path has been verified end-to-end: watchdog detects the API down → `monkey` launches WeChat → the module loads → DexKit scans for a few minutes (§8) → the server comes back by itself, because the feature toggle survived the reboot. No manual toggling required.

Do **not** build a watchdog that polls over `adb` on a short interval, and do not put the transport itself on `adb forward`. On the reference host the adb server crashed and respawned every 10–30 seconds entirely on its own — the USB device never dropped — which vaporised the forward roughly every 21 seconds. See the transport docs for the measurements.

---

## Troubleshooting

### `curl` gets connection refused / no response on :3001

Work through in this order:

1. **Did you just enable the module or update WeChat?** Wait out the DexKit scan (§8).
2. **Is WeChat running?** `adb shell pidof com.tencent.mm`. No pid = no server.
3. **Is anything listening?** `adb shell netstat -tln | grep 3001` (add `-p` from a root shell to confirm the socket belongs to `com.tencent.mm`).
4. **Is the feature toggle still on?** Reopen WeKit's settings inside WeChat.
5. **Is it the network rather than the phone?** Test from a machine on the *same subnet as the phone*. If that works and the agent host does not, it is routing — go to the transport docs, not back to the phone.
6. **Did WeChat auto-update?** Check the version. An unsupported build silently disables the hooks (§5).

### HTTP 401

The token in your request does not match the token in WeKit's server settings. Check for a trailing newline or space in whatever you put in `.env`, confirm the header is `Authorization: Bearer YOUR_TOKEN`, and re-read the token in WeKit's dialog on the phone. The plugin reads `WEKIT_TOKEN` at startup, so restart the gateway after changing it.

### The module is enabled but nothing is hooked

`adb logcat -d | grep -iE "vector|xposed|wekit|dexkit"` right after WeChat starts. Empty output means the framework never injected.

| Cause | Fix |
|---|---|
| Zygisk off | Magisk → Settings → Zygisk on → reboot |
| Framework not active | Open the Vector/LSPosed manager and check its status screen |
| Module scope does not include WeChat | Manager → Modules → WeKit → tick WeChat → Apply |
| WeChat not restarted after enabling scope | Close and reopen it (§6, step 4) |
| WeChat hot-patched itself (tinker) | Causes **silent** non-loading. Disable hot update in WeKit's settings; clearing an already-applied patch means deleting its directory with an on-device file manager |
| Unsupported WeChat version | Go back to 8.0.72 |
| You used NPatch instead of root + Xposed | Does not work for WeKit — `libdexkit.so` never loads |

### 🔴 Never force-stop WeChat

Once WeChat is running with the module injected, **do not force-stop it** — not from Settings → Apps → Force stop, not with `am force-stop com.tencent.mm`, and above all not from any script or watchdog.

On the reference deployment a force-stop left the Xposed injection in a broken state that only a **full phone reboot** recovered. The port stays closed and nothing in the logs explains why.

The one-time force-close in §6 is how you get the module loaded the first time, on a process that is not yet injected. After that, if you need WeChat restarted, **reboot the phone** and let it come back cleanly. Any automation you write should be able to *launch* WeChat (e.g. `monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1`) and must never be able to stop it.

### Root disappeared after a system update

Expected — the OTA overwrote `init_boot`. Re-run §3 with the `init_boot.img` from the *new* build's factory image.

### `adb shell su` returns `su: request rejected (2000)` instantly

A stored deny policy for the Shell UID, not SELinux and not a settings problem. Magisk app → **Superuser** tab → enable the **`[SharedUID] Shell`** entry. Changing "Automatic response" does not help, because the stored policy takes precedence.

### Anything involving `/data/adb`

SELinux blocks the `su` domain from reading and writing content under `/data/adb`, so `cp` / `chmod` / `cat` there fail with Permission denied. Install modules through the Magisk app or `magisk --install-module` rather than copying files in by hand; the one read-out you do need (the Vector manager APK) is the `setenforce` dance in §4.

---

## Done? Check these

| Check | Expected |
|---|---|
| `fastboot getvar unlocked` | `yes` |
| Magisk app | Reports a version, Zygisk on |
| Framework manager | Framework **active** |
| `adb logcat -d \| grep -i wekit` after WeChat starts | Module loaded, `libdexkit.so` ok |
| WeKit settings in WeChat | API+MCP server on, port 3001, **token changed**, hot update disabled, Xposed hidden |
| `curl …/api/self/info` **from the agent host** | 200 with your `wxId` |

That last line is the entire phone-side contract this plugin depends on.

Next: pick a transport (WiFi is strongly recommended over USB — the transport docs have the measured reasons), set `WEKIT_TOKEN` / `WEKIT_BASE_URL` / `WEKIT_ALLOWED_USERS`, and enable the plugin in Hermes.

And before you rely on it for anything, re-read the callout at the top of this page and the architecture notes on **edge-triggered inbound delivery**. Messages that arrive while the poll loop is between `wait-for-new-message` calls are lost permanently, with no cursor to recover them. The plugin mitigates the window by dispatching replies as background tasks so the listener is re-armed immediately — it cannot close it.
