"""USB 传输(已退役): 把手机上的 WeKit 端口暴露给另一台主机.

⚠️ 已退役: 这条链路已被 `../router-dnat/` 的路由器 DNAT 取代, 在参考主机上已经
停用. 留着只是为了应付"手机确实没法通过网络访问"这种情况, 它不再是受支持的路径,
也不会再被验证.

⚠️ RETIRED: this transport has been superseded by the router DNAT path in
`../router-dnat/` and is disabled on the reference host. It is kept only for the
case where the phone genuinely cannot be reached over the network; it is no
longer a supported or exercised path.

⚠️ **这是备选方案, 不是推荐的传输方式.** 优先让 agent 走 WiFi 访问手机
(见 ../README.md). 在参考部署里, 这条 USB 链路实测每小时约 170 次长轮询失败,
差不多每 21 秒断一次, 根因是主机上的 adb server 每 10 到 30 秒就自己崩溃重启一次.
`adb forward` 规则活在 adb server 的内存里, 所以每崩一次 forward 就无声蒸发, 正在
进行的长轮询也跟着断. 手机的 USB 连接自始至终都没问题, `adb devices` 一直报
`device`.

只有在 agent 主机确实无法通过网络访问手机时才用它, 并且做好随时照看它的准备.

它做两件事:

1. 保持 `adb forward tcp:<port> tcp:<port>` 存活, 并且**只在它真的不见了的时候**
   才重建: 无条件重跑 `adb forward` 会替换掉规则并切断所有活动连接, 光这一点就
   足以把每一次长轮询都打断;
2. 在 LISTEN_HOST:LISTEN_PORT 上接受 TCP 连接并转发到被 forward 的那个端口, 这样
   跑在 VM/WSL 里的 agent(看不到宿主机 127.0.0.1 上的 adb forward)也能连上.

环境变量配置:

    WEKIT_ADB_PATH      adb 路径              (默认 "adb")
    WEKIT_ADB_SERIAL    设备序列号            (默认: 唯一那台连着的设备)
    WEKIT_PHONE_PORT    手机上的 WeKit 端口   (默认 3001)
    WEKIT_BRIDGE_HOST   监听地址              (默认 127.0.0.1)
    WEKIT_BRIDGE_PORT   监听端口              (默认 13001)

⚠️ WEKIT_BRIDGE_HOST 默认 127.0.0.1 是有意为之. 绑 0.0.0.0 等于把这个微信账号的
完整控制权暴露给整个网络, 唯一的防线是一个明文传输的 bearer token. 要放宽必须是
想清楚之后的决定.

========================== English original ==========================

USB transport (fallback): expose the phone's WeKit port to another host.

⚠️ **This is the fallback, not the recommended transport.** Prefer reaching the
phone over WiFi (see ../README.md). On the reference deployment this USB path
was measured at roughly 170 long-poll failures per hour — about one break every
21 seconds — because the host's adb server crashed and respawned on its own
every 10-30 seconds. `adb forward` rules live in the adb server's memory, so
each crash silently vaporised the forward and killed the in-flight long poll.
The phone's USB connection was never the problem; `adb devices` reported
`device` throughout.

Use this only when the agent host genuinely cannot reach the phone over the
network, and expect to babysit it.

It does two things:

1. keeps `adb forward tcp:<port> tcp:<port>` alive, rebuilding it **only when it
   is actually missing** — re-running `adb forward` unconditionally replaces the
   rule and severs every active connection, which by itself is enough to break
   every long poll;
2. accepts TCP on LISTEN_HOST:LISTEN_PORT and proxies to the forwarded port,
   so an agent in a VM/WSL (which cannot see the host's adb forward on
   127.0.0.1) can still reach it.

Configuration by environment variable:

    WEKIT_ADB_PATH      path to adb            (default: "adb")
    WEKIT_ADB_SERIAL    device serial          (default: the only attached device)
    WEKIT_PHONE_PORT    WeKit port on phone    (default: 3001)
    WEKIT_BRIDGE_HOST   listen address         (default: 127.0.0.1)
    WEKIT_BRIDGE_PORT   listen port            (default: 13001)

⚠️ WEKIT_BRIDGE_HOST defaults to 127.0.0.1 on purpose. Binding 0.0.0.0 exposes
full control of the WeChat account to everything on the network, guarded only by
a bearer token sent in cleartext. Widen it only deliberately.
"""

import contextlib
import os
import socket
import subprocess
import threading
import time

ADB = os.getenv("WEKIT_ADB_PATH", "adb")
SERIAL = os.getenv("WEKIT_ADB_SERIAL") or ""
PHONE_PORT = int(os.getenv("WEKIT_PHONE_PORT") or 3001)
LISTEN = (os.getenv("WEKIT_BRIDGE_HOST", "127.0.0.1"),
          int(os.getenv("WEKIT_BRIDGE_PORT") or 13001))
TARGET = ("127.0.0.1", PHONE_PORT)


def _adb(*args: str, timeout: int = 15):
    cmd = [ADB]
    if SERIAL:
        cmd += ["-s", SERIAL]
    return subprocess.run(cmd + list(args), timeout=timeout,
                          capture_output=True, text=True)


def ensure_forward() -> None:
    """只在 forward 不见了的时候才重建.

    绝对不要无条件重跑 `adb forward`: 它会替换掉规则并切断所有活动连接, 那样每一轮
    都会把长轮询打断一次.

    Rebuild the forward only when it is missing.

    Never re-run `adb forward` unconditionally: it replaces the rule and cuts
    every live connection, which would break the long poll on every pass.
    """
    spec = f"tcp:{PHONE_PORT}"
    while True:
        try:
            r = _adb("forward", "--list")
            alive = r.returncode == 0 and spec in (r.stdout or "")
            if not alive:
                _adb("forward", spec, spec)
        except Exception:
            pass
        time.sleep(20)


def pipe(a: socket.socket, b: socket.socket) -> None:
    try:
        while True:
            data = a.recv(65536)
            if not data:
                break
            b.sendall(data)
    except OSError:
        pass
    finally:
        for s in (a, b):
            with contextlib.suppress(OSError):
                s.close()


def main() -> None:
    threading.Thread(target=ensure_forward, daemon=True).start()
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(LISTEN)
    srv.listen(64)
    print(f"bridge listening on {LISTEN[0]}:{LISTEN[1]} -> {TARGET[0]}:{TARGET[1]}",
          flush=True)
    while True:
        try:
            client, _ = srv.accept()
        except OSError:
            continue
        try:
            upstream = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            upstream.settimeout(10)
            upstream.connect(TARGET)
            upstream.settimeout(None)
        except OSError:
            with contextlib.suppress(OSError):
                client.close()
            continue
        threading.Thread(target=pipe, args=(client, upstream), daemon=True).start()
        threading.Thread(target=pipe, args=(upstream, client), daemon=True).start()


if __name__ == "__main__":
    main()
