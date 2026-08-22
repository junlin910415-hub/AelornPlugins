package tw.linsy.aelorn.ktref

import org.bukkit.plugin.java.JavaPlugin
// 星號匯入是刻意的。operator 擴充（get/set/contains）必須逐一匯入才會被解析，
// 漏掉 set 的症狀是「no 'set' operator method providing array access」，
// 而且會讓那一行的型別推導失敗、把錯誤擴散到後面幾行 —— 逐一匯入不值得那個風險。
import tw.linsy.aelorn.kt.*
import tw.linsy.aelorn.lib.AelornLib
import tw.linsy.aelorn.lib.store.Document
import tw.linsy.aelorn.lib.store.Store
import tw.linsy.aelorn.lib.store.StoreConfig

/**
 * 純 Kotlin 的插件參考實作 —— 整個專案一個 `.java` 檔都沒有。
 *
 * 這支存在的理由是回答一個問題：艾洛恩的插件可不可以直接寫成純 Kotlin。
 * 答案是可以，而且沒有任何一層需要為此改動 —— Bukkit 只認 class 檔，
 * 不在乎是哪個語言產生的。主類別繼承 JavaPlugin 就結束了。
 *
 * 三件與 Java 專案不同、而且都不是選配的事：
 *
 * 1. **kotlin-stdlib 必須在執行期拿得到。** 這支刻意**沒有**自己宣告它，
 *    改用 `depend: [AelornLibKt]` —— 那支已經宣告了，而 Paper 的插件 classloader
 *    看得到相依插件的 classpath。少一份要維護的版本號，也少一次啟動時的解析。
 *
 * 2. **建置順序是 Java 先、Kotlin 後**（見 build-all.ps1）。純 Kotlin 專案沒有
 *    `src\main\java\`，javac 那一段會被跳過。
 *
 * 3. **`-no-stdlib` 是必要的**，否則編譯器會把自己那份 stdlib 塞進來，
 *    與執行期那份混用會在用到差異 API 時才丟 NoSuchMethodError。
 *
 * 不部署到正式服。它是 CONVENTIONS 的參考實作，跟 AelornWorldEvents 之於 Java 一樣。
 */
class KotlinRefPlugin : JavaPlugin() {

    override fun onEnable() {
        val core = AelornLib.get()
        if (core == null) {
            logger.severe("AelornLib 未載入。")
            server.pluginManager.disablePlugin(this)
            return
        }

        // 儲存層 + Kotlin DSL：這幾行在 Java 那邊要寫成 builder 鏈加上 lambda。
        val profiles: Store = store("profiles", StoreConfig.memory())

        profiles.save("alice", document {
            "name" to "Alice"
            "level" to 30
            "stats" nested {
                "kills" to 12
                "deaths" to 3
            }
        })
        profiles.save("bob", document {
            "name" to "Bob"
            "level" to 8
        })

        // 讀出、改一點、寫回 —— 走核心的逐鍵鎖，不是 get-then-put。
        profiles.edit("bob") { "level" to 9 }.join()

        val veterans = profiles.query {
            "level" gte 9
            sortDesc("level")
        }

        val alice: Document? = profiles.load("alice")
        val kills = alice?.value<Int>("stats.kills") ?: -1

        logger.info(
            "純 Kotlin 參考實作就緒：核心 ${core.version()}、" +
                "平台 ${core.platform().describe()}、" +
                "查詢命中 ${veterans.size} 筆（${veterans.joinToString { it.key() }}）、" +
                "alice.stats.kills=$kills"
        )
    }
}
