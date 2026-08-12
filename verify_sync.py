#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UniChat 读库同步一键验证脚本

用途:手机 USB 连接并授权 adb 后,运行本脚本即可自动:
  1. 安装最新 APK
  2. 启动 UniChat(触发自动读库同步)
  3. 读取 UniChat 数据库,检查微信/抖音同步状态与消息条数

用法(在项目目录):
    python verify_sync.py

依赖:已配置 adb(建议 D:\\mumaxxx\\ai\\work\\android-sdk\\platform-tools)
"""
import subprocess
import sys
import time
import os
import shutil
import sqlite3
import tempfile

PROJECT = os.path.dirname(os.path.abspath(__file__))
APK = os.path.join(PROJECT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")


def adb(*args):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    return r.stdout + r.stderr


def main():
    print("== UniChat 读库同步验证 ==")
    if not os.path.exists(APK):
        print(f"[错误] 找不到 APK: {APK}")
        sys.exit(1)

    # 1. 等待设备
    print("等待手机连接...")
    for _ in range(15):
        dev = adb("devices")
        if any(line.endswith("\tdevice") for line in dev.splitlines()):
            break
        time.sleep(3)
    else:
        print("[错误] 手机未连接。请检查 USB 线、端口,并在手机上允许 USB 调试。")
        sys.exit(1)
    print("手机已连接 ✓")

    # 2. 安装 APK
    print("安装 APK ...")
    out = adb("install", "-r", APK)
    if "Success" not in out:
        print(f"[错误] 安装失败:\n{out}")
        sys.exit(1)
    print("安装成功 ✓")

    # 3. 启动 UniChat(触发自动同步)
    print("启动 UniChat(自动同步)...")
    adb("shell", "am", "start", "-n", "com.unichat.app/.MainActivity")
    time.sleep(20)  # 等自动同步完成

    # 4. 检查 root
    root = adb("shell", "su", "-c", "id")
    print("root 状态:", "有 ✓" if "uid=0" in root else "无 ✗")
    if "uid=0" not in root:
        print("[提示] 读库同步需要 root。若手机弹出 su 授权窗口,请允许后重新运行。")

    # 5. 拉取 UniChat 数据库
    tmp = tempfile.mkdtemp(prefix="unichat_verify_")
    adb("shell", "su", "-c",
        "cp /data/data/com.unichat.app/databases/unichat.db /data/local/tmp/u.db; "
        "cp /data/data/com.unichat.app/databases/unichat.db-wal /data/local/tmp/u.db-wal; "
        "cp /data/data/com.unichat.app/databases/unichat.db-shm /data/local/tmp/u.db-shm; "
        "chmod 644 /data/local/tmp/u.db*")
    for f in ("u.db", "u.db-wal", "u.db-shm"):
        adb("pull", f"/data/local/tmp/{f}", os.path.join(tmp, f))

    # 6. 分析数据库
    db = os.path.join(tmp, "u.db")
    con = sqlite3.connect(db)
    cur = con.cursor()
    print("\n== 同步状态 ==")
    try:
        for row in cur.execute("SELECT platform, hookInstalled, lastSyncAt, msgCount FROM sync_stats"):
            import datetime
            t = datetime.datetime.fromtimestamp(row[2] / 1000).strftime("%H:%M:%S") if row[2] else "-"
            print(f"  平台: {row[0]}  Hook注入: {bool(row[1])}  最近同步: {t}  条数: {row[3]}")
    except sqlite3.Error as e:
        print("  sync_stats 读取失败:", e)
    for t in ("contacts", "messages"):
        try:
            n = cur.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
            print(f"  {t}: {n} 条")
        except sqlite3.Error as e:
            print(f"  {t} 读取失败:", e)
    con.close()

    print("\n== 结论 ==")
    print("若上面有微信相关条数>0,说明读库同步成功。")
    print("打开手机 UniChat 聊天页,顶部点「同步」可手动再次同步。")


if __name__ == "__main__":
    main()
