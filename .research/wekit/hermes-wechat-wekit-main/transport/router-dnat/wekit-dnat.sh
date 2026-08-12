#!/bin/sh
# 让另一个网段上的主机也能访问手机上的 WeKit API: 在手机所连的那台路由器上做 DNAT.
#
# 当 agent 主机和手机不在同一个网络时才用它(比如一个光猫下面并联了两台路由器).
# 如果两者本来就在同一网段, 根本不需要这个脚本, 把 WEKIT_BASE_URL 直接指向手机即可.
#
#   agent 主机 ---> ROUTER_ADDR:PORT ---(DNAT)---> phone_ip:PORT
#
# 为什么不用 USB/adb: 在参考部署里, `adb forward` 大约每 21 秒就断一次, 因为主机上
# 的 adb server 会不停地自己崩溃再重启, 把 forward 一起带走. 换到这条链路之后, 长
# 轮询失败从每小时约 170 次降到 0.
#
# 手机地址每次运行都从 DHCP 租约文件重新解析, 所以租约变了会自愈. 规则都带一个注释
# 标记, 删除时只删带标记的那些, 因此不会动到别的防火墙规则(包括代理/VPN 的规则).
#
# 在 OpenWrt 系固件上验证过(busybox sh + iptables). 放进开机钩子并定期执行, 例如:
#   /etc/rc.local:  (sleep 30; sh /etc/wekit-dnat.sh) &
#   cron:           */2 * * * * sh /etc/wekit-dnat.sh >/dev/null 2>&1
#
# ========================= English original =========================
#
# Reach the phone's WeKit API from a host on a different subnet, by DNAT on the
# router the phone is connected to.
#
# Use this when the agent host and the phone are on different networks (for
# example two routers hanging off one modem). If they share a subnet you do not
# need this at all — just point WEKIT_BASE_URL straight at the phone.
#
#   agent host ---> ROUTER_ADDR:PORT ---(DNAT)---> phone_ip:PORT
#
# Why not USB/adb: on the reference deployment `adb forward` broke roughly every
# 21 seconds because the host's adb server kept crashing and respawning, taking
# the forward with it. Moving to this path took long-poll failures from ~170/hour
# to zero.
#
# The phone's address is resolved from the DHCP lease file each run, so a lease
# change repairs itself. Rules are tagged with a comment and only tagged rules
# are ever removed, so this will not disturb other firewall rules (proxy/VPN
# rules included).
#
# Tested on OpenWrt-family firmware (busybox sh, iptables). Run from a boot hook
# and periodically, e.g.:
#   /etc/rc.local:  (sleep 30; sh /etc/wekit-dnat.sh) &
#   cron:           */2 * * * * sh /etc/wekit-dnat.sh >/dev/null 2>&1
#
# ============================ configuration ============================
# 必填. agent 主机将要连接的本路由器地址(通常是它在共同上游网段上的 WAN 地址).
# 故意不给默认值: 一个看起来合理但填错的地址, 会把 DNAT 打到别人的主机上.
#
# Required. Address on THIS router that the agent host will connect to (usually
# its WAN address on the shared upstream segment). No default on purpose: a
# plausible-looking wrong address would DNAT to somebody else's host.
ROUTER_ADDR="${WEKIT_ROUTER_WAN:-}"

# 必填. 手机在 DHCP 租约文件里的主机名, 要一字不差(用 cat /var/dhcp.leases 查).
#
# Required. The phone's hostname exactly as it appears in the DHCP lease file
# (check with:  cat /var/dhcp.leases  ).
PHONE_HOSTNAME="${WEKIT_PHONE_HOSTNAME:-}"

# WeKit 在手机上监听的端口 / WeKit's port on the phone.
PORT="${WEKIT_PHONE_PORT:-3001}"

# 强烈建议设置: 把能通过这条规则访问手机的来源, 限制到 agent 主机的地址. 留空的话,
# 任何能路由到 ROUTER_ADDR 的主机, 都能拿到这个微信账号的完整读取和发送权限, 唯一
# 的防线是一个明文传输的 bearer token.
#
# STRONGLY RECOMMENDED: restrict who may reach the phone through this rule to
# the agent host's address. Left empty, ANY host that can route to ROUTER_ADDR
# gets full read/send control of the WeChat account, protected only by a bearer
# token that travels in cleartext.
ALLOW_SRC="${WEKIT_ALLOW_SRC:-}"

LEASES="${WEKIT_DHCP_LEASES:-/var/dhcp.leases}"
TAG=wekit-dnat
# =======================================================================

if [ -z "$ROUTER_ADDR" ] || [ -z "$PHONE_HOSTNAME" ]; then
    echo "wekit-dnat: set WEKIT_ROUTER_WAN and WEKIT_PHONE_HOSTNAME (or edit the" >&2
    echo "            configuration block at the top of this script)." >&2
    exit 2
fi

PHONE=$(awk -v n="$PHONE_HOSTNAME" '$4==n{print $3}' "$LEASES" 2>/dev/null | head -1)
if [ -z "$PHONE" ]; then
    logger -t "$TAG" "no DHCP lease for '$PHONE_HOSTNAME' in $LEASES; leaving rules unchanged"
    exit 0
fi

# 已经指向正确地址就什么都不做. 无谓地重建规则, 会把 agent 正在跑的那条长轮询打断.
#
# Already pointing at the right address: do nothing. Rebuilding rules needlessly
# would sever the agent's in-flight long poll.
if iptables -t nat -S PREROUTING 2>/dev/null |
        grep -q -- "--comment $TAG .*--to-destination $PHONE:$PORT"; then
    exit 0
fi

# 只删我们自己之前下的规则(从大序号往小删, 序号才不会失效).
# Remove only our own previous rules (highest index first so indices stay valid).
for n in $(iptables -t nat -L PREROUTING --line-numbers -n 2>/dev/null |
           grep "$TAG" | awk '{print $1}' | sort -rn); do
    iptables -t nat -D PREROUTING "$n" 2>/dev/null
done
for n in $(iptables -t nat -L POSTROUTING --line-numbers -n 2>/dev/null |
           grep "$TAG" | awk '{print $1}' | sort -rn); do
    iptables -t nat -D POSTROUTING "$n" 2>/dev/null
done
for n in $(iptables -L FORWARD --line-numbers -n 2>/dev/null |
           grep "$TAG" | awk '{print $1}' | sort -rn); do
    iptables -D FORWARD "$n" 2>/dev/null
done

if [ -n "$ALLOW_SRC" ]; then
    SRC="-s $ALLOW_SRC"
else
    SRC=""
    logger -t "$TAG" "WARNING: WEKIT_ALLOW_SRC unset - the phone's WeKit API is reachable by any host that can route to $ROUTER_ADDR"
fi

# $SRC 是一对故意可留空的参数, 所以这里要关掉 SC2086
# shellcheck disable=SC2086  # $SRC is an intentional optional argument pair
iptables -t nat -I PREROUTING 1 $SRC -d "$ROUTER_ADDR" -p tcp --dport "$PORT" \
    -m comment --comment "$TAG" -j DNAT --to-destination "$PHONE:$PORT"
# 同上 / same reason as above
# shellcheck disable=SC2086
iptables -I FORWARD 1 $SRC -d "$PHONE" -p tcp --dport "$PORT" \
    -m comment --comment "$TAG" -j ACCEPT
# 让回包经由路由器返回, 而不是直接发给一个手机根本没有路由可达的源地址.
#
# Make replies come back through the router rather than going directly to a
# source the phone has no route to.
iptables -t nat -I POSTROUTING 1 -d "$PHONE" -p tcp --dport "$PORT" \
    -m comment --comment "$TAG" -j MASQUERADE

logger -t "$TAG" "DNAT $ROUTER_ADDR:$PORT -> $PHONE:$PORT installed${ALLOW_SRC:+ (source restricted to $ALLOW_SRC)}"
