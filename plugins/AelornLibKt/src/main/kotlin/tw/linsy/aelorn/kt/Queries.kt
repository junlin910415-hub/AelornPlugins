@file:JvmName("Queries")

package tw.linsy.aelorn.kt

import tw.linsy.aelorn.lib.store.StoreQuery

/**
 * 查詢的 Kotlin 寫法。
 *
 * ```
 * val top = buildQuery {
 *     "level" gte 30
 *     "world" eq "aswaria"
 *     sortDesc("level")
 *     limit(10)
 * }
 * ```
 *
 * 對照 Java 的鏈式寫法，差別不只是短：條件在這裡是**語句**而不是運算式，所以可以
 * 用一般的 `if` 決定要不要加某個條件。Java 那邊要做同一件事，得把 StoreQuery
 * 存進區域變數再一路重新指派，而那正是「忘了接回傳值」這個 bug 的溫床 ——
 * StoreQuery 是不可變的，漏接一次就整個條件消失，而且不會有任何錯誤。
 */
fun buildQuery(build: QueryScope.() -> Unit): StoreQuery =
    QueryScope().apply(build).query

class QueryScope {

    @PublishedApi
    internal var query: StoreQuery = StoreQuery.all()

    infix fun String.eq(value: Any?) {
        query = query.and(this).equalTo(value)
    }

    infix fun String.ne(value: Any?) {
        query = query.and(this).notEqualTo(value)
    }

    infix fun String.gt(value: Any) {
        query = query.and(this).greaterThan(value)
    }

    infix fun String.gte(value: Any) {
        query = query.and(this).atLeast(value)
    }

    infix fun String.lt(value: Any) {
        query = query.and(this).lessThan(value)
    }

    infix fun String.lte(value: Any) {
        query = query.and(this).atMost(value)
    }

    infix fun String.oneOf(values: Collection<Any?>) {
        query = query.and(this).`in`(values)
    }

    /** 不分大小寫的子字串比對。 */
    infix fun String.has(fragment: String) {
        query = query.and(this).contains(fragment)
    }

    fun exists(path: String) {
        query = query.and(path).exists()
    }

    fun missing(path: String) {
        query = query.and(path).missing()
    }

    fun sortAsc(path: String) {
        query = query.sortAscending(path)
    }

    fun sortDesc(path: String) {
        query = query.sortDescending(path)
    }

    fun limit(count: Int) {
        query = query.limit(count)
    }

    fun skip(count: Int) {
        query = query.skip(count)
    }
}
