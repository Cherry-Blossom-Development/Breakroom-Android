package com.cherryblossomdev.breakroom.text

// Ticket descriptions are HTML from web's rich-text editor
// (RichTextEditor.vue), but older ones are plain text from before it existed
// -- lines separated by newlines. Android edits and shows them as plain text:
//
//   - toPlainText: HTML -> one line per paragraph, list items as "• item" /
//     "1. item" (nested lists indented); entities decoded. Plain text passes
//     through unchanged.
//   - fromPlainText: the reverse, for saving -- one escaped <p> per line, and
//     runs of "• " / "- " / "* " or "1. " lines become <ul> / <ol> lists.
//     Mirrors web utilities/richText.js toRichHtml, plus lists.
//
// Only an edited description is converted back, so one that isn't touched
// keeps its web formatting exactly. Inline formatting (bold, links, colours)
// doesn't survive an edit; hasInlineFormatting lets the edit form say so.
object RichText {

    // Text counts as HTML only if it has real formatting tags -- plain text
    // can contain angle brackets too, like old support requests'
    // "Name <email@example.com>" (same test as web richText.js)
    private val HAS_TAGS = Regex(
        "</?(p|div|br|span|ul|ol|li|h[1-6]|strong|em|b|i|u|s|a|img|blockquote|pre|code|font|hr)\\b[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val INLINE_FORMATTING = Regex(
        "<(strong|em|b|i|u|s|a|img|code|font|h[1-6]|blockquote|pre)\\b|<span\\b[^>]*style=",
        RegexOption.IGNORE_CASE
    )
    private val TOKEN = Regex("<[^>]*>|[^<]+|<")
    private val TAG_NAME = Regex("^</?\\s*([a-zA-Z0-9]+)")
    private val WHITESPACE = Regex("\\s+")
    private val BULLET_LINE = Regex("^\\s*[•\\-*]\\s+(.*)$")
    private val NUMBERED_LINE = Regex("^\\s*\\d+[.)]\\s+(.*)$")
    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")
    private val BLOCKS = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "hr", "tr")

    fun isHtml(text: String?): Boolean = text != null && HAS_TAGS.containsMatchIn(text)

    fun hasInlineFormatting(text: String?): Boolean = isHtml(text) && INLINE_FORMATTING.containsMatchIn(text!!)

    fun toPlainText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        if (!isHtml(text)) return text.replace("\r\n", "\n").trim()

        val out = StringBuilder()
        var atLineStart = true
        // Open lists, innermost last: null for <ul>, else the <ol>'s counter
        val lists = ArrayDeque<IntArray?>()

        fun newLine() {
            if (out.isNotEmpty() && !atLineStart) out.append('\n')
            atLineStart = true
        }

        for (match in TOKEN.findAll(text)) {
            val token = match.value
            if (!token.startsWith("<") || token == "<") {
                var piece = decodeEntities(token).replace(WHITESPACE, " ")
                if (atLineStart) piece = piece.trimStart()
                if (piece.isNotEmpty()) {
                    out.append(piece)
                    atLineStart = false
                }
                continue
            }
            val name = TAG_NAME.find(token)?.groupValues?.get(1)?.lowercase() ?: continue
            val closing = token.startsWith("</")
            when {
                name == "br" -> {
                    out.append('\n')
                    atLineStart = true
                }
                name == "ul" || name == "ol" -> {
                    newLine()
                    if (closing) lists.removeLastOrNull()
                    else lists.addLast(if (name == "ol") intArrayOf(0) else null)
                }
                name == "li" -> {
                    newLine()
                    if (!closing) {
                        val counter = lists.lastOrNull()
                        out.append("  ".repeat(maxOf(lists.size - 1, 0)))
                        out.append(if (counter != null) "${++counter[0]}. " else "• ")
                        atLineStart = true // skip the item's leading whitespace
                    }
                }
                name in BLOCKS -> newLine()
            }
        }
        return out.toString()
            .lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun fromPlainText(text: String): String {
        val out = StringBuilder()
        var openList: String? = null
        fun closeList() {
            openList?.let { out.append("</$it>") }
            openList = null
        }
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val bullet = BULLET_LINE.find(line)
            val numbered = if (bullet == null) NUMBERED_LINE.find(line) else null
            val listType = when {
                bullet != null -> "ul"
                numbered != null -> "ol"
                else -> null
            }
            if (listType != openList) {
                closeList()
                listType?.let {
                    out.append("<$it>")
                    openList = it
                }
            }
            when {
                bullet != null -> out.append("<li>").append(escape(bullet.groupValues[1])).append("</li>")
                numbered != null -> out.append("<li>").append(escape(numbered.groupValues[1])).append("</li>")
                else -> out.append("<p>").append(escape(line)).append("</p>")
            }
        }
        closeList()
        return out.toString()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            body.startsWith("#") -> body.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> when (body.lowercase()) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> m.value
            }
        }
    }
}
