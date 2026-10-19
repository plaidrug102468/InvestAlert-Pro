package com.investalert.app.data

data class TraderAttachment(
    val name: String,
    val mimeType: String,
    val extractedText: String,
    val kind: String
) {
    val summary: String
        get() = extractedText.trim().take(6000)
}
