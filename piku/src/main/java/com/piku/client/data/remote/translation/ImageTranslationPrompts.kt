package com.piku.client.data.remote.translation


internal object ImageTranslationPrompts {

    fun prompt(targetLang: String): String = when (targetLang) {
        LlmTranslateEngine.TARGET_ZH -> PROMPT_ZH
        LlmTranslateEngine.TARGET_JA -> PROMPT_JA
        else -> PROMPT_EN
    }

    private const val PROMPT_ZH =
        "这是一张漫画图片。请严格按顺序执行以下步骤：\n" +
        "1) 检测并擦除图中所有原有文字，保留画面完整\n" +
        "2) 将擦除的文字翻译为简体中文\n" +
        "3) 把中文译文按原位置、合适字号写回对应文本框/气泡，保持画风不变\n" +
        "只输出最终图片，不要任何解释。"

    private const val PROMPT_EN =
        "This is a manga page image. Please strictly follow these steps in order:\n" +
        "1) Detect and erase ALL original text from the image, keeping the artwork intact\n" +
        "2) Translate the erased text into English\n" +
        "3) Write the English translation back into the original text boxes and speech balloons at their original positions, with appropriate font size and style that matches the art\n" +
        "Output ONLY the final image with no explanation."

    private const val PROMPT_JA =
        "This is a manga page image. Please strictly follow these steps in order:\n" +
        "1) Detect and erase ALL original text from the image, keeping the artwork intact\n" +
        "2) Translate the erased text into Japanese\n" +
        "3) Write the Japanese translation back into the original text boxes and speech balloons at their original positions, with appropriate font size and style that matches the art\n" +
        "Output ONLY the final image with no explanation."
}
