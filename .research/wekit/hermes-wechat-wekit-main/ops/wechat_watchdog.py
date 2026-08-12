"""让手机上的微信(以及跟着它跑的 WeKit API 服务)保持存活.

健康检查走 **HTTP**, 打的就是 agent 自己在用的那个 WeKit 接口, 所以系统正常时
一次 adb 都不会调. 只有 HTTP 探测连续失败之后才会碰 adb, 而且也只是看一眼微信
在不在跑, 不在就把它拉起来.

这里固化了两条拿代价换来的规矩:

* **绝不 force-stop 微信.** 那会把 Xposed/WeKit 的注入一起掀掉, 恢复得重启手机.
  要重新拉起就用 `monkey`.
* **不要用 adb 轮询.** 早先的版本每 60 秒跑一次 `adb shell pidof`, 在参考机器上
  这点动静就足以扰乱本来就不稳的 adb server. 改成 HTTP 探测后, 正常路径上完全
  不碰 adb.

全部配置都走环境变量:

    WEKIT_BASE_URL     必填, 例如 http://192.168.1.50:3001
    WEKIT_TOKEN        必填, WeKit API 的 bearer token
    WEKIT_ADB_SERIAL   可选, adb 设备序列号; 不填则由 adb 挑那台唯一连着的设备
    WEKIT_ADB_PATH     可选, adb 可执行文件路径(默认 "adb")
    WEKIT_LOG_PATH     可选, 日志文件(默认 ./wechat_watchdog.log)
    WEKIT_PROBE_INTERVAL_S     可选, 默认 120
    WEKIT_FAILS_BEFORE_ADB     可选, 默认 3
    WEKIT_BOOT_GRACE_S         可选, 默认 60

只要有 Python 3 和 adb 就能跑: Windows(计划任务), Linux/macOS(systemd unit,
launchd, cron @reboot).

========================== English original ==========================

Keep WeChat (and therefore the WeKit API server) alive on the phone.

Health is checked over **HTTP** against the same WeKit endpoint the agent uses,
so a healthy system costs zero adb calls. adb is touched only after the HTTP
probe has failed repeatedly, and then only to see whether WeChat is running and
relaunch it if it is not.

Two hard-won rules encoded here:

* **Never force-stop WeChat.** It tears down the Xposed/WeKit injection, and
  recovering from that takes a phone reboot. Relaunch with `monkey` instead.
* **Do not poll over adb.** An earlier version ran `adb shell pidof` every 60s;
  on the reference host that was enough to disturb the adb server, which is
  itself unstable. HTTP probing avoids adb entirely on the happy path.

Configuration is entirely by environment variable:

    WEKIT_BASE_URL     required, e.g. http://192.168.1.50:3001
    WEKIT_TOKEN        required, the WeKit API bearer token
    WEKIT_ADB_SERIAL   optional, adb device serial; if unset, adb picks the
                       only attached device
    WEKIT_ADB_PATH     optional, path to the adb binary (default: "adb")
    WEKIT_LOG_PATH     optional, log file (default: ./wechat_watchdog.log)
    WEKIT_PROBE_INTERVAL_S     optional, default 120
    WEKIT_FAILS_BEFORE_ADB     optional, default 3
    WEKIT_BOOT_GRACE_S         optional, default 60

Runs anywhere Python 3 and adb are available — Windows (Scheduled Task),
Linux/macOS (systemd unit, launchd, cron @reboot).
"""

import datetime
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE_URL = (os.getenv("WEKIT_BASE_URL") or "").rstrip("/")
TOKEN = os.getenv("WEKIT_TOKEN") or ""
ADB = os.getenv("WEKIT_ADB_PATH", "adb")
SERIAL = os.getenv("WEKIT_ADB_SERIAL") or ""
LOG = os.getenv("WEKIT_LOG_PATH", "wechat_watchdog.log")

WECHAT_PKG = "com.tencent.mm"


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.getenv(name) or default)
    except ValueError:
        return default


INTERVAL = _int_env("WEKIT_PROBE_INTERVAL_S", 120)
FAILS_BEFORE_ADB = _int_env("WEKIT_FAILS_BEFORE_ADB", 3)
BOOT_GRACE = _int_env("WEKIT_BOOT_GRACE_S", 60)


def log(msg: str) -> None:
    line = f"{datetime.datetime.now():%Y-%m-%d %H:%M:%S} {msg}"
    print(line, flush=True)
    try:
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except OSError:
        pass


def api_ok() -> bool:
    """HTTP 健康探测. 刻意不碰 adb.

    HTTP health probe. Deliberately does not touch adb.
    """
    req = urllib.request.Request(
        f"{BASE_URL}/api/self/info", headers={"Authorization": f"Bearer {TOKEN}"}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status == 200
    except urllib.error.HTTPError as e:
        # 401 说明服务器是通的, 只是令牌不对; 重启微信永远修不了这个, 所以直接把话
        # 说明白, 而不是在这里空转重试.
        #
        # A 401 means we reached the server but the token is wrong — relaunching
        # WeChat will never fix that, so say so plainly instead of looping.
        if e.code == 401:
            log("API returned 401 — WEKIT_TOKEN does not match the token "
                "configured in WeKit. Fix the token; not a WeChat problem.")
        return False
    except Exception:
        return False


def adb_cmd(*args: str, timeout: int = 25):
    cmd = [ADB]
    if SERIAL:
        cmd += ["-s", SERIAL]
    cmd += list(args)
    try:
        return subprocess.run(cmd, timeout=timeout, capture_output=True, text=True)
    # adb 出问题绝不能把循环带走
    except Exception as e:  # adb failures must never kill the loop
        log(f"adb error {e!r} running {args}")

        class _Failed:
            returncode = -1
            stdout = ""
            stderr = str(e)

        return _Failed()


def revive() -> None:
    """只有在 HTTP 反复失败之后才会被调用.

    Called only after repeated HTTP failures.
    """
    pid = (adb_cmd("shell", "pidof", WECHAT_PKG).stdout or "").strip()
    if pid:
        log(f"API down but WeChat is running (pid={pid}) — leaving it alone. "
            "Check the network path, or whether WeKit's API server is enabled.")
        return
    # monkey 走的是正常的 launcher intent. 绝对不要先 force-stop: 那会杀掉 Xposed
    # 注入, 只能重启手机才能恢复.
    #
    # monkey launches via the normal launcher intent. Never force-stop first:
    # that would kill the Xposed injection and require a reboot to restore.
    r = adb_cmd("shell", "monkey", "-p", WECHAT_PKG,
                "-c", "android.intent.category.LAUNCHER", "1")
    log(f"API down and WeChat not running -> relaunched (rc={r.returncode}). "
        "The WeKit server needs a DexKit scan on first start; allow a few minutes.")


def main() -> int:
    if not BASE_URL or not TOKEN:
        print("WEKIT_BASE_URL and WEKIT_TOKEN must be set", file=sys.stderr)
        return 2

    log(f"watchdog started (probe {BASE_URL} every {INTERVAL}s)")
    time.sleep(BOOT_GRACE)
    fails = 0
    while True:
        try:
            if api_ok():
                if fails:
                    log(f"API recovered after {fails} consecutive failures")
                fails = 0
            else:
                fails += 1
                log(f"API probe failed ({fails}/{FAILS_BEFORE_ADB})")
                if fails >= FAILS_BEFORE_ADB:
                    revive()
                    fails = 0
        # 看门狗自己绝不能死
        except Exception as e:  # the watchdog must never die
            log(f"loop error {e!r}")
        time.sleep(INTERVAL)


if __name__ == "__main__":
    raise SystemExit(main())
