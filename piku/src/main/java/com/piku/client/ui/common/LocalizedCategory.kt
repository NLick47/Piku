package com.piku.client.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.piku.client.domain.model.PoipikuCategory

/** 命中已知分类用本地化名，否则回退站点原文 */
@Composable
fun localizedCategoryName(categoryCd: Int, rawName: String): String =
    PoipikuCategory.fromCd(categoryCd)?.let { stringResource(it.nameRes) } ?: rawName
