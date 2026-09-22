package com.piku.client.domain.model

/**
 * 作品访问门——App 应给用户渲染的解锁引导类型；null 表示无需门卡。
 *
 * 判定唯一来源：`DetailRepository` 结合作品属性（IllustItem class / 密码框）、
 * 当前登录态、R-18 显示设置与服务端 append 响应，决策后写入 [WorkDetail.gate]。
 * ViewModel 与 UI 只读这一个字段，不再各自推导——历史上"两处判定漂移"导致过
 * 多次回归（登录用户被展示登录门、关注门误判为 Twitter 门等）。
 *
 * poipiku 的服务端判定（逆向自 common.js，2026-09）：
 * append result_num：>0 放行 / -2 密码错 / -3 需登录 / -4 拒绝（原因在 html 原文）/
 * -5 Twitter 关注者限定 / -20 需转推 / 其他负值 → 显示 html 原文。
 */
enum class RestrictionReason {
    /** 需登录（Login 作品属性或服务端 -3/-4 登录提示；匿名时优先展示） */
    LOGIN,

    /** R-18 显示未开启（App 自身的显示策略锁；服务端并不拦，见 DetailRepository 注释） */
    ADULT,

    /** poipiku 内「こっそりフォロー」限定：App 内一键关注作者即可解锁（实测） */
    FOLLOW,

    /** Twitter 关注者限定（TFollower）：服务端经 Twitter 验证，App 只能引导网页端 */
    FOLLOW_TWITTER,

    /** 需转推作者推文（-20）：转推动作在 Twitter/网页端完成 */
    RETWEET,
}
