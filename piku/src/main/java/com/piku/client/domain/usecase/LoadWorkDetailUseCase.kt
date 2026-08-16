package com.piku.client.domain.usecase

import com.piku.client.data.repository.DetailRepository
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import javax.inject.Inject

class LoadWorkDetailUseCase @Inject constructor(
    private val detailRepository: DetailRepository,
) {
    /**
     * @param existing 已解析的锁页 detail，密码解锁时传入可跳过重复的详情页 HTML 请求
     */
    suspend operator fun invoke(
        work: Work,
        password: String = "",
        existing: WorkDetail? = null,
    ): Result<WorkDetail> =
        detailRepository.getWorkDetail(work, password, existing)
}
