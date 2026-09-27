package com.meme.finder.ui.screen.gallery

import androidx.compose.runtime.Immutable
import com.meme.finder.domain.model.ImageItem
import java.util.Calendar

/**
 * 把扁平图片列表转成"分组头 + 图片"的扁平序列，供 LazyVerticalGrid 顺序渲染。
 * 序列中每个 Header 占满整行（GridItemSpan(maxLineSpan)），Item 是单个图片 cell。
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
sealed class GalleryEntry {

    /** 月分组的标题项：2024年3月 这种 */
    @Immutable
    data class Header(
        val year: Int,
        val month: Int,  // 1..12
    ) : GalleryEntry() {
        /** 渲染文本，如 "2024年3月"。 */
        val text: String get() = "$year 年 $month 月"

        /** ISO 风格键，便于做映射。 */
        val key: String get() = "${year}_${month}"
    }

    /** 单张图片 cell。 */
    @Immutable
    data class Item(val image: ImageItem) : GalleryEntry()
}

/**
 * 把图片列表按"年月"分组：返回 [(月分头, 月内图片)] 列表。
 * 组之间按拍摄时间递减，组内同样按拍摄时间递减。
 */
@Immutable
data class GalleryGroup(
    val header: GalleryEntry.Header,
    val items: List<ImageItem>,
)

private fun galleryTimestampOf(item: ImageItem): Long =
    item.dateTakenMs ?: (item.dateAddedSec * 1000L)

fun groupByMonth(items: List<ImageItem>): List<GalleryGroup> {
    if (items.isEmpty()) return emptyList()
    val sorted = items.sortedByDescending(::galleryTimestampOf)
    val result = ArrayList<GalleryGroup>(sorted.size / 50 + 1)
    var currentHeader: GalleryEntry.Header? = null
    var currentBucket: ArrayList<ImageItem>? = null
    val cal = Calendar.getInstance()
    for (item in sorted) {
        cal.timeInMillis = galleryTimestampOf(item)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1  // Calendar.MONTH 是 0-based
        if (currentHeader?.year != year || currentHeader?.month != month) {
            currentHeader?.let { h -> result.add(GalleryGroup(h, currentBucket!!)) }
            currentHeader = GalleryEntry.Header(year, month)
            currentBucket = ArrayList()
        }
        currentBucket!!.add(item)
    }
    currentHeader?.let { h -> result.add(GalleryGroup(h, currentBucket!!)) }
    return result
}

/**
 * 把分组列表转成扁平的 entry 序列，用于快速跳转时按 index 查找 Header。
 * Header 占 index N，其后 N+1..N+M 是该组的 items，依此类推。
 */
fun flattenGroups(groups: List<GalleryGroup>): List<GalleryEntry> {
    if (groups.isEmpty()) return emptyList()
    val total = groups.sumOf { 1 + it.items.size }
    val result = ArrayList<GalleryEntry>(total)
    for (g in groups) {
        result.add(g.header)
        g.items.forEach { result.add(GalleryEntry.Item(it)) }
    }
    return result
}

/** 给定 [entries] 和当前第一个可见 item 的全局 index，找到最近的 Header。 */
fun findVisibleHeader(entries: List<GalleryEntry>, firstVisibleIndex: Int): GalleryEntry.Header? {
    if (entries.isEmpty()) return null
    val safeIdx = firstVisibleIndex.coerceIn(0, entries.lastIndex)
    // 从当前可见位置往前找第一个 Header
    for (i in safeIdx downTo 0) {
        val e = entries[i]
        if (e is GalleryEntry.Header) return e
    }
    // 找不到（理论上不可能，列表第一个应该是 Header）
    return null
}
