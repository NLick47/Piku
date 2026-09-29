package com.piku.client.ui.common

import androidx.annotation.StringRes
import com.piku.client.R
import com.piku.client.domain.model.WorkSource

/** 源短名。新增源时 here 的 when 会编译报错，提醒补一条标签 */
@StringRes
fun WorkSource.labelRes(): Int = when (this) {
    WorkSource.POIPIKU -> R.string.home_source_poipiku
    WorkSource.PIXIV -> R.string.home_source_pixiv
}
