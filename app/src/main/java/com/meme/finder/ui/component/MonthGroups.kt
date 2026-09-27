package com.meme.finder.ui.component

import androidx.compose.runtime.Immutable
import com.meme.finder.domain.model.ImageItem
import java.util.Calendar

/**
 * 图片列表的"按月分段"表示，供相册页和搜索页共用同一套分组网格。
 *
 * 分组依据：dateTakenMs（拍摄时间，更符合用户对"年月"的直觉）；
 * 如果 dateTakenMs 为空（无 EXIF），fallback 到 dateAddedSec*1000。
 *
 * 必须先按分组键本身排序再分段：DAO 的排序键是 date_added_sec（入库时间），
 * 与分组键 dateTakenMs（拍摄时间）不一致时同一月份会被切成多段，产生重复的
 * "header_年_月" key，LazyGrid 测量到后一段会直接抛
 * IllegalArgumentException: Key was already used 闪退。
 * 排序后月份必然连续，同月图片归入一段，key 唯一。
 */
@Immutable
sealed class MonthEntry {

    /** 月分组的标题项：2024 年 3 月 这种 */
    @Immutable
    data class Header(
        val year: Int,
        val month: Int,  // 1..12
    ) : MonthEntry() {

        /** 渲染文本，如 "2024 年 3 月"。 */
        val text: String get() = "$year 年 $month 月"

        /** ISO 风格键，便于做映射。 */
        val key: String get() = "${year}_${month}"
    }

    /** 单张图片 cell。 */
    @Immutable
    data class Item(val image: ImageItem) : MonthEntry()
}

@Immutable
data class MonthGroup(
    val header: MonthEntry.Header,
    val items: List<ImageItem>,
)

private fun timestampOf(item: ImageItem): Long =
    item.dateTakenMs ?: (item.dateAddedSec * 1000L)

/**
 * 把图片列表按"年月"分组：返回 [(月分头, 月内图片)] 列表。
 * 组之间按拍摄时间递减，组内同样按拍摄时间递减。
 */
fun groupByMonth(items: List<ImageItem>): List<MonthGroup> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedByDescending(::timestampOf)
    val result = ArrayList<MonthGroup>(sorted.size / 50 + 1)
    var currentHeader: MonthEntry.Header? = null
    var currentBucket: ArrayList<ImageItem>? = null
    val cal = Calendar.getInstance()
    for (item in sorted) {
        cal.timeInMillis = timestampOf(item)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1  // Calendar.MONTH 是 0-based
        if (currentHeader?.year != year || currentHeader?.month != month) {
            currentHeader?.let { h -> result.add(MonthGroup(h, currentBucket!!)) }
            currentHeader = MonthEntry.Header(year, month)
            currentBucket = ArrayList()
        }
        currentBucket!!.add(item)
    }
    currentHeader?.let { h -> result.add(MonthGroup(h, currentBucket!!)) }
    return result
}

/**
 * 把分组列表转成扁平的 entry 序列，用于快速跳转时按 index 查找 Header。
 * Header 占 index N，其后 N+1..N+M 是该组的 items，依此类推。
 */
fun flattenGroups(groups: List<MonthGroup>): List<MonthEntry> {
    if (groups.isEmpty()) return emptyList()
    val total = groups.sumOf { 1 + it.items.size }
    val result = ArrayList<MonthEntry>(total)
    for (g in groups) {
        result.add(g.header)
        g.items.forEach { result.add(MonthEntry.Item(it)) }
    }
    return result
}

/** 给定 [entries] 和当前第一个可见 item 的全局 index，找到最近的 Header。 */
fun findVisibleHeader(entries: List<MonthEntry>, firstVisibleIndex: Int): MonthEntry.Header? {
    if (entries.isEmpty()) return null
    val safeIdx = firstVisibleIndex.coerceIn(0, entries.lastIndex)
    // 从当前可见位置往前找第一个 Header
    for (i in safeIdx downTo 0) {
        val e = entries[i]
        if (e is MonthEntry.Header) return e
    }
    // 找不到（理论上不可能，列表第一个应该是 Header）
    return null
}
