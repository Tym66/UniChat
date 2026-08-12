# Getting the agent to the phone

The plugin needs to reach WeKit's HTTP/MCP server (port 3001 by default) on the
phone. Set `WEKIT_BASE_URL` to wherever that is. How you make it reachable is
up to your network — this directory holds helpers for the two awkward cases.

## Pick a path

| Situation | What to do |
|---|---|
| Agent host and phone on the **same network** | Nothing here is needed. Set `WEKIT_BASE_URL=http://<phone-ip>:3001` and you are done. |
| **Different subnets**, and you control the router the phone is on | `router-dnat/` — recommended |
| Phone genuinely unreachable over the network | `usb-bridge/` — fallback, see the warning below |

Give the phone a DHCP reservation whichever path you take, so its address does
not move underneath you.

## Why USB is the fallback, not the default

`adb forward` looks like the obvious answer and it is a trap. On the reference
deployment the host's **adb server crashed and respawned by itself every 10-30
seconds**. `adb devices` reported `device` the whole time — the USB link was
fine. But forward rules live inside the adb server's memory, so every crash
silently destroyed the forward and killed the long poll that was running
through it.

Measured on one host, same phone, same day:

| Transport | Long-poll failures |
|---|---|
| USB `adb forward` | **~170 per hour** (a break every ~21s) |
| WiFi via router DNAT | **0** |

Because inbound is edge-triggered (see `../docs/architecture.md`), each of those
breaks is a window where arriving WeChat messages are lost for good. If you must
use USB, expect to babysit it.

## Security

WeKit's API is protected by a single bearer token sent over **plaintext HTTP**.
Anyone who can reach the port and knows the token has full read/send access to
the WeChat account.

- Use a long random `WEKIT_TOKEN`, never a dictionary word.
- Restrict who can reach the port. The DNAT script takes `WEKIT_ALLOW_SRC` to
  limit it to the agent host; the USB bridge binds `127.0.0.1` by default.
- Treat the phone's network as trusted, or put a tunnel (WireGuard, SSH) in
  front of it. Do not expose port 3001 to the internet.
