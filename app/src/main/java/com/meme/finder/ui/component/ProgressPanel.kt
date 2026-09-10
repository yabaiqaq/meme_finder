package com.meme.finder.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.meme.finder.data.progress.OverallProgress
import com.meme.finder.data.progress.TaskProgress

/**
 * 进度面板：任一任务在跑就展开，显示扫描/OCR/标签的 done/total + 进度条。
 */
@Composable
fun ProgressPanel(progress: OverallProgress) {
    AnimatedVisibility(
        visible = progress.anyRunning,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (progress.scan.isRunning) ProgressRow("扫描相册", progress.scan)
            if (progress.ocr.isRunning)  ProgressRow("OCR 识别", progress.ocr)
            if (progress.label.isRunning) ProgressRow("标签识别", progress.label)
        }
    }
}

@Composable
private fun ProgressRow(label: String, p: TaskProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${p.done}/${p.total}", style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            progress = { p.ratio },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        )
    }
}
