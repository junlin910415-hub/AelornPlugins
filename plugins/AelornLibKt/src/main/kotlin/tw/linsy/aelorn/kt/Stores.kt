@file:JvmName("Stores")

package tw.linsy.aelorn.kt

import org.bukkit.plugin.Plugin
import tw.linsy.aelorn.lib.AelornLib
import tw.linsy.aelorn.lib.store.Document
import tw.linsy.aelorn.lib.store.Store
import tw.linsy.aelorn.lib.store.StoreConfig
import tw.linsy.aelorn.lib.store.StoreQuery
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Kotlin 這一側的儲存層。
 *
 * 目標不是「把 Java API 再包一層」，而是把 Java 沒有語法可以表達、因此在 Java 那邊
 * 只能寫成樣板的東西拿掉：具名讀取的型別推導、建構文件時的區塊語法、以及
 * 「讀出來、改一點、寫回去」這個佔了絕大多數呼叫的動作。
 *
 * 沒有包成 coroutine。伺服器上的 kotlinx-coroutines 是 1.5.2，比 stdlib 落後好幾個
 * 大版本，而這一層真正的併發模型是核心的虛擬執行緒 —— 疊一層 coroutine 只會多一個
 * 需要對齊的排程器，換不到任何東西。
 */

/** `core.stores().open(this, name, config)` 的簡寫。 */
fun Plugin.store(name: String, config: StoreConfig = StoreConfig.yaml()): Store =
    AelornLib.require().stores().open(this, name, config)

// ── 讀取 ──────────────────────────────────────────────────────────────────

/**
 * 具名型別讀取：`doc.value<Int>("stats.kills")`。
 *
 * 走的是 Document 自己的轉換規則（YAML 把數字讀成字串時仍算數字），而不是硬轉型 ——
 * 硬轉型會在管理員手改過設定檔之後才炸，而且訊息只有 ClassCastException。
 */
inline fun <reified T> Document.value(path: String): T? = when (T::class) {
    String::class -> string(path).orElse(null) as T?
    Int::class -> integer(path).let { if (it.isPresent) it.asInt as T else null }
    Long::class -> longValue(path).let { if (it.isPresent) it.asLong as T else null }
    Double::class -> decimal(path).let { if (it.isPresent) it.asDouble as T else null }
    Boolean::class -> bool(path).orElse(null) as T?
    UUID::class -> uuid(path).orElse(null) as T?
    Document::class -> document(path).orElse(null) as T?
    else -> get(path).orElse(null) as T?
}

/** `"stats.kills" in doc`。`Document` 的成員叫 `has` 不叫 `contains`，所以這個打得中。 */
operator fun Document.contains(path: String): Boolean = has(path)

val Document.isNotEmpty: Boolean get() = !isEmpty

// ── 建立與修改 ────────────────────────────────────────────────────────────

/**
 * 區塊語法建立文件：
 * ```
 * val profile = document {
 *     "name" to player.name
 *     "stats.kills" to 0
 * }
 * ```
 */
fun document(build: DocumentScope.() -> Unit): Document =
    DocumentScope(Document.builder()).apply(build).builder.build()

/** [document] 的作用域；`to` 在這裡是「設定欄位」而不是建立 Pair。 */
class DocumentScope(@PublishedApi internal val builder: Document.Builder) {

    infix fun String.to(value: Any?) {
        builder.set(this, value)
    }

    /** 巢狀區塊：`"stats" nested { "kills" to 3 }`。 */
    infix fun String.nested(build: DocumentScope.() -> Unit) {
        val child = DocumentScope(Document.builder()).apply(build).builder.build()
        builder.set(this, child)
    }

    fun remove(path: String) {
        builder.remove(path)
    }
}

/** 以區塊修改既有文件，回傳新文件（Document 是不可變的）。 */
fun Document.edit(build: DocumentScope.() -> Unit): Document =
    DocumentScope(toBuilder()).apply(build).builder.build()

// ── 儲存區 ────────────────────────────────────────────────────────────────

/**
 * 讀出、依區塊修改、寫回，走核心的逐鍵鎖。
 *
 * ```
 * val current = profiles.load(id)?.value<Int>("logins") ?: 0
 * profiles.edit(id) { "logins" to current + 1 }
 * ```
 * 這是取代 get-then-put 的那個方法 —— 後者在兩個執行緒同時進來時會掉一次更新，
 * 而在區域執行緒的伺服器上「同時」是常態不是意外。
 */
fun Store.edit(key: String, build: DocumentScope.() -> Unit): CompletableFuture<Document> =
    update(key) { current -> current.edit(build) }

/*
 * 這裡刻意**沒有** `operator fun Store.get`／`Document.get`。
 *
 * Kotlin 的成員永遠優先於擴充，而 `Store` 與 `Document` 在 Java 那一側都已經有
 * 名為 `get` 的成員（分別回傳 `CompletableFuture<Optional<Document>>` 與
 * `Optional<Object>`）。Java 的 `get`／`set` 在 Kotlin 會自動被當成陣列存取運算子，
 * 所以 `store["k"]` **一定**解析到那個非同步成員，寫成擴充的同步版永遠不會被呼叫。
 *
 * 這個坑很惡劣：它編得過，型別卻是 Future，於是錯誤出現在幾行之後、訊息還指向別的地方。
 * 實際寫參考插件時就撞到了。與其留下一個永遠打不中的多載，不如給明確的名字。
 */

/** 同步讀取；呼叫端必須已經在可阻塞的執行緒上。非同步請用 `get(key)`。 */
fun Store.load(key: String): Document? = getNow(key).orElse(null)

fun Store.load(key: UUID): Document? = getNow(key.toString()).orElse(null)

/** 同步寫入。 */
fun Store.save(key: String, document: Document) = putNow(key, document)

fun Store.save(key: UUID, document: Document) = putNow(key.toString(), document)

/** 同步刪除；回傳是否真的刪掉了東西。 */
fun Store.drop(key: String): Boolean = deleteNow(key)

fun Store.drop(key: UUID): Boolean = deleteNow(key.toString())

/** `key in store`。`Store` 沒有名為 contains 的成員，所以這個擴充是打得中的。 */
operator fun Store.contains(key: String): Boolean = getNow(key).isPresent

operator fun Store.contains(key: UUID): Boolean = getNow(key.toString()).isPresent

fun Store.edit(key: UUID, build: DocumentScope.() -> Unit): CompletableFuture<Document> =
    edit(key.toString(), build)

/** 查詢結果直接解構：`for ((key, doc) in store.query { … })`。 */
operator fun Store.Entry.component1(): String = key()

operator fun Store.Entry.component2(): Document = document()

/** 同步查詢；非同步請用 `find(query)`。 */
fun Store.query(build: QueryScope.() -> Unit): List<Store.Entry> = findNow(buildQuery(build))
