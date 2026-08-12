# Troubleshooting

中文: [troubleshooting.md](troubleshooting.md)

A symptom-driven runbook for `hermes-wechat-wekit`. Each entry is **Symptom → How to confirm → Root cause → Fix**.

> **Before anything else — legal / ToS.** Automating a personal WeChat account violates WeChat's terms of service. A ban does not just cost you the account: it freezes the WeChat Pay balance attached to it. Use a **dedicated secondary account** on a dedicated device, keep traffic low and human-scale, and treat this project as research / personal use only. Note also that WeChat allows one active session per account — logging that account in on the phone signs it out everywhere else. Nothing in this document makes automation safe from a policy standpoint.

> **Security.** WeKit's API server listens on all interfaces and its bearer token travels over **plaintext HTTP**. Anything that can reach port 3001 with that token has full read/write control of the WeChat account — the API surface is far larger than what this plugin uses. Keep it on a trusted LAN, use a long random token, and never port-forward it to the internet.

---

## 0. Orientation

Four facts that explain most of the confusion people hit:

1. **Inbound is edge-triggered.** WeKit's `wait-for-new-message` MCP tool registers a WCDB listener *only for the duration of the call* and removes it in a `finally` block. There is no queue, no buffer, and no cursor. **Any WeChat message that arrives while the poll loop is not inside a wait call is lost permanently and cannot be recovered.** This is upstream WeKit behaviour, not a bug in this plugin, and there is no backfill in v0.1. See §6.
2. **Plugin log records go to `agent.log`, not `gateway.log`.** See §8.
3. **Transport instability is the single biggest source of "it works sometimes".** See §4.
4. **`WEKIT_BASE_URL` is required and has no default.** If it is unset the platform refuses to connect and says so in the log — it does not guess an address. See §2.

Find your log first — every section below greps it:

```bash
# Hermes home is usually ~/.hermes (i.e. /root/.hermes when the gateway runs as root)
ls -l ~/.hermes/logs/
# if you're not sure:
find / -name 'agent.log' -path '*hermes*' 2>/dev/null
```

Set a few shell variables so the commands below paste cleanly:

```bash
export AGENT_LOG=~/.hermes/logs/agent.log
export WEKIT_BASE_URL=http://192.168.1.50:3001     # your phone / router DNAT address
export WEKIT_TOKEN=YOUR_TOKEN
```

---

## 1. The platform never appears at all

### Symptom

`wechat-wekit` is missing from `hermes gateway status`, or the gateway starts with no `wechat-wekit:` lines whatsoever in `agent.log` — not even a failure.

### How to confirm

```bash
grep -c 'wechat-wekit' "$AGENT_LOG"                      # 0 = never instantiated
grep -E '^WEKIT_TOKEN=' ~/.hermes/.env                   # is it set at all?
```

### Root cause

`WEKIT_TOKEN` gates registration. `check_requirements()`, `validate_config()` and the env-enablement hook all return false/None when it is unset, so the platform is treated as not configured and is never brought up. There is no error for this — an unconfigured platform is a normal state.

The second, sneakier variant: the token *is* in your shell but **not in the environment of the gateway process**. Exporting it in an interactive shell does nothing for a systemd-managed gateway.

### Fix

Put it where the gateway actually reads its environment (typically `~/.hermes/.env`), then restart the gateway:

```bash
# ~/.hermes/.env
WEKIT_TOKEN=YOUR_TOKEN
WEKIT_BASE_URL=http://192.168.1.50:3001
WEKIT_ALLOWED_USERS=wxid_xxxxxxxx
WEKIT_ALLOW_ALL_USERS=false
```

Also confirm the plugin itself is enabled in `config.yaml` under `plugins.enabled` as **`wechat-wekit-platform`** — the plugin's package name carries the `-platform` suffix, while the platform it registers is named `wechat-wekit`. They are not the same string and mixing them up produces a silent no-op.

---

## 2. `connect()` fails — "cannot reach WeKit API"

### Symptom

At gateway start:

```
wechat-wekit: cannot reach WeKit API at http://192.168.1.50:3001 after retries: <reason>.
Is the phone forwarded and WeKit API server on?
```

`connect()` performs `GET /api/self/info` up to 4 times, 1.5 s apart, and refuses to bring the platform up unless one returns 200. Two things in that line are the discriminators — **the address it names** and **`<reason>`**. Read both.

**If instead of this line you see `WEKIT_BASE_URL is not set`**, that is the whole problem: the variable is required and the platform will not guess. Set it (§0, fact 4) and restart.

### How to confirm

```bash
curl -sS -v "$WEKIT_BASE_URL/api/self/info" -H "Authorization: Bearer $WEKIT_TOKEN"
```

| Result | Root cause | Fix |
|---|---|---|
| `200` + a small JSON body naming the logged-in account | API is fine — the failure was transient at boot | Restart the gateway; if it recurs, see §4 |
| `401` (reported as `http 401` in the log line) | Token mismatch between `WEKIT_TOKEN` and the token set in WeKit's "API + MCP server" settings on the phone | Re-read the token from the phone's WeKit settings dialog and update `.env`. Do not leave WeKit's shipped default token in place. |
| `Connection refused` | Nothing is listening on :3001 — WeChat is not running, WeKit's API server toggle is off, or the module didn't load | See §7 |
| Hangs, then timeout / `000` (no RST at all) | Packets aren't reaching the phone: wrong host in `WEKIT_BASE_URL`, missing/broken DNAT, phone on a different subnet, or a firewall in between | §4 — fix the transport, then re-run this curl |
| `200` but the account named is not the one you expect | The phone is logged into the wrong WeChat account | Log the phone into the dedicated secondary account (and remember that doing so signs that account out everywhere else) |

**Not a fault, so don't chase it:** on a USB `adb forward` setup, `curl 127.0.0.1:3001` from inside WSL will *never* work. `adb forward` binds Windows' loopback, and WSL2 has its own network namespace with an isolated loopback. Test from the Windows side, or — better — stop using USB (§4).

---

## 3. The agent never receives *any* message

**Check the whitelist first.** A single wrong character in a wxid silently discards every inbound message, and the discard is logged at `DEBUG` — invisible at the default log level. In the reference deployment this cost hours: the operator read the friend list from the API, whitelisted the wxid it returned, and the account the human was actually messaging *from* was a different one. The poll captured every message correctly; the adapter dropped all of them with no visible line.

### How to confirm

The authoritative list of ids that have actually reached the adapter is in the log. The poll loop logs every captured inbound **before** `_dispatch` (and therefore before the whitelist) runs:

```bash
grep 'wechat-wekit: inbound from' "$AGENT_LOG" | tail -20
# just the distinct ids:
grep -o 'wechat-wekit: inbound from [^ ]*' "$AGENT_LOG" | awk '{print $NF}' | sort -u
```

Compare against your configuration:

```bash
grep -E '^WEKIT_(ALLOWED_USERS|ALLOW_ALL_USERS)=' ~/.hermes/.env
```

Three outcomes:

| What you see | Meaning | Go to |
|---|---|---|
| `inbound from wxid_xxxxxxxx` lines exist, but that id is **not** in `WEKIT_ALLOWED_USERS` | Whitelist drop — this section |
| `inbound from …` lines exist and the id **is** whitelisted | The message reached the agent; the failure is downstream (LLM, send). Look for `dispatch failed:` in `agent.log` and the gateway's own `inbound message: platform=wechat-wekit` line |
| **No** `inbound from …` lines at all | Nothing is being captured. Go to §2 (can't reach WeKit) and §4 (polls dying) |

### Root cause

`_dispatch` computes `who = {conv_id, sender}` and drops the message unless that set intersects `WEKIT_ALLOWED_USERS` — but **only when the whitelist is non-empty and `WEKIT_ALLOW_ALL_USERS` is off**. The drop is `logger.debug("wechat-wekit: drop msg from unlisted %s", conv_id)` — below `INFO`, so on a default-level install you get complete silence with zero error indication.

Note the corollary, because it is the opposite failure: **an empty or unset `WEKIT_ALLOWED_USERS` means no filtering at all.** The whitelist is not fail-closed.

### Fix

Put the id you saw in `inbound from` into the whitelist. Do **not** derive it from the contact list — the contact list tells you who is a friend, not which account is talking to the bot, and a person can message you from an account that isn't in the list you looked at.

```bash
# ~/.hermes/.env  — comma separated; surrounding whitespace is stripped
WEKIT_ALLOWED_USERS=wxid_xxxxxxxx,wxid_yyyyyyyy
WEKIT_ALLOW_ALL_USERS=false
```

Restart the gateway, then confirm the message now gets past the filter (the gateway's `inbound message: platform=wechat-wekit` line, plus a reply attempt).

**Related traps in the same area:**

- **Groups.** `conv_id` for a group ends in `@chatroom` (or `@im.chatroom`). The filter matches on `{conv_id, sender}`, so whitelisting the *chatroom id* admits the whole group, while whitelisting a *member wxid* admits only that member. Caveat: the `inbound from` line prints **`conv_id` only** — the sender wxid is not logged, so in practice the workable option for groups is to whitelist the chatroom id you can see.
- **Testing from the agent's own account.** `wait-for-new-message` does not report self-sent messages. Messaging the bot from the phone it runs on produces nothing — that is expected, and it is also why there is no echo loop. Test from a second account.
- **`WEKIT_ALLOW_ALL_USERS`** accepts `1`, `true`, or `yes` (case-insensitive) and disables the filter entirely. It is a debugging aid, not a configuration: an account with automation enabled and no inbound filter will answer anyone who has its wxid, including groups and spam. Do not leave it on.
- Values may also come from the plugin config's `extra.allowed_contacts`; a non-empty `WEKIT_ALLOWED_USERS` **replaces** that list rather than adding to it.

---

## 4. Messages arrive only intermittently — "it works sometimes"

### How to confirm

Count poll errors per hour:

```bash
grep 'wechat-wekit: poll error' "$AGENT_LOG" \
  | awk '{print substr($0,1,13)}' | sort | uniq -c
```

(`substr($0,1,13)` buckets by `YYYY-MM-DD HH` — adjust if your log timestamp format differs.)

A healthy channel shows **zero** poll errors plus a periodic heartbeat — printed on every 5th successful poll that returned no message, so on the default 30 s poll roughly every 2.5 minutes when idle:

```bash
grep 'wechat-wekit: poll alive' "$AGENT_LOG" | tail -5
```

> ⚠️ When comparing "before vs after a fix", always filter with a **date-aware** comparison — `awk '$0 >= "2026-01-01 23:45:51"'` — not `sed -n '/23:45:51/,$p'`. A bare time substring matches historical lines and will inflate your counts by an order of magnitude. This mistake produced a wildly wrong error count once already.

### Root cause

Almost always the transport. On the reference deployment the phone was USB-attached and reached via `adb forward`, and **the Windows adb server crashed and respawned on its own every 10–30 seconds**. Passive observation (zero adb calls of our own) caught the server PID cycling continuously while `adb devices` reported `device` the whole time — the USB link never dropped; the *server process* was dying. Forward rules live in adb-server memory, so every crash vaporised the forward and killed the in-flight long poll.

Measured impact: **~170 poll errors/hour — a break roughly every 21 seconds.** For a message to be received it had to land inside a sub-21-second window when the listener happened to be armed. Hence "sometimes it answers".

The same host, after switching to the WiFi/DNAT path: **55-second long polls completing intact, 0 poll errors.**

There is a second-order effect that makes this worse than the raw error rate suggests: after a poll error the loop discards the MCP session and sleeps with exponential backoff (1 s → 2 s → 4 s → … capped at 30 s) before re-arming. The channel is deaf for the whole backoff, and edge-triggered means those messages are gone.

### Fix

Move the transport off USB.

| Topology | What to do |
|---|---|
| Agent host and phone on the **same subnet** | Point `WEKIT_BASE_URL` straight at the phone: `http://192.168.1.50:3001`. No script, no adb, nothing else. |
| Agent host and phone on **different subnets** (typical: PC on the upstream router, phone on a downstream router's LAN) | Run `wekit-dnat.sh` on the router that owns the phone's LAN, and point `WEKIT_BASE_URL` at that router's WAN address. |
| USB `adb forward` | **Not recommended.** Keep it only as an emergency fallback. |

Verify from the agent host:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' "$WEKIT_BASE_URL/api/self/info" \
  -H "Authorization: Bearer $WEKIT_TOKEN"     # expect 200
```

and, on an OpenWrt-family router running the DNAT script:

```sh
sh /etc/wekit-dnat.sh                              # idempotent; safe to re-run
iptables -t nat -S PREROUTING  | grep wekit-dnat   # DNAT to the phone's current IP
iptables -S FORWARD            | grep wekit-dnat
iptables -t nat -S POSTROUTING | grep wekit-dnat
logread | grep wekit-dnat | tail
```

How the script behaves, and why:

- It resolves the phone's current IP from the DHCP lease file (`/var/dhcp.leases`) by hostname (`WEKIT_PHONE_HOSTNAME`), so a lease change self-heals.
- If the rule already points at the correct IP it **exits without touching anything** — deliberately, so a periodic re-run does not tear down an active long poll.
- Every rule it installs carries a `wekit-dnat` comment, and it only ever deletes rules bearing that comment, so it will not disturb a co-resident transparent proxy's iptables rules.
- Persist it via `rc.local` plus a short cron interval (2 minutes works).

> 🔴 **Failure mode specific to this script: if the hostname isn't found in the lease file, it exits 0 silently.** No rule, no log line, nothing to grep. Symptom: `curl` from the agent host times out and `logread` shows nothing at all. Confirm with `cat /var/dhcp.leases` and set `WEKIT_PHONE_HOSTNAME` to the exact hostname field the phone registers; a DHCP reservation makes this stable. If the phone uses a static IP it will never appear in the lease file — either give it a reservation instead, or hard-code the address.

> **Rule ordering:** the script inserts at position 1 of `PREROUTING`. If your router also runs a transparent proxy that hijacks the port you chose, check that nothing has been inserted ahead of it.

> If you must stay on USB: expect this failure mode, and note that `adb shell pidof …` momentarily disturbs the forward, so a probe issued right after an adb call can return HTTP 000 that is a pure artifact. Probe with plain `curl`, never with adb in the loop.

---

## 5. Long polls die early — a poll error at a suspiciously regular interval

### Symptom

`wechat-wekit: poll error: ...` appears at a fixed cadence — for example every ~60 s — regardless of message traffic, and the interval doesn't match anything on the network.

### How to confirm

Compare the two timeouts. The long-poll duration is yours to set; the HTTP read timeout is not:

```bash
grep -E '^WEKIT_POLL_TIMEOUT_MS=' ~/.hermes/.env      # your long-poll duration, ms
```

In `connect()` the HTTP client is built as `httpx.Timeout(15.0, read=poll_timeout_ms/1000 + 15)`, so the read timeout always outlives the poll by 15 s and cannot be the cause here. (It could be in older revisions, which hardcoded `read=60.0`.) Then time one poll by hand:

```bash
# 1) open an MCP session and capture the session id from the response headers
curl -sS -D - -o /dev/null -X POST "$WEKIT_BASE_URL/mcp" \
  -H "Authorization: Bearer $WEKIT_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}' \
  | grep -i '^mcp-session-id:'

SID=<paste>

# 2) complete the handshake, exactly as the adapter does
curl -sS -o /dev/null -X POST "$WEKIT_BASE_URL/mcp" \
  -H "Authorization: Bearer $WEKIT_TOKEN" -H "mcp-session-id: $SID" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

# 3) run one wait
time curl -sS -X POST "$WEKIT_BASE_URL/mcp" \
  -H "Authorization: Bearer $WEKIT_TOKEN" -H "mcp-session-id: $SID" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"wait-for-new-message","arguments":{"timeout-ms":30000}}}'
```

With no traffic this should block for ~30 s and return a "No new message" text result.

### Root cause

Two distinct ones — the timing tells them apart:

- **Errors at ~60 s intervals:** `WEKIT_POLL_TIMEOUT_MS` is at or above the client's 60 s read timeout, so the HTTP read gives up before WeKit returns its normal empty result. Every poll ends in an exception, the MCP session id is discarded, and backoff kicks in.
- **Errors at some *other* fixed interval well under your poll timeout:** something in the path is severing idle TCP connections — a NAT/conntrack idle timeout, a firewall, or the adb-server crash cycle of §4.

### Fix

- `WEKIT_POLL_TIMEOUT_MS` is safe to raise: the HTTP read timeout is derived from it (poll + 15 s), so it can no longer be outrun by a longer poll. The default is 30000, which is what the reference deployment runs; 55000 was exercised during transport testing and completed intact. Values under 5000 are clamped up to 5000, and an unparseable value falls back to 30000.
- For a severed-connection interval, fix the path (§4) rather than shortening the poll — a shorter poll means more gaps between waits, and every gap is deaf.

---

## 6. Messages sent while the agent was replying went missing

### Symptom

A user sends three messages in quick succession. The agent answers the first and behaves as if the other two were never sent. Or: the gateway was restarted and everything sent during the restart is gone.

### How to confirm

```bash
grep 'wechat-wekit: inbound from' "$AGENT_LOG" | tail -20
```

If a message the user swears they sent has **no** `inbound from` line, the plugin never saw it. It is not recoverable — do not go looking for it in a queue, because there isn't one.

### Root cause

This is the edge-trigger property, and it is expected behaviour, not a defect in your deployment.

`wait-for-new-message` arms a WCDB listener when the call starts and removes it in a `finally` when the call returns. Between two calls the channel is deaf, and there is no queue, buffer, or cursor to replay from — messages that arrive in a gap are gone permanently.

The plugin minimises the gap rather than eliminating it. Each captured message is dispatched as a **background asyncio task**, so the poll loop can re-arm the listener immediately instead of blocking. This matters enormously: `_dispatch` awaits all the way through the LLM producing a reply, which is seconds to tens of seconds. Awaiting it inline would make the channel deaf for that entire window every single time it answered — precisely the window in which a user is most likely to send a follow-up.

Gaps that remain in v0.1:

| Gap | Duration | Notes |
|---|---|---|
| Between two `wait-for-new-message` calls | one HTTP round trip | The floor. Unavoidable without upstream changes. |
| Re-`initialize` after a dropped MCP session | one extra round trip | Follows any poll error. |
| Poll error backoff | 1 s → 30 s, doubling | This is why §4 matters so much. |
| Gateway restart / redeploy | seconds to minutes | Announce restarts, or do them when nobody is messaging. |
| Unparseable wait result | one message | See below. |

### Fix

There is no fix that recovers a lost message. What you can do:

- Drive poll errors to zero (§4). With a stable transport the loss window is a single round trip and normal question-and-answer traffic is reliable; rapid-fire bursts may still drop one.
- Restart the gateway deliberately, not casually.
- Tell users of the channel, honestly, that a burst of messages may lose one. Do not present this channel as lossless.

A **history-based backfill is not implemented**. WeKit does expose per-conversation history, but the data it returns is `sender: content` pairs with no timestamp and no message id, so reconciling it against what was already delivered requires positional diffing — which collides directly with a user legitimately sending the same words twice. The adapter deliberately does **not** dedup on content for exactly this reason. (`wait-for-new-message` fires once per DB insert, so it does not re-deliver; the only duplication risk would come from a backfill that doesn't exist yet.)

**One more silent-loss path worth grepping for:** if WeKit's wait result doesn't match the adapter's parser, the message is discarded with a `DEBUG` line:

```bash
grep 'wechat-wekit: unparsed wait result' "$AGENT_LOG"
```

The parser is anchored on exactly `ConvId='…',Sender='…',Type=N,Content='…'`. Realistically what defeats it is a WeKit version that changes that output format. If you see these lines, report the (redacted) shape upstream and here.

---

## 7. After a phone reboot, the API never comes back

### Symptom

The phone rebooted. `curl /api/self/info` returns `Connection refused` indefinitely. The gateway logs the §2 connect failure and the channel stays down.

### How to confirm

```bash
adb -s YOUR_SERIAL shell pidof com.tencent.mm       # empty = WeChat isn't running
adb -s YOUR_SERIAL shell 'ss -ltn | grep 3001'      # nothing listening = server not up yet
```

If you are calling a Windows `adb.exe` from WSL over ssh, redirect explicitly or the call will appear to hang — the adb server daemon inherits ssh's stdout pipe and ssh never sees EOF:

```bash
adb -s YOUR_SERIAL shell pidof com.tencent.mm </dev/null >/tmp/adbout 2>&1; cat /tmp/adbout
```

### Root cause

Two things, in order:

1. **WeChat itself does not necessarily auto-start after a reboot.** WeKit's "API + MCP server" toggle *is* persistent across reboots — it stays on — but the server is hosted inside the WeChat process. No WeChat process, no server. The settings dialog offers a token and a port; it has no "start on boot" option.
2. **Even once WeChat starts, the server takes minutes to appear.** On a cold start the module runs a full DexKit scan first; two to three minutes of `Connection refused` immediately after launch was normal in the reference deployment. Do not conclude the module is broken and start reinstalling things.

### Fix

Relaunch WeChat with `monkey`, then wait:

```bash
adb -s YOUR_SERIAL shell monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1
# then poll for a few minutes:
for i in $(seq 1 30); do
  curl -sS -o /dev/null -w "$i %{http_code}\n" "$WEKIT_BASE_URL/api/self/info" \
    -H "Authorization: Bearer $WEKIT_TOKEN"
  sleep 20
done
```

> 🔴 **Never `am force-stop com.tencent.mm` as part of recovery.** In the reference deployment a force-stop left the Xposed module unloaded and required a full phone reboot to get the channel back — which is why the watchdog is built to launch WeChat and never to stop it. Use `monkey` to bring it up; that's all.

To automate this, run the `wechat_watchdog.py` example (`ops/`). Its design is deliberate and worth copying if you write your own:

- The health probe is **plain HTTP over the same path Hermes uses** (`GET /api/self/info`), on a slow interval (120 s default). In the healthy case it makes **zero adb calls** — an earlier version probed with `adb shell pidof` every 60 s, and that adb traffic was itself a source of instability.
- adb is touched only after several consecutive HTTP failures (3 by default), and then only to check whether WeChat is alive and `monkey` it if not.
- If HTTP is failing but WeChat *is* alive, it logs that and **changes nothing** — that combination points at the network/DNAT/WeKit-server layer, not the app, and blindly restarting WeChat would make things worse.
- It waits out a boot grace period before its first probe.

Configure it via `WEKIT_BASE_URL` / `WEKIT_TOKEN` / `WEKIT_ADB_SERIAL` / `WEKIT_ADB_PATH` / `WEKIT_LOG_PATH`. **Always pin `WEKIT_ADB_SERIAL` if more than one device is attached** — an unqualified adb call will drift between devices, and in the reference deployment two similar phones were plugged into the same host, only one of which was the rooted one.

---

## 8. Plugin logs appear nowhere

### Symptom

You `grep wechat-wekit gateway.log` and get nothing at all — not even the startup lines — so you conclude the plugin never loaded or the poll loop never started. That conclusion is wrong, and it is an easy hour to lose.

### How to confirm

```bash
grep -c 'wechat-wekit' ~/.hermes/logs/gateway.log     # typically 0
grep -c 'wechat-wekit' "$AGENT_LOG"                   # this is where they are
```

### Root cause

`gateway.log` is filtered by logger namespace: a component filter admits only records from the `gateway.*` logger tree. A Hermes plugin's module is imported under the plugin namespace (`hermes_plugins.*`), so `logging.getLogger(__name__)` inside `adapter.py` produces records that **do not match the gateway filter** and are never written to `gateway.log`. They land in `agent.log` instead.

Nothing is misconfigured. You are reading the wrong file.

### Fix

Grep `agent.log` for everything plugin-side:

```bash
grep 'wechat-wekit' "$AGENT_LOG" | tail -50

# the lines that matter most:
grep 'wechat-wekit: connected to'          "$AGENT_LOG" | tail -3   # connect() succeeded
grep 'wechat-wekit: inbound poll loop'     "$AGENT_LOG" | tail -3   # started / stopped
grep 'wechat-wekit: poll alive'            "$AGENT_LOG" | tail -3   # heartbeat
grep 'wechat-wekit: poll error'            "$AGENT_LOG" | tail -20  # should be empty
grep 'wechat-wekit: dispatch failed'       "$AGENT_LOG" | tail -20  # background reply blew up
```

`gateway.log` is still useful for the *other* half of the path — the gateway's own `inbound message: platform=wechat-wekit …` line, which proves the message got past the whitelist and into the agent. Use both files: `agent.log` for capture, `gateway.log` for delivery.

`dispatch failed` exists precisely because dispatch runs as a detached background task — without the done-callback that logs it, a failing reply would vanish with no trace anywhere. If you see these, the channel captured the message fine and something downstream (LLM, send) broke.

---

## 9. Outbound problems

| Symptom | Confirm | Cause / fix |
|---|---|---|
| `send` returns `http <code>: <body>` | `grep 'wechat-wekit' "$AGENT_LOG"` | Outbound text is `POST /api/messages/text` with `{"type":"text","convId":…,"content":…}`. A 401 here is the same token problem as §2. Reproduce with the appendix curl to separate "plugin broken" from "API rejecting". |
| `send` returns `not connected` | — | The adapter's HTTP client is gone: `connect()` failed or `disconnect()` ran. See §2. |
| `send_image` fails with a non-200 | Look for the `http <code>: <body>` error in `agent.log` | Image send is **multipart** `POST /api/messages/image`: form field `convId` plus a file part named exactly `file`, with the bytes uploaded. Do not set `Content-Type` by hand — the HTTP client sets the multipart boundary. The alternative JSON `{convId, path}` mode takes a **phone-local** path and is useless from the agent host. Give the filename a real image extension; WeChat wants one. |
| Image arrives, caption doesn't (or arrives separately) | — | Expected. The caption is sent afterwards as its own plain text message; the image itself carries no caption. If the image succeeds and the caption send fails, the overall result is still reported as success. |
| `send_image` fails with `image load failed:` | — | The reference wasn't resolvable. `_load_image` accepts a local filesystem path, an `http(s)` URL (downloaded by the agent host), a `file://` URI, or a `data:` URI. Note the http(s) branch makes the agent host fetch an arbitrary URL — don't feed it untrusted input. |
| Replies contain markdown that renders as literal `**asterisks**` | — | Expected. WeChat renders no markdown; the platform hint tells the agent to reply in plain text, but models slip. |
| Long replies get cut | — | The platform declares `max_message_length: 2000`; the gateway splits or truncates accordingly. |
| Outbound works for contacts you did not whitelist | — | Expected. `WEKIT_ALLOWED_USERS` filters **inbound only**; outbound is unrestricted. Treat that as a safety consideration, not a feature. |
| Scheduled/cron messages go nowhere | `grep -E '^WEKIT_HOME_CHANNEL=' ~/.hermes/.env` | Scheduled deliveries need `WEKIT_HOME_CHANNEL` set to a convId. Unset, there is no default destination. |
| A reply is cut off mid-generation on gateway restart | — | Expected. `disconnect()` gives in-flight dispatches 10 seconds to finish sending and then cancels them, so a restart during a slow LLM call loses that reply (and, per §6, anything that arrives during the restart). |

---

## 10. Shorter ones

| Symptom | Confirm | Cause / fix |
|---|---|---|
| The agent sees `[image message]`, `[voice message]`, `[file/link/app message]` | Compare with the raw `inbound from … type=N` line | Non-text payloads are decoded into a readable line (filename and size, voice duration, the link's URL). A payload that arrives **empty** falls back to a placeholder from the WeChat type code (unknown codes render as `[typeN message]`). The actual bytes are attached only when media retrieval is on — `WEKIT_MEDIA_ADB_PATH` set **and** the companion phone script installed. Without both, the description is all the agent gets. |
| Log shows less text than the user sent | — | The `inbound from` line truncates the preview to 60 characters. The full text is still dispatched. |
| Contact names show as raw wxids instead of nicknames | `curl -sS "$WEKIT_BASE_URL/api/contacts/wxid_xxxxxxxx" -H "Authorization: Bearer $WEKIT_TOKEN"` | The lookup failed (failures are `DEBUG`-level) and it fell back to the id. The adapter prefers `remarkName`, then `nickname`. Successful lookups are cached per-process, so a name changed on the phone won't refresh until the gateway restarts; failed lookups are *not* cached and are retried on every message. |
| A group's messages are ignored while DMs work | Check what `inbound from` prints for the group | For groups `conv_id` is the chatroom id ending in `@chatroom` / `@im.chatroom`. Whitelist that id to admit the group. In a DM, `conv_id` and `sender` are both the peer's wxid, which is why a bare wxid works there. |
| Duplicate replies / the agent talking to itself | Check which platform plugins are enabled | Two plugins bound to the same WeChat account will double-send and can loop. Enable exactly one WeChat channel — in the reference deployment the older phone-UI plugin had to be explicitly disabled. |
| The agent follows instructions embedded in someone's WeChat message | — | Message content is untrusted input, not instructions. The platform hint says so, but a whitelist is your actual control. Keep `WEKIT_ALLOW_ALL_USERS` off. |

---

## 11. The action tools

### The agent doesn't seem to have the tools

Confirm they registered at all. This loads the plugin exactly the way the gateway does:

```bash
cd /usr/local/lib/hermes-agent && ./venv/bin/python - <<'PY'
from hermes_cli.plugins import PluginManager
PluginManager().discover_and_load()
from tools.registry import registry
d = getattr(registry, "_tools", None) or registry.tools
for n in sorted(n for n in d if n.startswith("wechat_")):
    print(n, d[n].toolset)
from toolsets import resolve_toolset
print(sorted(resolve_toolset("hermes-wechat-wekit")))
PY
```

All seven should print with toolset `hermes-wechat-wekit`. If they do but the agent still cannot call them, the toolset is registered and not *enabled*: for a plugin platform Hermes defaults the enabled toolset to `hermes-{platform}`, which is why the name matches — but a `platform_toolsets:` block for `wechat-wekit` in `~/.hermes/config.yaml` overrides that default, and must then list `hermes-wechat-wekit` explicitly or the tools vanish for this platform only.

If nothing prints, the plugin failed to import. Run the same load with the traceback visible — a syntax error or a bad import in `actions.py` takes the whole platform down with it, so the channel going silent at the same moment is the same fault.

### A write action returns an error mentioning `WEKIT_ENABLE_WRITE_ACTIONS`

Working as designed. Accepting friends, changing group membership, assigning labels and posting to Moments refuse until that variable is truthy. Set it in `~/.hermes/.env` and restart the gateway — and understand what you are turning on: with it set, a message from a whitelisted contact saying "add me to your group" is something the agent can actually carry out.

### `wechat_labels action=set` fails with "no such WeChat label"

Also working as designed, and the error lists the labels that do exist. WeChat only accepts a label that already exists; WeKit resolves each name to an id and **skips** — with a log line, and still a `200` — any name it cannot resolve. So this used to look like success while doing nothing. Create the label in the WeChat app (Me → Contacts → Tags), then assign it.

If the name *is* correct and it still fails, re-read `GET /api/labels`: label creation goes out over a CGI and the row appears only once the server has answered.

### A label assignment "succeeded" but the member list is unchanged

Read it back before believing it — that is the whole lesson of the bug above. The membership query reads `rcontact.contactLabelIds`, which reflects what WeChat has persisted, so an assignment still in flight is invisible. Wait a few seconds and re-read. If it never lands, check that the phone has network: the write is a server round trip, not a local edit.

### `WEKIT_ALLOWED_LABEL` didn't admit anyone

Look in `agent.log` (not `gateway.log` — §8) around the connect for one of:

```
wechat-wekit: allow-list label 'NAME' added N contact(s)
wechat-wekit: allow-list label 'NAME' resolved to no contacts
wechat-wekit: could not resolve allow-list label 'NAME': …
```

The second means the label exists but nobody carries it, or the assignment has not landed yet. The third means the lookup itself failed — the label is then ignored entirely and `WEKIT_ALLOWED_USERS` remains the only gate, which is why you should keep that variable populated rather than relying on the label alone.

Membership is re-read at connect and then every ~10 minutes, and a change is logged as `allow-list changed on refresh: +[...] -[...]`. If you need it immediately, restart the gateway.

### `wechat_send_voice` fails on `edge-tts`

Only the `text` form needs it; `audio_path` with a ready mp3 does not. Install it into the venv the gateway actually runs (`/usr/local/lib/hermes-agent/venv/bin/pip install edge-tts`), not the system Python. The mp3 → SILK conversion happens on the phone, so no local codec is involved.

### A tool reports success but nothing happened in WeChat

Treat every `ok: true` from this API as "the request was accepted", not "the effect exists". Several WeKit endpoints dispatch a CGI and answer immediately. Verify with an independent read — `wechat_pull_history` will show a sent voice message as `<type:voice>`, and the label endpoints will show membership — and prefer that over the write's own return value.

---

## Appendix — diagnostic commands

**Reachability and auth** (run from the agent host, always from there — a probe from your laptop proves nothing about the gateway's path):

```bash
curl -sS -o /dev/null -w '%{http_code}\n' "$WEKIT_BASE_URL/api/self/info" \
  -H "Authorization: Bearer $WEKIT_TOKEN"
```

**Who is the phone logged in as:**

```bash
curl -sS "$WEKIT_BASE_URL/api/self/info" -H "Authorization: Bearer $WEKIT_TOKEN"
```

**Contact / group ids** (valid `type` values: `all`, `friends`, `groups`, `official_accounts` — all plural; singular forms are rejected):

```bash
curl -sS "$WEKIT_BASE_URL/api/contacts?type=friends" -H "Authorization: Bearer $WEKIT_TOKEN"
```

**Send a test message without involving Hermes** (`filehelper` is WeChat's own file-transfer assistant — safe target, no third party involved):

```bash
curl -sS -X POST "$WEKIT_BASE_URL/api/messages/text" \
  -H "Authorization: Bearer $WEKIT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"text","convId":"filehelper","content":"probe"}'
```

**Channel health at a glance:**

```bash
grep 'wechat-wekit' "$AGENT_LOG" | tail -50
grep -c 'wechat-wekit: poll error' "$AGENT_LOG"                        # target: 0
grep 'wechat-wekit: poll error' "$AGENT_LOG" \
  | awk '{print substr($0,1,13)}' | sort | uniq -c                     # per-hour buckets
grep -o 'wechat-wekit: inbound from [^ ]*' "$AGENT_LOG" \
  | awk '{print $NF}' | sort | uniq -c                                 # who actually talks to it
```

**Router DNAT path:**

```sh
sh /etc/wekit-dnat.sh
cat /var/dhcp.leases | grep -i <your phone hostname>    # the script silently exits if this is empty
iptables -t nat -S PREROUTING  | grep wekit-dnat
iptables -S FORWARD            | grep wekit-dnat
iptables -t nat -S POSTROUTING | grep wekit-dnat
logread | grep wekit-dnat | tail
```

**Phone:**

```bash
adb -s YOUR_SERIAL shell pidof com.tencent.mm
adb -s YOUR_SERIAL shell 'ss -ltn | grep 3001'
adb -s YOUR_SERIAL shell monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1
# NEVER as a recovery step: adb shell am force-stop com.tencent.mm
```

**Is the adb server crashing on its own?** (Windows, passive — this must not call adb, or you are measuring your own disturbance):

```powershell
while ($true) {
  "{0}  {1}" -f (Get-Date -f HH:mm:ss),
    ((Get-Process adb -ErrorAction SilentlyContinue | Select-Object -Expand Id) -join ',')
  Start-Sleep 5
}
```

A PID that changes every 10–30 s while `adb devices` still reports `device` is the §4 failure. Stop debugging the plugin and move the transport to WiFi.
