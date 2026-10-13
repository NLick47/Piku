package com.piku.client.core.ui.common

import com.piku.client.R
import com.piku.client.core.model.AppError

fun AppError.toFeedErrorRes(): Int = when (this) {
    is AppError.Network -> R.string.home_error_network
    is AppError.NotFound -> R.string.error_content_not_found
    else -> R.string.home_error_parse
}