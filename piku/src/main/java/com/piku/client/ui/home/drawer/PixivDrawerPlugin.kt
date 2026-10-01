package com.piku.client.ui.home.drawer

import com.piku.client.domain.model.WorkSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixivDrawerPlugin @Inject constructor() : SourceDrawerPlugin {

    override val source = WorkSource.PIXIV

    override val ownsAdultRow: Boolean = true
}
