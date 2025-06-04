package com.example.everytalk.util

import org.commonmark.parser.Parser
import org.commonmark.renderer.text.TextContentRenderer

fun convertMarkdownToPlainText(markdown: String): String {
    if (markdown.isBlank()) return ""
    val parser = Parser.builder().build()
    val document = parser.parse(markdown)
    val renderer = TextContentRenderer.builder().build()
    return renderer.render(document).trim()
}