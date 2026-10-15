package com.piku.client.core.ui.common

import androidx.annotation.StringRes
import com.piku.client.R
import com.piku.client.core.model.WorkSource

/** 源短名。新增源时 here 的 when 会编译报错，提醒补一条标签 */
@StringRes
fun WorkSource.labelRes(): Int = when (this) {
    WorkSource.POIPIKU -> R.string.home_source_poipiku
    WorkSource.PIXIV -> R.string.home_source_pixiv
    WorkSource.AO3 -> R.string.home_source_ao3
}
