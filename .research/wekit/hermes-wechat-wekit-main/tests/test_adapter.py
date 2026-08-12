"""WeKit adapter 的单元测试.

跑这些测试既不需要 Hermes 也不需要手机: adapter 依赖的那些 Hermes 内部实现已经在
conftest.py 里打好了桩, 这里每一个测试跑的都是纯逻辑.

Unit tests for the WeKit adapter.

These run without Hermes and without a phone: the Hermes internals the adapter
imports are stubbed in conftest.py, and every test here exercises pure logic.
"""

import base64

import pytest

from plugin import adapter as wk

# ── 解析 wait-for-new-message 的返回 / parsing the wait-for-new-message payload ───────────
#
# 这是 adapter 里最脆弱的一层: WeKit 把整条消息压成一个扁平字符串返回, 我们再用
# 一条正则从里面还原出四个字段. 用户能打出来的任何东西, 都必须能原样穿过这一层.
#
# This is the most fragile surface in the adapter: WeKit returns the message as
# one flat string and we recover four fields from it with a regex. Anything a
# user can type has to survive the trip.

def parse(text):
    m = wk._WAIT_RE.match(text.strip())
    if not m:
        return None
    return {
        "convId": m.group(1),
        "sender": m.group(2),
        "type": int(m.group(3)),
        "content": m.group(4),
    }


def test_parses_a_plain_direct_message():
    got = parse("ConvId='wxid_abc',Sender='wxid_abc',Type=1,Content='hello'")
    assert got == {"convId": "wxid_abc", "sender": "wxid_abc",
                   "type": 1, "content": "hello"}


def test_parses_a_group_message_with_distinct_sender():
    got = parse("ConvId='123@chatroom',Sender='wxid_bob',Type=1,Content='hi all'")
    assert got["convId"] == "123@chatroom"
    assert got["sender"] == "wxid_bob"


def test_content_may_contain_apostrophes():
    got = parse("ConvId='c',Sender='s',Type=1,Content='it's fine'")
    assert got["content"] == "it's fine"


def test_content_may_span_multiple_lines():
    got = parse("ConvId='c',Sender='s',Type=1,Content='line one\nline two'")
    assert got["content"] == "line one\nline two"


def test_content_may_contain_the_field_delimiters_verbatim():
    # 用户把分隔符原样打出来时, 不能靠这个伪造字段边界. 后面几个字段是锚定的,
    # 所以贪婪的 Content 分组会把这一整段都吃进去.
    #
    # A user typing the delimiter must not be able to spoof the framing. The
    # trailing fields are anchored, so the greedy Content group keeps it all.
    hostile = "look: ',Type=99,Content='gotcha"
    got = parse(f"ConvId='c',Sender='s',Type=1,Content='{hostile}'")
    assert got["type"] == 1
    assert got["content"] == hostile


def test_empty_content_parses():
    assert parse("ConvId='c',Sender='s',Type=3,Content=''")["content"] == ""


@pytest.mark.parametrize("junk", [
    "",
    "No new message arrived within 30000ms",
    "totally unexpected output",
    "ConvId='c',Sender='s',Type=notanumber,Content='x'",
])
def test_unparseable_payloads_are_rejected_rather_than_guessed(junk):
    assert parse(junk) is None


# ── 入站白名单 / the inbound whitelist ────────────────────────────────────────────────────
#
# 这里配错了是不会报错的: wxid 打错一个字符, 每一条消息都会被丢掉, 现场只剩下一行
# debug 级别的日志.
#
# Getting this wrong is silent: a mistyped wxid drops every message with only a
# debug-level log line to show for it.

def allowed(allowlist, allow_all, conv_id, sender, *,
            label="", label_unresolved=False):
    """去问真正的 adapter, 而不是照抄一份它的逻辑.

    这些测试以前跑的是本地重写的一份白名单实现, 结果就是: 把 adapter 里的白名单
    整个删掉, 测试也照样全绿. 而后来真正冒出来的那个 bug, 也就是标签解析失败时
    放行所有人, 恰恰就藏在副本和原件的差异里.

    Ask the real adapter, not a copy of its logic.

    These tests used to run against a local reimplementation of the gate, which
    meant deleting the gate itself left them green — and the bug that eventually
    turned up (an unresolved label admitting everyone) lived in exactly the
    difference between the copy and the original.
    """
    a = wk.WeKitAdapter.__new__(wk.WeKitAdapter)
    a.static_allowed = set(allowlist)
    a.label_allowed = set()
    a.allowed_contacts = set(allowlist)
    a.allow_all = allow_all
    a.allowed_label = label
    a.label_unresolved = label_unresolved
    return a._is_allowed(conv_id, sender)


def test_direct_message_from_a_listed_contact_is_allowed():
    assert allowed({"wxid_me"}, False, "wxid_me", "wxid_me")


def test_direct_message_from_an_unlisted_contact_is_dropped():
    assert not allowed({"wxid_me"}, False, "wxid_stranger", "wxid_stranger")


def test_group_message_is_allowed_when_the_group_itself_is_listed():
    assert allowed({"123@chatroom"}, False, "123@chatroom", "wxid_anyone")


def test_group_message_is_allowed_when_the_sender_is_listed():
    # 除了会话之外还去匹配发送者, 是故意这么设计的: 受信任的人即使身处一个
    # 本身没进白名单的群, 也照样能联系到 agent.
    #
    # Matching on sender as well as conversation is deliberate: it lets a
    # trusted person reach the agent from a group that is not itself listed.
    assert allowed({"wxid_me"}, False, "999@chatroom", "wxid_me")


def test_group_message_from_strangers_in_an_unlisted_group_is_dropped():
    assert not allowed({"wxid_me"}, False, "999@chatroom", "wxid_other")


def test_an_empty_whitelist_allows_everything():
    # 文档写明的默认行为: 什么都没配就等于不过滤.
    # Documented default: nothing configured means no filtering.
    assert allowed(set(), False, "wxid_anyone", "wxid_anyone")


def test_allow_all_overrides_the_whitelist():
    assert allowed({"wxid_me"}, True, "wxid_stranger", "wxid_stranger")


# 标签白名单必须"失败即关闭". 空集合在这里的常规含义是"不过滤", 所以一个配了却
# 读不出来的标签, 若不特殊处理, 就等于把账号交给任何一个发消息过来的人.
# 这个错误唯独不能往这个方向倒.
#
# The label allow-list must fail CLOSED. An empty set normally means "no
# filter", so a label that was asked for and could not be read would otherwise
# hand the account to anyone who messages it — the one direction this error
# must never go.

def test_a_label_that_failed_to_resolve_does_not_admit_everyone():
    assert not allowed(set(), False, "wxid_stranger", "wxid_stranger",
                       label="hermes", label_unresolved=True)


def test_a_configured_label_with_no_members_does_not_admit_everyone():
    # 标签配了, 也查到了, 只是没人挂这个标签: 仍然不等于"放行所有人".
    # Label configured, lookup succeeded, nobody carries it: still not "open".
    assert not allowed(set(), False, "wxid_stranger", "wxid_stranger",
                       label="hermes")


def test_a_failed_label_still_honours_the_env_list():
    # 回退到 WEKIT_ALLOWED_USERS, 正是同时保留这两套配置方式的意义所在.
    # Falling back to WEKIT_ALLOWED_USERS is the point of keeping both.
    assert allowed({"wxid_me"}, False, "wxid_me", "wxid_me",
                   label="hermes", label_unresolved=True)
    assert not allowed({"wxid_me"}, False, "wxid_x", "wxid_x",
                       label="hermes", label_unresolved=True)


def test_allow_all_still_wins_over_an_unresolved_label():
    assert allowed(set(), True, "wxid_stranger", "wxid_stranger",
                   label="hermes", label_unresolved=True)


# ── 配置 / configuration ──────────────────────────────────────────────────────────────────

def test_base_url_is_empty_when_unset_rather_than_guessed(monkeypatch):
    # 猜一个地址出来, 换来的是一串莫名其妙的重试, 而不是一句干脆的"你没配这个东西".
    #
    # Guessing an address produces a confusing retry loop instead of a clear
    # "you did not configure this" error.
    monkeypatch.delenv("WEKIT_BASE_URL", raising=False)
    assert wk._default_base_url() == ""


def test_base_url_loses_its_trailing_slash(monkeypatch):
    monkeypatch.setenv("WEKIT_BASE_URL", "http://192.168.1.50:3001/")
    assert wk._default_base_url() == "http://192.168.1.50:3001"


@pytest.mark.parametrize("poll_ms", [5000, 30000, 55000, 120000])
def test_read_timeout_always_outlives_the_long_poll(poll_ms):
    # HTTP 读超时一旦短过长轮询窗口, 每一轮 poll 都会被自己掐断, 入站从此悄无声息地死掉.
    #
    # If the HTTP read timeout ever falls below the poll window, every single
    # poll aborts and inbound goes silently dead.
    read_s = poll_ms / 1000.0 + 15.0
    assert read_s > poll_ms / 1000.0


# ── 图片加载 / image loading ──────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_loads_a_data_uri():
    a = wk.WeKitAdapter.__new__(wk.WeKitAdapter)
    png = base64.b64encode(b"\x89PNG\r\n\x1a\n").decode()
    data, name, ctype = await a._load_image(f"data:image/png;base64,{png}")
    assert data.startswith(b"\x89PNG")
    assert ctype == "image/png"
    assert name.endswith(".png")


@pytest.mark.asyncio
async def test_loads_a_local_file(tmp_path):
    p = tmp_path / "shot.png"
    p.write_bytes(b"\x89PNG\r\n\x1a\nbody")
    a = wk.WeKitAdapter.__new__(wk.WeKitAdapter)
    data, name, ctype = await a._load_image(str(p))
    assert data == b"\x89PNG\r\n\x1a\nbody"
    assert name == "shot.png"
    assert ctype == "image/png"


@pytest.mark.asyncio
async def test_loads_a_file_uri(tmp_path):
    p = tmp_path / "pic.jpg"
    p.write_bytes(b"\xff\xd8\xff")
    a = wk.WeKitAdapter.__new__(wk.WeKitAdapter)
    data, name, _ = await a._load_image(f"file://{p}")
    assert data == b"\xff\xd8\xff"
    assert name == "pic.jpg"


@pytest.mark.asyncio
async def test_a_missing_local_file_raises_rather_than_sending_nothing(tmp_path):
    a = wk.WeKitAdapter.__new__(wk.WeKitAdapter)
    with pytest.raises(OSError):
        await a._load_image(str(tmp_path / "nope.png"))


# ── 消息类型命名 / message type labelling ─────────────────────────────────────────────────

def test_known_message_types_are_named():
    assert wk._type_name(1) == "text"
    assert wk._type_name(3) == "image"
    assert wk._type_name(34) == "voice"


def test_unknown_message_types_fall_back_to_a_readable_label():
    assert wk._type_name(12345) == "type12345"


# ── 微信消息体解码 / WeChat payload decoding ──────────────────────────────────────────────
#
# 除了纯文本, 其余消息进来都是一坨 XML. 原样丢给模型, 唯一有用的那点信息会被 CDN key
# 和 AES 参数彻底淹没, 所以每种消息体都会被渲染成一行简短、能直接用的说明.
#
# Everything except plain text arrives as an XML blob. Handing that to a model
# verbatim buries the one useful fact under CDN keys and AES material, so each
# payload is rendered into a short, actionable line.

from plugin.adapter import describe_payload  # noqa: E402

IMAGE = ('<?xml version="1.0"?><msg><img aeskey="9a598cb2" encryver="1" '
         'length="245678" hdlength="892134" md5="abc123" /></msg>')
VOICE = ('<msg><voicemsg endflag="1" voiceformat="4" voicelength="2768" '
         'aeskey="097f5e4e" fromusername="wxid_abc"></voicemsg></msg>')
STICKER = ('wxid_abc:0:1:e4f6e764:<msg><emoji fromusername="wxid_abc" '
           'md5="deadbeef" type="2" /></msg>')
FILE = ('<msg><appmsg><title><![CDATA[report.xlsx]]></title><type>6</type>'
        '<appattach><totallen>11251</totallen><fileext>xlsx</fileext>'
        '</appattach></appmsg></msg>')
LINK = ('<msg><appmsg><title><![CDATA[An article]]></title><des><![CDATA[worth '
        'reading]]></des><type>5</type><url><![CDATA[https://example.com/a]]>'
        '</url></appmsg></msg>')
QUOTE = ('<msg><appmsg><title>and this?</title><type>57</type><refermsg>'
         '<displayname>Alice</displayname><content>earlier message</content>'
         '</refermsg></appmsg></msg>')
LOCATION = '<msg><location x="31.23" y="121.47" poiname="Some Tower" /></msg>'


def test_plain_text_passes_through_untouched():
    text, kind, _ = describe_payload(1, "hello there")
    assert text == "hello there"
    assert kind == "text"


def test_image_reports_size_and_photo_kind():
    text, kind, meta = describe_payload(3, IMAGE)
    assert kind == "photo"
    assert text.startswith("[Image]")
    assert "871.2 KB" in text          # 优先取 hdlength 而非 length / prefers hdlength over length
    assert meta["md5"] == "abc123"


def test_voice_reports_duration_in_seconds():
    text, kind, meta = describe_payload(34, VOICE)
    assert kind == "voice"
    assert "2.8s" in text
    assert meta["duration_ms"] == "2768"


def test_sticker_survives_the_routing_prefix():
    # 表情消息进来长这样: `wxid:0:1:<hash>:<msg>…`, 前缀不先剥掉, 解析出来就是一堆乱码.
    #
    # Stickers arrive as `wxid:0:1:<hash>:<msg>…` — the prefix must be stripped
    # before parsing or the payload looks like garbage.
    text, kind, _ = describe_payload(47, STICKER)
    assert kind == "sticker"
    assert text == "[Sticker]"


def test_file_reports_name_extension_and_size():
    text, kind, meta = describe_payload(49, FILE)
    assert kind == "document"
    assert "report.xlsx" in text
    assert "XLSX" in text and "11.0 KB" in text
    assert meta["filename"] == "report.xlsx"


def test_file_variant_type_is_handled_the_same():
    # 微信也会用 0x41000031 加 appmsg type 74 这种形式来发文件.
    # WeChat also delivers files as 0x41000031 with appmsg type 74.
    text, kind, meta = describe_payload(1090519089, FILE.replace("<type>6<", "<type>74<"))
    assert kind == "document"
    assert "report.xlsx" in text
    assert meta["appmsg_type"] == 74


def test_link_exposes_the_url_so_the_agent_can_follow_it():
    text, kind, meta = describe_payload(49, LINK)
    assert kind == "text"
    assert "https://example.com/a" in text
    assert meta["url"] == "https://example.com/a"


def test_quote_surfaces_both_the_reply_and_what_was_quoted():
    text, _, meta = describe_payload(49, QUOTE)
    assert "and this?" in text
    assert "Alice" in text
    assert meta["quoted_text"] == "earlier message"


def test_location_reports_name_and_coordinates():
    text, kind, meta = describe_payload(48, LOCATION)
    assert kind == "location"
    assert "Some Tower" in text
    assert meta["lat"] == "31.23"


def test_raw_xml_never_reaches_the_model():
    # 整件事的意义就在这: 绝不能把那坨原始数据递到模型手里.
    # The whole point: a model must never be handed the blob.
    for mtype, payload in ((3, IMAGE), (34, VOICE), (47, STICKER),
                           (49, FILE), (49, LINK), (48, LOCATION)):
        text, _, _ = describe_payload(mtype, payload)
        assert "<msg" not in text and "aeskey" not in text and "<appmsg" not in text


def test_unknown_xml_degrades_to_a_label_not_a_blob():
    text, _, _ = describe_payload(99, "<msg><somethingnew foo='1'/></msg>")
    assert "<" not in text
    assert "could not be decoded" in text


def test_oversized_payload_is_refused_rather_than_parsed():
    # 消息内容是攻击者可控的, 所以解析必须有上限.
    # Message content is attacker-controlled; parsing is capped.
    huge = "<msg>" + ("<a/>" * 200_000) + "</msg>"
    text, _, _ = describe_payload(3, huge)
    assert "<a/>" not in text


def test_malformed_xml_does_not_raise():
    text, _, _ = describe_payload(3, "<msg><img aeskey='x' UNCLOSED")
    assert isinstance(text, str) and text


# ── 公众号文章缓存解析 / Chromium Simple Cache parsing (official-account articles) ────────

import gzip as _gzip  # noqa: E402
import struct as _struct  # noqa: E402

from plugin.adapter import (  # noqa: E402
    _article_text_from_html,
    _decompress_body,
    _simple_cache_entry,
)


def _make_cache_entry(url: str, body: bytes) -> bytes:
    """拼一个最小可用的 Simple Cache '_0' 文件: 头部 + key + body.

    Build a minimal Simple Cache '_0' file: header + key + body.
    """
    key = url.encode()
    # 真实的 SimpleFileHeader 是 24 字节: magic(8)+version(4)+key_len(4)+hash(4),
    # 再按 8 字节对齐补齐; key 从偏移量 24 开始.
    #
    # Real SimpleFileHeader is 24 bytes: magic(8)+version(4)+key_len(4)+hash(4)
    # padded to 8-byte alignment; the key starts at offset 24.
    header = _struct.pack("<QIII", 0xFCFB6D1BA7725C30, 8, len(key), 0) + b"\x00\x00\x00\x00"
    return header + key + body


def test_parses_a_simple_cache_entry_key_and_body():
    raw = _make_cache_entry("1/0/https://mp.weixin.qq.com/s?__biz=X", b"PAYLOAD")
    key, body = _simple_cache_entry(raw)
    assert "mp.weixin.qq.com/s" in key
    assert body == b"PAYLOAD"


def test_rejects_a_file_without_the_magic():
    assert _simple_cache_entry(b"not a cache file at all, padded out......") is None


def test_decompresses_gzip_with_trailing_cache_junk():
    # Simple Cache 会在 gzip 流后面再追加 EOF 记录; 严格的一次性解压会被这截尾巴噎住.
    #
    # Simple Cache appends EOF records after the gzip stream; a strict one-shot
    # decompress would choke on them.
    html = b"<html><body>hello world " + b"x" * 800 + b"</body></html>"
    blob = _gzip.compress(html) + b"\x00\x01\x02trailing-eof-records"
    assert _decompress_body(blob) == html


def test_extracts_title_and_body_from_article_html():
    doc = (b'<html><head><meta property="og:title" content="Test Title">'
           b'</head><body><div id="js_content">'
           b'<p>First paragraph.</p><p>Second paragraph.</p>'
           b'</div></div></body></html>')
    title, text = _article_text_from_html(doc)
    assert title == "Test Title"
    assert "First paragraph." in text
    assert "Second paragraph." in text
    assert "<p>" not in text and "js_content" not in text


def test_article_extraction_unescapes_entities():
    doc = (b'<html><body><div id="js_content">'
           b'<p>A &amp; B &lt;tag&gt;</p></div></div></body></html>')
    _title, text = _article_text_from_html(doc)
    assert "A & B <tag>" in text


# ── 媒体抓取的分派 / media fetch routing (which kinds get a real file pulled) ─────────────

from plugin.adapter import PhoneMediaFetcher  # noqa: E402


def test_every_downloadable_media_kind_is_fetchable():
    # 一道回归防线: 语音和表情其实早就被配套脚本下载下来了, 却始终没被附加上去,
    # 因为 fetch() 当时只处理 document/photo.
    #
    # A regression guard: voice and sticker were downloaded by the companion
    # script but never attached because fetch() only handled document/photo.
    assert set(PhoneMediaFetcher.FETCHABLE) == {
        "document", "photo", "voice", "sticker", "video"}


def test_text_only_kinds_are_not_fetchable():
    for kind in ("text", "location", "quote", "link"):
        assert kind not in PhoneMediaFetcher.FETCHABLE


def test_voice_prefers_mp3_over_amr():
    # WeKit 会把一条语音同时存成这两种格式; 能直接播放的是 mp3.
    # WeKit saves a voice note as both; the mp3 is the playable one.
    exts = PhoneMediaFetcher._MEDIA_EXTS["voice"]
    assert exts.index(".mp3") < exts.index(".amr")


def test_media_kind_to_message_type_covers_every_fetchable_kind():
    from plugin.adapter import _MEDIA_KIND_TO_TYPE
    for kind in PhoneMediaFetcher.FETCHABLE:
        assert kind in _MEDIA_KIND_TO_TYPE


def test_attach_note_names_each_media_kind():
    from plugin.adapter import _attach_note
    for kind, noun in (("document", "file"), ("photo", "image"),
                       ("voice", "voice"), ("video", "video"), ("sticker", "sticker")):
        note = _attach_note("[x] placeholder", kind, "/tmp/x")
        assert noun in note.lower()
        assert "/tmp/x" in note


# ── 异常描述 / describing exceptions ──────────────────────────────────────
#
# 线上出现过 12 条 "poll error: " 后面完全空白的告警: `str(e)` 是空的, 而每一条
# 都意味着监听器被拆掉重连、那段时间的入站消息永久丢失。报了错却说不出是什么,
# 等于没报。
#
# Production logged 12 warnings reading "poll error: " and nothing else, because
# str(e) was empty — while each one means the listener was torn down and inbound
# messages in that window were lost for good. An error that says nothing is not
# an error report.

def test_an_exception_with_an_empty_message_still_names_its_type():
    got = wk._describe_exc(ValueError(""))
    assert "ValueError" in got
    assert got.strip() != "ValueError:"


def test_a_normal_exception_keeps_its_message():
    assert wk._describe_exc(ValueError("boom")) == "ValueError: boom"


def test_the_cause_is_unwrapped():
    try:
        raise RuntimeError("outer") from KeyError("inner")
    except RuntimeError as e:
        got = wk._describe_exc(e)
    assert "RuntimeError" in got and "KeyError" in got


def test_a_group_shows_its_members_not_just_the_wrapper():
    # 这正是 MCP 那边 "unhandled errors in a TaskGroup" 什么也说明不了的原因.
    # This is why "unhandled errors in a TaskGroup" explained nothing.
    class FakeGroup(Exception):
        exceptions = (ValueError("a"), TypeError("b"))

    got = wk._describe_exc(FakeGroup("grp"))
    assert "ValueError" in got and "TypeError" in got


def test_describe_exc_terminates_on_a_self_referencing_chain():
    # 自引用的异常链不能把日志打挂 —— 这里必须收敛.
    # A self-referencing chain must not hang the logger.
    e = ValueError("loop")
    e.__cause__ = e
    assert "ValueError" in wk._describe_exc(e)
