"""WeKit 动作封装, 以及构建在其上的 Hermes agent 工具.

``adapter.py`` 里的平台适配器管的是消息流本身: 轮询收信, 渲染, 回复. 本模块管
的是一次对话可能用得上的其余所有 WeKit HTTP API 能力: 拉聊天记录, 重新拉取微信
没有下载过的图片, 发原生语音气泡, 管群成员, 发朋友圈, 读联系人标签.

这里有两层, 是刻意分开的:

* :class:`WeKitActions` 是对 WeKit REST 端点 (``http://<phone>:3001/api/...``)
  的一层薄异步封装, 不依赖 Hermes. 它不 import agent 运行时的任何东西, 所以能
  对着 mock 出来的 ``httpx`` client 做单元测试, 适配器自己也复用它 (比如把一个
  标签解析成白名单).

* 工具层是 JSON schema, handler 和 :func:`register_tools`, 把这些动作接进
  Hermes, 变成 ``hermes-wechat-wekit`` toolset 里 agent 可调用的工具. 这个名字
  是要紧的: 对插件平台, Hermes 按 ``hermes-{platform_key}`` 推导默认 toolset,
  所以拿平台名来命名, 才能一行配置都不用改就在微信会话里生效.

  注意它不是只在微信会话里生效. Hermes 把不认识的插件 toolset 一律当作处处默认
  开启, 所以 CLI 或 Telegram 会话同样拿得到这些工具, 除非运维用 ``hermes tools``
  另行关掉. 这通常正是你要的效果 (从 CLI 操作微信很有用), 但它意味着信任边界是
  下面那道写操作开关, 而不是你此刻所在的平台.

安全
----
发消息 (文字, 语音, 视频) 的风险并不比 agent 本来就会发的那条回复更高, 所以这
类工具始终可用. 会改动账号社交关系, 或者对别人可见的动作, 也就是通过好友申请,
加/踢/邀请群成员, 发朋友圈, 全部由 ``WEKIT_ENABLE_WRITE_ACTIONS`` 把关. 开关关
着时 (默认如此) 这些工具照样出现, 但会拒绝执行并说明怎么打开, 这样模型就没法悄
无声息地重塑这个账号. 用自动化操作个人微信号违反其服务条款, 请专门开小号用.

--- English original ---

WeKit action helpers and the Hermes agent tools built on top of them.

The platform adapter in ``adapter.py`` is about the message *stream* — poll for
inbound, render it, send a reply.  This module is about everything else WeKit's
HTTP API can do that a conversation might call for: pulling chat history,
re-fetching an image WeChat never downloaded, sending a native voice bubble,
managing group members, posting to Moments, and reading contact labels.

Two layers live here, deliberately kept apart:

* :class:`WeKitActions` — a thin, Hermes-free async wrapper over the WeKit REST
  endpoints (``http://<phone>:3001/api/...``).  It imports nothing from the
  agent runtime, so it is unit-testable against a mocked ``httpx`` client and is
  reused by the adapter itself (e.g. to resolve a label into an allow-list).

* The tool layer — JSON schemas, handlers, and :func:`register_tools` — wires
  those actions into Hermes as agent-callable tools in the ``hermes-wechat-wekit``
  toolset.  The name matters: for a plugin platform Hermes derives the default
  toolset as ``hermes-{platform_key}``, so naming it after the platform is what
  makes it reach WeChat sessions with no config edit.

  Note it does not *only* reach them.  Hermes treats an unrecognised plugin
  toolset as on-by-default everywhere, so a CLI or Telegram session gets these
  tools too until the operator says otherwise via ``hermes tools``.  That is
  often what you want — driving WeChat from the CLI is useful — but it means
  the trust boundary is the write gate below, not the platform you are on.

Safety
------
Sending a message (text, voice, video) is no riskier than the reply the agent
already sends, so those tools are always available.  Actions that change the
account's social graph or are visible to other people — accepting a friend,
adding/removing/inviting group members, posting to Moments — are gated behind
``WEKIT_ENABLE_WRITE_ACTIONS``.  With the gate off (the default) those tools
still appear, but refuse to act and say how to enable them, so the model cannot
silently reshape the account.  Automating a personal WeChat account violates its
terms; use a dedicated secondary account.
"""

from __future__ import annotations

import asyncio
import contextlib
import mimetypes
import os
import posixpath
import tempfile
from typing import Any
from urllib.parse import quote

import httpx


def _load_file(path: str, default_mime: str) -> tuple[str, str, bytes]:
    """把一个本地文件读成 (basename, mime, bytes).

    这是阻塞调用, 一律用 ``asyncio.to_thread`` 包起来, 别让文件 I/O 跑在收信轮
    询共用的那个事件循环上.

    Read a local file into (basename, mime, bytes).

    Blocking — call via ``asyncio.to_thread`` so file I/O never runs on the
    event loop the inbound poll shares.
    """
    if not os.path.isfile(path):
        raise WeKitActionError(f"file not found: {path}")
    name = os.path.basename(path)
    mime = mimetypes.guess_type(name)[0] or default_mime
    with open(path, "rb") as fh:
        return name, mime, fh.read()


def _safe_unlink(path: str) -> None:
    with contextlib.suppress(OSError):
        os.remove(path)

# tool_result / tool_error 来自 Hermes 运行时. 在运行时之外 import 本模块时
# (测试, 单独使用) 回退到行为兼容的本地替身, 这样没装 agent 也照样 import 得动.
#
# tool_result / tool_error live in the Hermes runtime.  Fall back to compatible
# local shims when imported outside it (tests, standalone use) so this module
# stays importable without the agent installed.
try:  # pragma: no cover - trivial import glue
    from tools.registry import tool_error, tool_result
except Exception:  # pragma: no cover
    import json as _json

    def tool_result(data: Any = None, **kwargs: Any) -> str:
        return _json.dumps(data if data is not None else kwargs, ensure_ascii=False)

    def tool_error(message: Any, **extra: Any) -> str:
        out = {"error": str(message)}
        out.update(extra)
        return _json.dumps(out, ensure_ascii=False)


# ── REST 封装 / the REST wrapper ──────────────────────────────────────────


class WeKitActionError(RuntimeError):
    """WeKit 的某个端点返回了错误, 或者根本连不上.

    A WeKit endpoint returned an error or unreachable response.
    """


class WeKitActions:
    """对 WeKit ``/api`` 这一整组接口的异步封装.

    传入一个现成的 ``httpx.AsyncClient`` 就能复用适配器的连接; 不传的话, 每次
    调用各自开一个短命 client, 用完即关 (一次性的工具 handler 用这个就够了).

    Async wrapper over the WeKit ``/api`` surface.

    Pass an existing ``httpx.AsyncClient`` to reuse the adapter's connection;
    omit it and each call opens and closes its own short-lived client (fine for
    the one-shot tool handlers).
    """

    def __init__(
        self,
        base_url: str,
        token: str,
        client: httpx.AsyncClient | None = None,
        *,
        timeout: float = 30.0,
    ) -> None:
        self.base_url = (base_url or "").rstrip("/")
        self.token = (token or "").strip()
        self._client = client
        self._timeout = timeout

    # -- 底层管道 / plumbing -----------------------------------------------

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.token}"}

    async def _request(
        self,
        method: str,
        path: str,
        *,
        params: dict | None = None,
        json_body: dict | None = None,
        files: list | None = None,
        data: dict | None = None,
        expect: str = "json",
    ) -> Any:
        url = f"{self.base_url}/api/{path.lstrip('/')}"

        async def _do(client: httpx.AsyncClient) -> httpx.Response:
            return await client.request(
                method,
                url,
                headers=self._headers(),
                params=params,
                json=json_body,
                files=files,
                data=data,
                # 借来的 client 带的是适配器的 timeout, 那个值由 long poll
                # 的窗口推导出来, 可能长达几分钟. 这里都是普通的短请求, 所以
                # 自己定一个上界, 不去继承那个给轮询用的.
                #
                # A borrowed client carries the adapter's timeout, which is
                # derived from the long-poll window and can be minutes. These
                # are ordinary short requests, so they get their own bound
                # rather than inheriting one meant for a poll.
                timeout=self._timeout,
            )

        try:
            if self._client is not None:
                resp = await _do(self._client)
            else:
                async with httpx.AsyncClient(timeout=self._timeout) as client:
                    resp = await _do(client)
        except httpx.HTTPError as exc:
            raise WeKitActionError(f"{method} {path} failed: {exc}") from exc

        if resp.status_code >= 400:
            body = resp.text[:300]
            raise WeKitActionError(f"{method} {path} -> {resp.status_code}: {body}")

        if expect == "raw":
            return resp.content
        text = resp.text.strip()
        if not text:
            return None
        try:
            parsed = resp.json()
        except ValueError:
            return text

        # 200 不等于"这事成了". WeKit 有一部分失败是拿 HTTP 200 配上
        # {"success": false, "error": ...} 回给你的, mp3→SILK 转换在缺 ffmpeg
        # 时就正是这么干的, 而调用方会接着去发一条语音消息, 指向一个根本没被写
        # 出来的文件. 所以一律以 body 为准, 免得每个 action 都得自己记着这件事.
        #
        # A 200 is not the same as "it worked". WeKit answers some failures with
        # HTTP 200 and {"success": false, "error": ...} — the mp3→SILK converter
        # does exactly that when ffmpeg is missing, and the caller would go on to
        # send a voice message pointing at a file that was never written. Treat
        # the body as authoritative, so no action has to remember to.
        if isinstance(parsed, dict) and (
            parsed.get("success") is False or parsed.get("error")
        ):
            detail = parsed.get("error") or parsed.get("message") or parsed
            raise WeKitActionError(f"{method} {path} refused: {detail}")
        return parsed

    async def _upload(self, local_path: str) -> str:
        """把本地文件推到手机上, 返回 WeKit 保存它时用的路径.

        Push a local file to the phone; return the path WeKit saved it under.
        """
        name, mime, payload = await asyncio.to_thread(
            _load_file, local_path, "application/octet-stream"
        )
        res = await self._request(
            "POST",
            "media/upload",
            files=[("file", (name, payload, mime))],
        )
        path = (res or {}).get("path") if isinstance(res, dict) else None
        if not path:
            raise WeKitActionError(f"media/upload returned no path: {res!r}")
        return path

    # -- 功能 1: 聊天记录 / feature 1: history (ChatLab) -------------------

    async def pull_history(self, conv_id: str, count: int = 50) -> list[dict]:
        """返回某个会话最近的至多 ``count`` 条消息, 最旧的在前.

        WeKit 的历史接口按页取, 并且会把群里的发送者解析成名字; 每条消息只给
        ``{sender, content, type}`` (没有时间戳, 也没有服务端 id), 所以这里一
        直往前翻页, 攒够为止, 再按阅读顺序把这一段交回去.

        Return up to ``count`` recent messages for a conversation, oldest first.

        WeKit's history endpoint is page-based and resolves group senders to
        names; it returns only ``{sender, content, type}`` per message (no
        timestamp or server id), so this pages back until it has enough and
        hands the slice back in reading order.
        """
        count = max(1, min(int(count), 1000))
        page_size = min(count, 100)
        collected: list[dict] = []
        previous: list | None = None
        page = 1
        while len(collected) < count:
            batch = await self._request(
                "GET",
                f"conversations/{conv_id}/history",
                params={"page-index": page, "page-size": page_size},
            )
            if not isinstance(batch, list) or not batch:
                break

            # 服务端把同一页又还回来一次就停: 端点无视 `page-index` 时就长这
            # 个样, 继续翻只会往历史里灌重复内容, 而模型会把它当成真发生过的
            # 对话来读. 这里刻意拿整页跟整页比, 而不是对消息去重: 一个人连说
            # 两次 "ok" 不是重复.
            #
            # Stop if the server handed back the same page again — that is what
            # an endpoint ignoring `page-index` looks like, and continuing would
            # pad the history with repetition the model reads as real.
            # Deliberately compared page-against-page rather than deduplicating
            # messages: a person saying "ok" twice is not a duplicate.
            if batch == previous:
                break
            previous = batch

            collected.extend(batch)
            if len(batch) < page_size:
                break
            page += 1
        collected = collected[:count]
        # 端点返回的是最新在前, 这里翻成时间正序
        collected.reverse()  # endpoint returns newest-first; make it chronological
        return collected

    # -- 功能 2: 从 CDN 重拉媒体 / feature 2: re-fetch media from CDN ------

    async def cache_image(self, msg_svr_id: int | str) -> str:
        """让微信把一张图片从 CDN 拉进它自己的缓存.

        补的是这个缺口: 收到的图片如果微信从没自动下载过, 本地就没有字节可取.
        返回缓存后的路径; 接着 GET ``messages/{id}/image`` 就能把它读出来.

        Force WeChat to pull an image from the CDN into its own cache.

        Fixes the gap where a received image WeChat never auto-downloaded has no
        local bytes to fetch.  Returns the cached path; follow with a GET of
        ``messages/{id}/image`` to read it.
        """
        res = await self._request("POST", f"messages/{msg_svr_id}/image/cache")
        if isinstance(res, dict):
            return res.get("path") or ""
        return str(res or "")

    async def cache_file(self, msg_svr_id: int | str, talker: str | None = None) -> str:
        params = {"talker": talker} if talker else None
        res = await self._request(
            "POST", f"messages/{msg_svr_id}/file/cache", params=params
        )
        if isinstance(res, dict):
            return res.get("path") or ""
        return str(res or "")

    # -- 功能 3: 原生语音气泡 / feature 3: native voice bubble -------------

    async def audio_duration(self, phone_path: str) -> int:
        res = await self._request(
            "POST", "utils/audio/duration", json_body={"path": phone_path}
        )
        if isinstance(res, dict):
            for key in ("durationMs", "duration", "value"):
                if key in res:
                    return int(res[key])
        try:
            return int(res)
        except (TypeError, ValueError):
            return 0

    async def send_voice(
        self,
        conv_id: str,
        mp3_path: str,
        *,
        duration_ms: int | None = None,
    ) -> dict:
        """把一个本地 mp3 发成微信的原生语音气泡.

        先上传 mp3, 在手机上转成 SILK (微信的语音格式), 读出时长, 再走语音那
        条路径发出去.

        Send a local mp3 as a native WeChat voice bubble.

        Uploads the mp3, converts it to SILK on the phone (WeChat's voice
        format), reads its duration, and sends it down the voice path.
        """
        phone_mp3 = await self._upload(mp3_path)
        silk_path = posixpath.splitext(phone_mp3)[0] + ".silk"
        await self._request(
            "POST",
            "utils/audio/mp3-to-silk",
            json_body={"srcPath": phone_mp3, "destPath": silk_path},
        )
        dur = duration_ms if duration_ms is not None else await self.audio_duration(phone_mp3)
        await self._request(
            "POST",
            "messages/voice",
            json_body={"convId": conv_id, "path": silk_path, "durationMs": int(dur)},
        )
        return {"ok": True, "durationMs": int(dur)}

    # -- 功能 4: 群 + 好友 / feature 4: groups + friends -------------------

    async def list_group_members(self, group_id: str) -> list[dict]:
        res = await self._request("GET", f"groups/{group_id}/members")
        return res if isinstance(res, list) else []

    async def group_member_op(
        self, group_id: str, op: str, member_wxids: list[str]
    ) -> dict:
        """op ∈ {add, delete, invite}; ``member_wxids`` 是一个或多个 wxId.

        op ∈ {add, delete, invite}; ``member_wxids`` is one or more wxIds.
        """
        if op not in ("add", "delete", "invite"):
            raise WeKitActionError(f"unknown group op: {op}")
        member_wxids = [w for w in member_wxids if w]
        if not member_wxids:
            raise WeKitActionError("no member wxIds given")
        body = (
            {"memberWxid": member_wxids[0]}
            if len(member_wxids) == 1
            else {"memberWxids": member_wxids}
        )
        await self._request("POST", f"groups/{group_id}/members/{op}", json_body=body)
        return {"ok": True, "op": op, "members": member_wxids}

    async def accept_friend(
        self, user_id: str, ticket: str, scene: int, privacy: int | None = None
    ) -> dict:
        body: dict[str, Any] = {"userId": user_id, "ticket": ticket, "scene": int(scene)}
        if privacy is not None:
            body["privacy"] = int(privacy)
        await self._request("POST", "contacts/verify", json_body=body)
        return {"ok": True, "userId": user_id}

    # -- 功能 5: 朋友圈 + 视频 / feature 5: Moments + video ----------------

    async def post_moment_text(self, content: str) -> dict:
        await self._request("POST", "moments/text", json_body={"content": content})
        return {"ok": True}

    async def post_moment_pics(self, content: str, pic_paths: list[str]) -> dict:
        files = []
        for p in pic_paths:
            name, mime, payload = await asyncio.to_thread(_load_file, p, "image/jpeg")
            files.append(("file", (name, payload, mime)))
        if not files:
            raise WeKitActionError("no images given")
        await self._request(
            "POST", "moments/pics", data={"content": content}, files=files
        )
        return {"ok": True, "images": len(files)}

    async def send_video(self, conv_id: str, video_path: str) -> dict:
        name, mime, payload = await asyncio.to_thread(_load_file, video_path, "video/mp4")
        await self._request(
            "POST",
            "messages/video",
            data={"convId": conv_id},
            files=[("file", (name, payload, mime))],
        )
        return {"ok": True}

    # -- 功能 6: 联系人标签 / feature 6: contact labels --------------------

    async def list_labels(self) -> list[dict]:
        res = await self._request("GET", "labels")
        return res if isinstance(res, list) else []

    async def contacts_by_label(self, label_id_or_name: str) -> list[str]:
        # 标签名是人在手机上随手打的自由文本: 中文, 空格, 偶尔还带斜杠. 不转
        # 义的话, 像 "work/home" 这样的名字会改变请求的路径结构, 而不是老老实
        # 实待在一个路径段里.
        #
        # Label names are free text a person typed on their phone — Chinese,
        # spaces, and occasionally a slash. Unescaped, a name like "work/home"
        # would change the request's path shape rather than be one segment.
        seg = quote(str(label_id_or_name), safe="")
        res = await self._request("GET", f"labels/{seg}/contacts")
        return [str(x) for x in res] if isinstance(res, list) else []

    async def set_contact_labels(self, wx_id: str, labels: list[str]) -> dict:
        """整组替换一个联系人的标签.

        微信只认已经存在的标签: 在 WeKit 内部, 一个解析不出 id 的名字会被跳过,
        只留一行日志, 端点照样返回 200, 于是一个不存在的名字看上去像是成功了,
        实际什么都没做. 所以这里先拿标签列表核对名字, 名字不存在就报错, 而不是
        静默地什么也不干. WeKit 没有暴露创建标签的接口, 新标签只能去微信里建.

        Replace a contact's label set.

        WeChat only accepts labels that already exist: inside WeKit, a name it
        cannot resolve to an id is skipped with a log line, and the endpoint
        still answers 200 — so an unknown name would look like it worked and
        silently do nothing. Names are therefore checked against the label list
        first, and an unknown one is an error rather than a no-op. WeKit exposes
        no way to create a label, so a new one has to be made in WeChat itself.
        """
        labels = list(labels)
        if labels:
            known = {str(lbl.get("labelName") or "") for lbl in await self.list_labels()}
            missing = [name for name in labels if name not in known]
            if missing:
                raise WeKitActionError(
                    f"no such WeChat label: {', '.join(missing)}. "
                    f"Existing labels: {', '.join(sorted(known)) or '(none)'}. "
                    "Labels can only be created in the WeChat app itself "
                    "(Me -> Contacts -> Tags); WeKit exposes no endpoint for it."
                )
        await self._request(
            "POST", f"contacts/{wx_id}/labels", json_body={"labels": labels}
        )
        return {"ok": True, "wxId": wx_id, "labels": labels}


# ── 环境与开关辅助 / env / gating helpers ─────────────────────────────────


def _actions_from_env() -> WeKitActions | None:
    base = os.getenv("WEKIT_BASE_URL")
    token = os.getenv("WEKIT_TOKEN")
    if not base or not token:
        return None
    return WeKitActions(base, token)


def _check_wekit_available() -> bool:
    return bool(os.getenv("WEKIT_BASE_URL") and os.getenv("WEKIT_TOKEN"))


def _write_actions_enabled() -> bool:
    return str(os.getenv("WEKIT_ENABLE_WRITE_ACTIONS") or "").lower() in (
        "1",
        "true",
        "yes",
    )


_WRITE_GATE_MSG = (
    "This action changes the account or is visible to others and is disabled by "
    "default. Set WEKIT_ENABLE_WRITE_ACTIONS=1 in the environment to allow it."
)


async def _edge_tts_to_mp3(text: str, voice: str, out_path: str) -> None:
    """用 edge-tts 把文本合成为 mp3 (可选依赖).

    Render text to an mp3 with edge-tts (optional dependency).
    """
    # 延迟导入, 这样没装 edge-tts 也照样能加载本模块
    import edge_tts  # imported lazily so the module loads without it

    communicate = edge_tts.Communicate(text, voice)
    await communicate.save(out_path)


# ── 工具 schema / tool schemas ────────────────────────────────────────────

_CONV = {"type": "string", "description": "WeChat conversation id: a wxid for a "
         "person, or a ...@chatroom id for a group."}

PULL_HISTORY_SCHEMA = {
    "name": "wechat_pull_history",
    "description": (
        "Read recent messages from a WeChat conversation (a person or a group). "
        "Returns messages oldest-first with the sender resolved to a name in "
        "groups. Read-only. Non-text messages appear as <type:...> placeholders."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "conv_id": _CONV,
            "count": {"type": "integer", "description": "How many recent messages "
                      "to fetch (default 50, max 1000)."},
        },
        "required": ["conv_id"],
    },
}

SEND_VOICE_SCHEMA = {
    "name": "wechat_send_voice",
    "description": (
        "Send a native WeChat voice bubble to a conversation. Provide `text` to "
        "synthesize speech (edge-tts) or `audio_path` to send an existing "
        "mp3/wav. Same risk as sending any message."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "conv_id": _CONV,
            "text": {"type": "string", "description": "Text to speak (synthesized to voice)."},
            "audio_path": {"type": "string", "description": "Local mp3/wav to send as voice."},
            "voice": {"type": "string", "description": "edge-tts voice (default Xiaoxiao)."},
        },
        "required": ["conv_id"],
    },
}

SEND_VIDEO_SCHEMA = {
    "name": "wechat_send_video",
    "description": "Send a local video file (mp4) as a native WeChat video message.",
    "parameters": {
        "type": "object",
        "properties": {
            "conv_id": _CONV,
            "video_path": {"type": "string", "description": "Path to a local video file."},
        },
        "required": ["conv_id", "video_path"],
    },
}

GROUP_MEMBERS_SCHEMA = {
    "name": "wechat_group_members",
    "description": (
        "List, add, remove, or invite members of a WeChat group. action=list is "
        "read-only; add/remove/invite change the group and are gated behind "
        "WEKIT_ENABLE_WRITE_ACTIONS."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "group_id": {"type": "string", "description": "Group id (...@chatroom)."},
            "action": {"type": "string", "enum": ["list", "add", "remove", "invite"]},
            "member_wxids": {
                "type": "array",
                "items": {"type": "string"},
                "description": "wxIds to add/remove/invite (not needed for list).",
            },
        },
        "required": ["group_id", "action"],
    },
}

ACCEPT_FRIEND_SCHEMA = {
    "name": "wechat_accept_friend",
    "description": (
        "Accept a pending friend request. Needs the userId, ticket, and scene "
        "from the request (surfaced on the incoming add-request message). Gated "
        "behind WEKIT_ENABLE_WRITE_ACTIONS."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "user_id": {"type": "string"},
            "ticket": {"type": "string"},
            "scene": {"type": "integer"},
            "privacy": {"type": "integer"},
        },
        "required": ["user_id", "ticket", "scene"],
    },
}

MOMENT_SCHEMA = {
    "name": "wechat_post_moment",
    "description": (
        "Post to the account's Moments (朋友圈) timeline — text, or text plus "
        "images. Public to the account's contacts. Gated behind "
        "WEKIT_ENABLE_WRITE_ACTIONS."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "content": {"type": "string", "description": "Text of the moment."},
            "image_paths": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Optional local image paths to attach.",
            },
        },
        "required": ["content"],
    },
}

LABELS_SCHEMA = {
    "name": "wechat_labels",
    "description": (
        "Manage WeChat contact labels (标签/groups). action=list lists labels; "
        "action=members lists the wxIds carrying a label (use this to drive the "
        "agent's allow-list); action=set replaces a contact's full label set "
        "(gated behind WEKIT_ENABLE_WRITE_ACTIONS). Labels must already exist — "
        "they can only be created in the WeChat app itself, so 'set' with an "
        "unknown name fails rather than creating it."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "action": {"type": "string", "enum": ["list", "members", "set"]},
            "label": {"type": "string", "description": "Label id or name (for members)."},
            "wx_id": {"type": "string", "description": "Contact wxId (for set)."},
            "labels": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Full label set to assign the contact (for set).",
            },
        },
        "required": ["action"],
    },
}


# ── 工具 handler / tool handlers ──────────────────────────────────────────


async def _handle_pull_history(args: dict, **_kw: Any) -> str:
    act = _actions_from_env()
    if act is None:
        return tool_error("WeKit not configured (WEKIT_BASE_URL / WEKIT_TOKEN).")
    conv_id = (args.get("conv_id") or "").strip()
    if not conv_id:
        return tool_error("conv_id is required.")
    try:
        msgs = await act.pull_history(conv_id, int(args.get("count") or 50))
    except Exception as exc:
        return tool_error(str(exc))
    return tool_result({"conv_id": conv_id, "count": len(msgs), "messages": msgs})


async def _handle_send_voice(args: dict, **_kw: Any) -> str:
    act = _actions_from_env()
    if act is None:
        return tool_error("WeKit not configured (WEKIT_BASE_URL / WEKIT_TOKEN).")
    conv_id = (args.get("conv_id") or "").strip()
    if not conv_id:
        return tool_error("conv_id is required.")
    audio_path = (args.get("audio_path") or "").strip()
    text = (args.get("text") or "").strip()
    tmp_created: str | None = None
    try:
        if not audio_path:
            if not text:
                return tool_error("Provide either text or audio_path.")
            voice = (args.get("voice") or "zh-CN-XiaoxiaoNeural").strip()
            # 每次调用都取一个唯一的文件名: 同时发出去的两条语音不能互相盖掉
            # 对方的音频, 而按文本内容派生文件名, 在同一句话发两遍时恰好就会
            # 盖掉.
            #
            # A unique name per call: two voice messages sent at once must not
            # write over each other's audio, and a name derived from the text
            # would do exactly that when the same line is sent twice.
            fd, tmp_created = await asyncio.to_thread(
                tempfile.mkstemp, prefix="wekit-tts-", suffix=".mp3"
            )
            os.close(fd)  # edge-tts 自己按路径写文件 / edge-tts writes the path itself
            try:
                await _edge_tts_to_mp3(text, voice, tmp_created)
            except ImportError:
                return tool_error(
                    "edge-tts is not installed; pass audio_path with a ready mp3 "
                    "instead, or `pip install edge-tts`."
                )
            audio_path = tmp_created
        res = await act.send_voice(conv_id, audio_path)
    except Exception as exc:
        return tool_error(str(exc))
    finally:
        if tmp_created:
            await asyncio.to_thread(_safe_unlink, tmp_created)
    return tool_result(res)


async def _handle_send_video(args: dict, **_kw: Any) -> str:
    act = _actions_from_env()
    if act is None:
        return tool_error("WeKit not configured (WEKIT_BASE_URL / WEKIT_TOKEN).")
    conv_id = (args.get("conv_id") or "").strip()
    video_path = (args.get("video_path") or "").strip()
    if not conv_id or not video_path:
        return tool_error("conv_id and video_path are required.")
    try:
        res = await act.send_video(conv_id, video_path)
    except Exception as exc:
        return tool_error(str(exc))
    return tool_result(res)


async def _handle_group_members(args: dict, **_kw: Any) -> str:
    act = _actions_from_env()
    if act is None:
        return tool_error("WeKit not configured (WEKIT_BASE_URL / WEKIT_TOKEN).")
    group_id = (args.get("group_id") or "").strip()
    action = (args.get("action") or "").strip()
    if not group_id or not action:
        return tool_error("group_id and action are required.")
    try:
        if action == "list":
            members = await act.list_group_members(group_id)
            return tool_result({"group_id": group_id, "count": len(members),
                                "members": members})
        if not _write_actions_enabled():
            return tool_error(_WRITE_GATE_MSG)
        op = {"add": "add", "remove": "delete", "invite": "invite"}.get(action)
        if not op:
            return tool_error(f"unknown action: {action}")
        member_wxids = args.get("member_wxids") or []
        res = await act.group_member_op(group_id, op, list(member_wxids))
    except Exception as exc:
        return tool_error(str(exc))
    return tool_result(res)


async def _handle_accept_friend(args: dict, **_kw: Any) -> str:
    act = _actions_from_env()
    if act is None:
        return tool_error("WeKit not configured (WEKIT_BASE_URL / WEKIT_TOKEN).")
    if not _write_actions_enabled():
        return tool_error(_WRITE_GATE_MSG)
    user_id = (args.get("user_id") or "").strip()
    ticket = (args.get("ticket") or "").strip()
    scene = args.get("scene")
    if not user_id or not ticket or scene is None:
        return tool_error("user_id, ticket, and scene are required.")
    try:
        res = await act.accept_friend(user_id, ticket, int(scene), args.get("privacy"))
    except Exception as exc:
        return tool_error(str(exc))
    return tool_result(res)


async def _handle_post_moment(args: dict, **_kw: Any) -> str:
    act = _actions_from_env()
    if act is None:
        return tool_error("WeKit not configured (WEKIT_BASE_URL / WEKIT_TOKEN).")
    if not _write_actions_enabled():
        return tool_error(_WRITE_GATE_MSG)
    content = args.get("content") or ""
    image_paths = args.get("image_paths") or []
    try:
        if image_paths:
            res = await act.post_moment_pics(content, list(image_paths))
        else:
            res = await act.post_moment_text(content)
    except Exception as exc:
        return tool_error(str(exc))
    return tool_result(res)


async def _handle_labels(args: dict, **_kw: Any) -> str:
    act = _actions_from_env()
    if act is None:
        return tool_error("WeKit not configured (WEKIT_BASE_URL / WEKIT_TOKEN).")
    action = (args.get("action") or "").strip()
    try:
        if action == "list":
            return tool_result({"labels": await act.list_labels()})
        if action == "members":
            label = (args.get("label") or "").strip()
            if not label:
                return tool_error("label is required for action=members.")
            wxids = await act.contacts_by_label(label)
            return tool_result({"label": label, "count": len(wxids), "wxids": wxids})
        if action == "set":
            if not _write_actions_enabled():
                return tool_error(_WRITE_GATE_MSG)
            wx_id = (args.get("wx_id") or "").strip()
            labels = args.get("labels")
            if not wx_id or labels is None:
                return tool_error("wx_id and labels are required for action=set.")
            res = await act.set_contact_labels(wx_id, list(labels))
            return tool_result(res)
        return tool_error(f"unknown action: {action}")
    except Exception as exc:
        return tool_error(str(exc))


# 名称, schema, handler, emoji / name, schema, handler, emoji
_TOOLS = (
    ("wechat_pull_history", PULL_HISTORY_SCHEMA, _handle_pull_history, "📜"),
    ("wechat_send_voice", SEND_VOICE_SCHEMA, _handle_send_voice, "🎙️"),
    ("wechat_send_video", SEND_VIDEO_SCHEMA, _handle_send_video, "🎬"),
    ("wechat_group_members", GROUP_MEMBERS_SCHEMA, _handle_group_members, "👥"),
    ("wechat_accept_friend", ACCEPT_FRIEND_SCHEMA, _handle_accept_friend, "🤝"),
    ("wechat_post_moment", MOMENT_SCHEMA, _handle_post_moment, "📸"),
    ("wechat_labels", LABELS_SCHEMA, _handle_labels, "🏷️"),
)

TOOLSET = "hermes-wechat-wekit"


def register_tools(ctx: Any) -> None:
    """注册这些微信动作工具. 由插件的 register() 调用.

    Register the WeChat action tools. Called from the plugin's register().
    """
    for name, schema, handler, emoji in _TOOLS:
        ctx.register_tool(
            name=name,
            toolset=TOOLSET,
            schema=schema,
            handler=handler,
            check_fn=_check_wekit_available,
            is_async=True,
            emoji=emoji,
        )
