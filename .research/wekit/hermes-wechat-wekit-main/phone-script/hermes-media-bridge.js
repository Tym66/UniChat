/*
 * hermes-media-bridge.js: WeKit 用户脚本
 *
 * 这个脚本为什么存在
 * ------------------
 * WeKit 能下载收到的附件, 但它所有下载接口都以 `msgSvrId` 为键, 而 WeKit 没有
 * 任何一个 API 会把这个 id 交出来: `wait-for-new-message` 只返回
 * ConvId/Sender/Type/Content, `get-chat-history` 只返回 sender/content. 于是
 * API 另一端的 agent 只能知道"有文件到了", 却永远取不到这个文件.
 *
 * 本脚本不动 WeKit 源码, 从微信进程内部把这个缺口补上: 挂上 WeKit 自己挂的那个
 * WCDB insert, 直接从 ContentValues 里读出 `msgSvrId`, 再调 WeKit 自己的本地 API
 * 下载附件. 文件落进 WeKit 的下载目录, agent 按文件名取走即可.
 *
 * 安装
 *   adb push hermes-media-bridge.js \
 *     /sdcard/Android/data/com.tencent.mm/WeKit/scripts_js/
 * 然后在 WeKit 里启用 "JavaScript scripting", 再重启微信.
 *
 * 线程
 * insert 钩子跑在微信的数据库线程上. 在这里做网络 IO 会把整个 app 拖住, 附件一大
 * 微信就会直接冻死, 所以钩子只记下一个 id 就返回. 真正的下载交给 onLoad() 里起的
 * 工作线程去做.
 *
 * ========================= English original =========================
 *
 * hermes-media-bridge.js — WeKit user script
 *
 * WHY THIS EXISTS
 * ---------------
 * WeKit can download a received attachment, but every one of its download
 * endpoints is keyed by `msgSvrId`, and no WeKit API ever hands one out:
 * `wait-for-new-message` returns ConvId/Sender/Type/Content and nothing else,
 * and `get-chat-history` returns only sender/content. So an agent on the other
 * end of the API can see that a file arrived but can never fetch it.
 *
 * This script closes that gap from inside WeChat, without patching WeKit.
 * It hooks the same WCDB insert WeKit itself hooks, reads `msgSvrId` straight
 * off the ContentValues, and asks WeKit's own local API to download the
 * attachment. The file lands in WeKit's download folder, where the agent can
 * collect it by name.
 *
 * INSTALL
 *   adb push hermes-media-bridge.js \
 *     /sdcard/Android/data/com.tencent.mm/WeKit/scripts_js/
 * then enable "JavaScript scripting" in WeKit and restart WeChat.
 *
 * THREADING
 * The insert hook runs on WeChat's database thread. Doing network I/O there
 * would stall the app — a large attachment would freeze WeChat outright — so
 * the hook only records an id and returns. A worker thread started from
 * onLoad() does the downloading.
 */

// 推到手机上之前必须改这里: 它要和 WeKit 的 "API + MCP server" 设置里填的令牌
// 完全一致. 这是一把真钥匙, 等于该微信账号的完整读取和发送权限, 所以仓库里只放
// 占位符, 绝不能带着真值提交.
//
// EDIT THIS before pushing the script to the phone: it must equal the token set
// in WeKit's "API + MCP server" settings. It is a real credential — full read
// and send access to the WeChat account — so it stays a placeholder in the repo
// and never gets committed with a live value.
var TOKEN = "PUT-YOUR-WEKIT-TOKEN-HERE";
// WeKit 就监听在本进程内
var API = "http://127.0.0.1:3001/api";       // WeKit listens inside this process
var QUEUE_PREFIX = "hermes_pending_";
var POLL_INTERVAL_S = 2;
// 放弃一条消息前的尝试次数. 设上界是不让一条永久坏掉的消息无限重试; 不设成 1 是
// 因为一次超时或一次瞬时抖动, 不该让用户的附件就这么悄无声息地丢掉.
//
// Attempts before an item is abandoned. Bounded so one permanently broken
// message cannot be retried forever, but more than one so a timeout or a
// momentary hiccup does not silently cost the user their attachment.
var MAX_TRIES = 3;

/**
 * 把一次 http 响应压成一行日志.
 * 响应对象是 WeKit 自己拼的, 出错路径下 `status` 或 `ok` 可能根本不存在, 所以这里
 * 不假设任何字段一定有: 早先的版本会打出 "status=undefined", 把一次本来成功的下载
 * 判成了失败.
 *
 * One-line summary of an http response for the log.
 * WeKit builds the response object itself and an error path can leave `status`
 * or `ok` absent, so nothing here assumes a field exists — an earlier version
 * printed "status=undefined" and called a working download a failure.
 */
function describe(resp) {
    if (!resp) return "no response";
    var status = (typeof resp.status === "number") ? resp.status : "?";
    var out = "status=" + status + " ok=" + (resp.ok === true);
    if (resp.error) out += " err=" + resp.error;
    else if (resp.body) out += " body=" + String(resp.body).substring(0, 120);
    return out;
}

// 微信消息类型 -> 能处理它的那个 WeKit 下载接口.
// 1090519089 (0x41000031) 是微信用于文件传输的那个变体.
//
// WeChat message type -> the WeKit download endpoint that understands it.
// 1090519089 (0x41000031) is the variant WeChat uses for file transfers.
var ENDPOINT = {
    3: "image",
    34: "voice",
    47: "sticker",
    49: "file",
    1090519089: "file"
};

// 从 insert 钩子里捕获到的, 当前活着的 SQLiteDatabase 句柄. WeKit 除了 msgSvrId
// 之外没有任何办法定位一条消息, 而它又根本不产出 msgSvrId; 但钩子把数据库本身递到
// 了我们手上, 所以脚本装上之前就已经到达的消息仍然够得着.
//
// The live SQLiteDatabase handle, captured from the insert hook. WeKit exposes
// no way to look a message up by anything other than msgSvrId, and no way to
// obtain a msgSvrId at all — but the hook hands us the database itself, so
// messages that arrived before this script was installed are still reachable.
var lastDb = null;

/**
 * 把消息表里还躺着的, 已收到的媒体消息补进队列.
 * 等数据库句柄拿到之后跑一次, 这样脚本装上之前发来的文件也还取得回来.
 *
 * Queue any received media still sitting in the message table.
 * Runs once the database handle is known, so a file sent before the script was
 * installed can still be collected.
 */
function backfill(limit) {
    if (!lastDb) return 0;
    var found = 0;
    var cur = null;
    try {
        cur = lastDb.rawQuery(
            "SELECT msgSvrId, type, talker FROM message " +
            "WHERE isSend = 0 AND msgSvrId > 0 AND type IN (3,34,47,49,1090519089) " +
            "ORDER BY createTime DESC LIMIT " + limit, null);
        while (cur.moveToNext()) {
            // 按字符串取, 不是数字, 原因见下面的钩子
            var id = cur.getString(0);   // string, not number — see the hook
            var type = cur.getInt(1);
            var talker = cur.getString(2) || "";
            if (!ENDPOINT[type] || !id || id === "0") continue;
            storage.set(QUEUE_PREFIX + id, type + "|" + talker);
            log.i("hermes-media-bridge: backfilled msgSvrId=" + id + " type=" + type);
            found++;
        }
    } catch (e) {
        log.e("hermes-media-bridge: backfill failed: " + e);
    } finally {
        if (cur) { try { cur.close(); } catch (e2) {} }
    }
    return found;
}

function onLoad() {
    log.i("hermes-media-bridge: starting");

    // 要炸就在这里炸, 别拖到第一次下载: 令牌没改过会让每一次请求都变成 401, 而唯一
    // 的痕迹只是一行 warning, 看上去像"媒体下载偶尔抽风", 而不是"这个脚本压根
    // 没配置过".
    //
    // Fail loudly rather than at the first download: an unedited token turns
    // every fetch into a 401 whose only trace is a warning line, which reads
    // like "media retrieval is flaky" instead of "the script was never set up".
    if (TOKEN === "PUT-YOUR-WEKIT-TOKEN-HERE") {
        log.e("hermes-media-bridge: TOKEN is still the placeholder — edit it to " +
              "match WeKit's API server token, then reload the script. " +
              "No media will be downloaded until you do.");
    }

    xposed.hookAfter(
        "com.tencent.wcdb.database.SQLiteDatabase",
        "insertWithOnConflict",
        function (thisObj, args, result) {
            // 这条路径要尽可能短: 它跑在微信的数据库线程上.
            // Keep this path as short as possible: it is on WeChat's DB thread.
            try {
                if (args[0] !== "message") return;
                // 活着的 SQLiteDatabase 句柄, backfill() 要用
                lastDb = thisObj;      // the live SQLiteDatabase — see backfill()
                var values = args[2];

                // 每来一行消息就打一条, 这样才分得清"钩子根本没触发"和"钩子触发
                // 了但跳过了这条".
                //
                // One line per message row, so it is possible to tell "the hook
                // never fired" apart from "the hook fired and skipped it".
                log.d("hermes-media-bridge: insert type=" + values.getAsInteger("type") +
                      " isSend=" + values.getAsInteger("isSend") +
                      " msgSvrId=" + values.getAsString("msgSvrId"));

                // 自己发出去的消息
                if (values.getAsInteger("isSend") == 1) return;   // our own message

                var type = values.getAsInteger("type");
                if (!type || !ENDPOINT[type]) return;

                // 这个 id 必须按字符串读. msgSvrId 是 64 位值, 远超过 2^53, 一旦
                // 让它变成 JS number 就会被静默四舍五入, 下载时就会去要一条根本不
                // 存在的消息 id.
                //
                // Read the id as a STRING. msgSvrId is a 64-bit value well past
                // 2^53, so letting it become a JS number silently rounds it —
                // the download then asks for a message id that does not exist.
                var id = values.getAsString("msgSvrId");
                if (!id || id === "0") return;

                var talker = values.getAsString("talker") || "";
                storage.set(QUEUE_PREFIX + id, type + "|" + talker);
                log.i("hermes-media-bridge: queued msgSvrId=" + id + " type=" + type);
            } catch (e) {
                log.e("hermes-media-bridge: hook failed: " + e);
            }
        }
    );

    // 下载器. 跑在自己的线程上, 而且只碰全局变量, 所以不依赖从钩子那里闭包捕获
    // 任何东西.
    //
    // Downloader. Runs on its own thread and only ever touches globals, so it
    // does not depend on closing over anything from the hook.
    task.run(function () {
        log.i("hermes-media-bridge: downloader running");
        var didBackfill = false;
        while (true) {
            // 数据库句柄要等第一次 insert 之后才存在, 所以这次一次性的 backfill
            // 要等它出现, 而不是在脚本加载时就跑.
            //
            // The database handle only exists after the first insert, so the
            // one-shot backfill waits for it rather than running at load time.
            if (!didBackfill && lastDb) {
                didBackfill = true;
                log.i("hermes-media-bridge: backfill queued " + backfill(10) + " item(s)");
            }
            try {
                var keys = storage.keys();
                for (var i = 0; i < keys.length; i++) {
                    var key = keys[i];
                    if (key.indexOf(QUEUE_PREFIX) !== 0) continue;

                    var id = key.substring(QUEUE_PREFIX.length);
                    var parts = String(storage.get(key)).split("|");
                    var endpoint = ENDPOINT[parseInt(parts[0], 10)];
                    var talker = parts.length > 1 ? parts[1] : "";
                    var tries = parts.length > 2 ? parseInt(parts[2], 10) : 0;
                    if (isNaN(tries)) tries = 0;

                    if (!endpoint) { storage.remove(key); continue; }

                    // 只有拿到*明确*结果, 或者尝试次数已经烧完, 才把条目从队列里
                    // 删掉. 早先的版本在尝试之前就删了, 于是一次瞬时失败就把附件
                    // 永久弄丢.
                    //
                    // An entry is dropped only on a *definite* outcome, or once
                    // it has burned its attempts. An earlier version dropped it
                    // before even trying, which meant one transient failure lost
                    // the attachment for good.
                    tries++;
                    if (tries >= MAX_TRIES) {
                        storage.remove(key);
                    } else {
                        storage.set(key, parts[0] + "|" + talker + "|" + tries);
                    }

                    // 图片和文件先让微信把字节从 CDN 拉进它自己的缓存. 微信从来
                    // 没有自动下载过的消息(移动网络下的大图, 一个旧文件)本地根本
                    // 没有字节, 直接 GET 只会拿到缩略图或者失败, 这一步就是补这个
                    // 洞. 缓存调用是幂等的, 对已经缓存好的媒体再跑一次代价很低.
                    // 语音和表情没有缓存接口, 直接 GET.
                    //
                    // For images and files, ask WeChat to pull the bytes from
                    // the CDN into its own cache first. A message WeChat never
                    // auto-downloaded (large image on mobile data, an old file)
                    // has no local bytes, so a bare GET would only ever return a
                    // thumbnail or fail — this closes that gap. The cache call is
                    // idempotent, so running it on already-cached media is cheap.
                    // Voice and stickers have no cache endpoint; they GET directly.
                    if (endpoint === "image" || endpoint === "file") {
                        // 单独一个 try: 缓存这一步只是*优化*, 作用是覆盖微信从没
                        // 下载过媒体的情况, 所以它抛异常(WeKit 版本旧, 没有这个
                        // 接口, 或者 socket 出错)时, 下载仍然必须照常尝试. 跟外层
                        // 共用 try/catch 会把后面那个 GET 一起跳过.
                        //
                        // Isolated: the cache step is an *optimisation* — it
                        // helps when WeChat never downloaded the media — so if
                        // it throws (an older WeKit without this endpoint, a
                        // socket error) the download must still be attempted.
                        // Sharing the outer try/catch would have skipped the GET.
                        try {
                            var cacheUrl = API + "/messages/" + id + "/" + endpoint + "/cache" +
                                (endpoint === "file" && talker ?
                                    ("?talker=" + encodeURIComponent(talker)) : "");
                            var cresp = http.post(cacheUrl, null, null,
                                                  { Authorization: "Bearer " + TOKEN });
                            // 成功失败都记: 成功时这一行是 CDN 那步确实跑过的唯一
                            // 证据, 而"没看到 warning"不算证据.
                            //
                            // Logged either way: on success this is the only
                            // evidence the CDN step ran at all, and inferring it
                            // from a missing warning is not evidence.
                            log.i("hermes-media-bridge: cache " + endpoint + " " + id +
                                  " -> " + describe(cresp));
                        } catch (ce) {
                            log.w("hermes-media-bridge: cache " + endpoint + " " + id +
                                  " threw (" + ce + ") — downloading anyway");
                        }
                    }

                    var url = API + "/messages/" + id + "/" + endpoint;
                    var resp = http.get(url, talker ? { talker: talker } : {},
                                        { Authorization: "Bearer " + TOKEN });
                    if (resp && resp.ok === true) {
                        // 明确成功
                        storage.remove(key);          // definite success
                        log.i("hermes-media-bridge: downloaded " + id + " -> " + resp.body);
                    } else {
                        // 这*未必*是失败, 正因如此重试才有价值. 这个接口返回的是
                        // 保存后的*路径*而不是字节, 所以 WeKit HTTP 客户端里写死的
                        // 10 秒读超时, 全花在微信从 CDN 拉数据上: 附件一大就会把它
                        // 撑爆, 而下载本身还在继续跑. 下一次尝试会发现文件已经缓存
                        // 好了, 几乎立刻返回. agent 本来就是靠扫下载目录来定位媒体
                        // 的, 所以这里超时并不等于用户丢了文件.
                        //
                        // NOT necessarily a failure, which is why the retry is
                        // worth having. This endpoint answers with the saved
                        // *path*, not the bytes, so the 10s read timeout baked
                        // into WeKit's HTTP client is spent on WeChat pulling
                        // from the CDN — a large attachment blows through it
                        // while the download itself carries on. The next attempt
                        // then finds the file already cached and returns almost
                        // at once. The agent locates media by scanning the
                        // download folder anyway, so a timeout here does not
                        // mean the user lost the file.
                        log.w("hermes-media-bridge: download " + endpoint + " " + id +
                              " inconclusive (attempt " + tries + "/" + MAX_TRIES + ") -> " +
                              describe(resp) + " — the file may still have landed; " +
                              "large media exceeds WeKit's 10s HTTP read timeout");
                    }
                }
            } catch (e) {
                log.e("hermes-media-bridge: downloader error: " + e);
            }
            datetime.sleepS(POLL_INTERVAL_S);
        }
    });
}
