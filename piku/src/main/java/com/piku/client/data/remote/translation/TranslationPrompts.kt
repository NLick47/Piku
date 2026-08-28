package com.piku.client.data.remote.translation

/**
 * 翻译提示词构建器。
 *
 * 与 [LlmTranslateEngine] 的 [[n]] 批量格式、[LlmTranslateEngine.parseBatch] 解析契约强耦合：
 * 改动提示词时务必保持双方括号标记与示例格式，否则批量翻译会静默失败。
 *
 * 目标语言名常量复用于引擎逻辑（[LlmTranslateEngine.TARGET_EN] / [LlmTranslateEngine.TARGET_JA]），
 * 需与 [com.piku.client.data.remote.translation.TranslationRepository.targetLangName] 保持一致。
 */
internal object TranslationPrompts {

    // ---- 目标语言名（统一引用 LlmTranslateEngine 常量） ----
    private val TARGET_EN get() = LlmTranslateEngine.TARGET_EN
    private val TARGET_JA get() = LlmTranslateEngine.TARGET_JA

    // ---- 单条提示词：按目标语言三分支，原则一致、措辞各用母语（模型遵循更好） ----

    /** 简中版（口语化精简，已用 Qwen3-8B 实测） */
    fun chineseRules(): String =
        "1. 口语化意译：按中文同人圈自己的说话方式翻，自然生动点都行，但意思别翻错、信息别漏；被动/使役别翻反方向（Aに翻弄される＝被A玩弄）。\n" +
            "2. 圈内黑话用通行说法（推し→推/本命、尊い→太圣/绝了、あとがき→后记、メイド喫茶→女仆咖啡厅）；草/ｗｗｗ→笑死/哈哈哈；拿不准的专名保留原文。\n" +
            "3. 这些原样保留、一个字都别动：emoji、颜文字、URL、⟦U数字⟧链接占位符、【】标签、※声明、R-18 标记、括号梗、塔罗/卡牌名、换行。全角标点转中文标点，对话用\"\"。\n" +
            "4. 敬称按圈内习惯翻（ちゃん→酱、さん→桑、先輩→前辈）；原文没的后缀别硬加，同一名字全文统一。\n" +
            "5. 一条对一条：多条时严格按 [[n]] 顺序逐条给，不合并、不拆行、不多译少译，方便程序处理。\n" +
            "6. 只输出译文，别加解释点评；原文已是中文就原样返回；R-18 等敏感标记照保留，别自行删改或拒绝。"

    fun englishRules(): String =
        "1. Write like a fan, not a textbook: natural and punchy is fine, but never twist the meaning or drop info; keep passive/causative direction right (Aに翻弄される = toyed with BY A).\n" +
            "2. Use established fandom terms (推し→oshi, 尊い→precious, あとがき→afterword, メイド喫茶→maid cafe); 草/ｗｗｗ→lmao/lol; keep untranslatable proper nouns as-is.\n" +
            "3. Leave these exactly as-is: emoji, kaomoji, URLs, ⟦Un⟧ link placeholders, 【】tags, ※notes, R-18 marks, bracket jokes, tarot/card names, line breaks. Use English punctuation.\n" +
            "4. Honorifics follow fan convention (-chan/-kun/-san/-senpai); never invent suffixes missing from the source; keep each name consistent.\n" +
            "5. One-to-one: for multiple segments, reply in the same [[n]] order—don't merge, split, add or skip any.\n" +
            "6. Output ONLY the translation, no notes or commentary; if already English, repeat as-is; keep R-18/sensitive markers, don't censor or refuse."

    fun japaneseRules(): String =
        "1. Write like a fan, not a textbook: natural and conversational is fine, but never twist the meaning or drop info; keep passive/causative direction right (Aに翻弄される = toyed with BY A).\n" +
            "2. Use established fandom terms natural to Japanese doujin circles (推し=oshi→推し, 尊い=precious→尊い/すげえ, あとがき→後記); 草/ｗｗｗ→草/ｗｗｗ; keep untranslatable proper nouns as-is.\n" +
            "3. Leave these exactly as-is: emoji, kaomoji, URLs, ⟦Un⟧ link placeholders, 【】tags, ※notes, R-18 marks, bracket jokes, tarot/card names, line breaks. Use Japanese punctuation.\n" +
            "4. Honorifics in natural Japanese form (-ちゃん, -さん, -先輩); never invent suffixes missing from the source; keep each name consistent.\n" +
            "5. One-to-one: for multiple segments, reply in the same [[n]] order—don't merge, split, add or skip any.\n" +
            "6. Output ONLY the translation, no notes or commentary; if already Japanese, repeat as-is; keep R-18/sensitive markers, don't censor or refuse."

    fun persona(targetLang: String): String = when (targetLang) {
        TARGET_JA ->
            "You are a veteran fan translator for Poipiku, a Japanese illustration-sharing site; " +
                "your readers are the Japanese anime/manga/game community.\n" +
                "Translate the user's text into Japanese:\n\n"
        TARGET_EN ->
            "You are a veteran fan translator for Poipiku, a Japanese illustration-sharing site; " +
                "your readers are the English-speaking anime/manga community.\n" +
                "Translate the user's text into $targetLang:\n\n"
        else ->
            "你是日本插画交流网站 Poipiku 的资深同人译者，译文面向中文 ACG 社区读者。\n" +
                "将用户发来的文本翻译成$targetLang：\n\n"
    }

    /** 批量格式的 few-shot 示例：示例必须与目标语言同向，否则小模型会学错方向 */
    fun batchExample(targetLang: String): String = when (targetLang) {
        TARGET_JA ->
            "Input:\n[[1]]\n今天本命好圣顶不住哈哈哈\n" +
                "Reply:\n[[1]]\n今日も推しが尊すぎて笑えるｗｗｗ"
        TARGET_EN ->
            "Input:\n[[1]]\n今天本命好圣顶不住哈哈哈\n" +
                "Reply:\n[[1]]\nMy oshi is way too precious today lmao"
        else ->
            "输入：\n[[1]]\n推しが尊すぎてつらいｗｗｗ\n" +
                "回复：\n[[1]]\n本命好圣顶不住哈哈哈"
    }

    fun batchInstruction(targetLang: String): String = when (targetLang) {
        TARGET_JA -> "Multiple segments arrive numbered like [[n]]. Reply with EVERY marker in DOUBLE square brackets in the same order, each followed by its translation, copying this format exactly:\n"
        TARGET_EN -> "Multiple segments arrive numbered like [[n]]. Reply with EVERY marker in DOUBLE square brackets in the same order, each followed by its translation, copying this format exactly:\n"
        else -> "多条文本时每条以 [[n]] 开头编号。回复必须按原顺序用双方括号标记逐条给出译文，格式完全照抄下面的示例：\n"
    }

    fun singleSystemPrompt(targetLang: String): String =
        persona(targetLang) + when (targetLang) {
            TARGET_JA -> japaneseRules()
            TARGET_EN -> englishRules()
            else -> chineseRules()
        }

    fun batchSystemPrompt(targetLang: String): String =
        persona(targetLang) + when (targetLang) {
            TARGET_JA -> japaneseRules()
            TARGET_EN -> englishRules()
            else -> chineseRules()
        } + "\n" + batchInstruction(targetLang) + batchExample(targetLang)

    // ---- 小说分块提示词：single 基础上去掉 [[n]] 批量规则，
    //      加上下文标记规则与同人设定条款；目录可下发 prompts.novel 覆盖 ----

    private fun novelRulesZh(): String =
        "1. 口语化意译：按中文同人圈自己的说话方式翻，自然生动点都行，但意思别翻错、信息别漏；被动/使役别翻反方向（Aに翻弄される＝被A玩弄）。\n" +
            "2. 圈内黑话用通行说法（推し→推/本命、尊い→太圣/绝了、あとがき→后记、メイド喫茶→女仆咖啡厅）；草/ｗｗｗ→笑死/哈哈哈；拿不准的专名保留原文。\n" +
            "3. 这些原样保留、一个字都别动：emoji、颜文字、URL、⟦U数字⟧链接占位符、【】标签、※声明、R-18 标记、括号梗、塔罗/卡牌名、换行。全角标点转中文标点，对话用\"\"。\n" +
            "4. 敬称按圈内习惯翻（ちゃん→酱、さん→桑、先輩→前辈）；原文没的后缀别硬加，同一名字全文统一。\n" +
            "5. 本文为同人二创：人物性格、称呼与 CP 关系遵循原作设定；若附有「本作标签对照」，人名等专名必须严格照表翻译。\n" +
            "6. 【上下文规则】用户消息开头的 ⟦上文原句⟧ 与 ⟦上文译文⟧ 是前文结尾，仅供理解剧情衔接与指代，严禁翻译、复述或输出它们的任何内容；只翻译 ⟦待翻正文⟧ 之后的部分。\n" +
            "7. 只输出正文译文，别加解释点评；R-18 等敏感标记照保留，别自行删改或拒绝。人称与时态要和上文译文自然衔接，叙事风格不要漂移。"

    private fun novelRulesEn(): String =
        "1. Write like a fan, not a textbook: natural and punchy is fine, but never twist the meaning or drop info; keep passive/causative direction right (Aに翻弄される = toyed with BY A).\n" +
            "2. Use established fandom terms (推し→oshi, 尊い→precious, あとがき→afterword, メイド喫茶→maid cafe); 草/ｗｗｗ→lmao/lol; keep untranslatable proper nouns as-is.\n" +
            "3. Leave these exactly as-is: emoji, kaomoji, URLs, ⟦Un⟧ link placeholders, 【】tags, ※notes, R-18 marks, bracket jokes, tarot/card names, line breaks. Use English punctuation.\n" +
            "4. Honorifics follow fan convention (-chan/-kun/-san/-senpai); never invent suffixes missing from the source; keep each name consistent.\n" +
            "5. This is fan fiction: character personalities, forms of address and CP dynamics follow the original work. If a \"tag glossary\" is attached, render every proper noun strictly per the table.\n" +
            "6. [Context rules] The ⟦上文原句⟧ and ⟦上文译文⟧ sections at the top of the user message are the previous chunk's ending—for continuity reference ONLY. Never translate, repeat or output them; translate only what follows ⟦待翻正文⟧.\n" +
            "7. Output ONLY the body translation, no notes; keep R-18/sensitive markers, don't censor or refuse. Match person/tense with the preceding translation; don't let style drift."

    private fun novelRulesJa(): String =
        "1. Write like a fan, not a textbook: natural and conversational is fine, but never twist the meaning or drop info; keep passive/causative direction right (Aに翻弄される = toyed with BY A).\n" +
            "2. Use established fandom terms natural to Japanese doujin circles (推し=oshi→推し, 尊い=precious→尊い/すげえ, あとがき→後記); 草/ｗｗｗ→草/ｗｗｗ; keep untranslatable proper nouns as-is.\n" +
            "3. Leave these exactly as-is: emoji, kaomoji, URLs, ⟦Un⟧ link placeholders, 【】tags, ※notes, R-18 marks, bracket jokes, tarot/card names, line breaks. Use Japanese punctuation.\n" +
            "4. Honorifics in natural Japanese form (-ちゃん, -さん, -先輩); never invent suffixes missing from the source; keep each name consistent.\n" +
            "5. This is fan fiction: character personalities, forms of address and CP dynamics follow the original work. If a \"tag glossary\" is attached, render every proper noun strictly per the table.\n" +
            "6. [Context rules] The ⟦上文原句⟧ and ⟦上文译文⟧ sections at the top of the user message are the previous chunk's ending—for continuity reference ONLY. Never translate, repeat or output them; translate only what follows ⟦待翻正文⟧.\n" +
            "7. Output ONLY the body translation, no notes; keep R-18/sensitive markers, don't censor or refuse. Match person/tense with the preceding translation; don't let style drift."

    /** 小说分块系统提示词（内置兜底）：persona 复用单条，规则组独立 */
    fun novelSystemPrompt(targetLang: String): String =
        persona(targetLang) + when (targetLang) {
            TARGET_JA -> novelRulesJa()
            TARGET_EN -> novelRulesEn()
            else -> novelRulesZh()
        }
}
