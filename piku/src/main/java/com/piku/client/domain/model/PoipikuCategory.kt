package com.piku.client.domain.model

import com.piku.client.R

enum class PoipikuCategory(val cd: Int, val nameRes: Int) {
    ALL(-1, R.string.home_category_all),
    RAKUGAKI(4, R.string.category_raku_gaki),
    JISHUREN(5, R.string.category_jishuren),
    DEKITA(6, R.string.category_dekita),
    KAKO_WO_SARASU(7, R.string.category_kako_wo_sarasu),
    KUYOU(9, R.string.category_kuyo),
    SAGYOSHINCHOKU(10, R.string.category_sagyoshinchoku),
    OSHIRASE(14, R.string.category_oshirase),
    KAKIKAKE(15, R.string.category_kakikake),
    KAKENEE(16, R.string.category_kakenee),
    MEMO(17, R.string.category_memo),
    RIHABIRI(22, R.string.category_rihabiri),
    NETABARE(23, R.string.category_netabare),
    SHIRIWOTATAKU(30, R.string.category_shiriwotataku),
    OSHINAGAKI(32, R.string.category_oshinagaki),
    ;

    companion object {
        /** 按站点 CD 查分类（不含「全部」），未知 CD 返回 null */
        fun fromCd(cd: Int): PoipikuCategory? =
            entries.firstOrNull { it != ALL && it.cd == cd }
    }
}

data class CategoryGroup(
    val titleRes: Int,
    val categories: List<PoipikuCategory>,
)

val CATEGORY_GROUPS = listOf(
    CategoryGroup(
        R.string.category_group_practice,
        listOf(
            PoipikuCategory.RAKUGAKI,
            PoipikuCategory.JISHUREN,
            PoipikuCategory.RIHABIRI,
        ),
    ),
    CategoryGroup(
        R.string.category_group_share,
        listOf(
            PoipikuCategory.DEKITA,
            PoipikuCategory.KAKO_WO_SARASU,
            PoipikuCategory.KUYOU,
        ),
    ),
    CategoryGroup(
        R.string.category_group_wip,
        listOf(
            PoipikuCategory.SAGYOSHINCHOKU,
            PoipikuCategory.KAKIKAKE,
            PoipikuCategory.KAKENEE,
        ),
    ),
    CategoryGroup(
        R.string.category_group_community,
        listOf(
            PoipikuCategory.OSHIRASE,
            PoipikuCategory.MEMO,
            PoipikuCategory.NETABARE,
            PoipikuCategory.SHIRIWOTATAKU,
            PoipikuCategory.OSHINAGAKI,
        ),
    ),
)