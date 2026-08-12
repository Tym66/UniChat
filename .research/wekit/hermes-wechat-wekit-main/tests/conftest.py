"""把 Hermes 运行时打桩, 让 adapter 可以脱离 Hermes 单独做单元测试.

adapter 会从 Hermes agent 里 import `gateway.platforms.base` 和 `gateway.config`.
与其要求 CI 装一整套 Hermes, 不如在 import adapter 之前先注册几个最小替身. 这些替身
只要能让 import 通过、让类继承关系对得上就够了, 这里没有任何测试会真的去跑 Hermes 的行为.

Stub out the Hermes runtime so the adapter can be unit-tested standalone.

The adapter imports `gateway.platforms.base` and `gateway.config` from the
Hermes agent. Rather than requiring a full Hermes install in CI, we register
minimal stand-ins before the adapter is imported. They only need to satisfy the
import and the class hierarchy — no test here exercises Hermes behaviour.
"""

import sys
import types
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


class _Platform(str, Enum):
    WECHAT_WEKIT = "wechat-wekit"


@dataclass
class _SendResult:
    success: bool
    message_id: str = ""
    error: str = ""


class _MessageType(str, Enum):
    # 对应 gateway.platforms.base.MessageType, 要跟 Hermes 那边保持同步.
    # Mirrors gateway.platforms.base.MessageType — keep in step with Hermes.
    TEXT = "text"
    LOCATION = "location"
    PHOTO = "photo"
    VIDEO = "video"
    AUDIO = "audio"
    VOICE = "voice"
    DOCUMENT = "document"
    STICKER = "sticker"
    COMMAND = "command"


@dataclass
class _MessageEvent:
    text: str = ""
    message_type: "_MessageType" = _MessageType.TEXT
    source: object = None
    message_id: str = ""
    timestamp: object = None


@dataclass
class _MessageSource:
    chat_id: str = ""
    chat_name: str = ""
    chat_type: str = "dm"
    user_id: str = ""
    user_name: str = ""
    platform: object = None


class _BasePlatformAdapter:
    def __init__(self, config=None, platform=None, **kwargs):
        self.config = config
        self.platform = platform

    def build_source(self, **kwargs):
        return _MessageSource(**kwargs)

    async def handle_message(self, event):
        return None


def _install():
    gateway = types.ModuleType("gateway")
    platforms = types.ModuleType("gateway.platforms")
    base = types.ModuleType("gateway.platforms.base")
    config = types.ModuleType("gateway.config")

    base.BasePlatformAdapter = _BasePlatformAdapter
    base.SendResult = _SendResult
    base.MessageEvent = _MessageEvent
    base.MessageType = _MessageType
    base.MessageSource = _MessageSource
    config.Platform = _Platform

    gateway.platforms = platforms
    platforms.base = base
    sys.modules.setdefault("gateway", gateway)
    sys.modules.setdefault("gateway.platforms", platforms)
    sys.modules.setdefault("gateway.platforms.base", base)
    sys.modules.setdefault("gateway.config", config)


_install()
