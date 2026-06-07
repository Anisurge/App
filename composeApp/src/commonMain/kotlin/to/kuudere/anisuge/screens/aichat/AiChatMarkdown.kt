package to.kuudere.anisuge.screens.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val TextPrimary = Color(0xFFF0F0F0)
private val TextSecondary = Color(0xFF8A8A9A)
private val LinkColor = Color(0xFFBF80FF)
private val CodeBg = Color.White.copy(alpha = 0.08f)
private val BlockquoteBar = Color(0xFF9B59B6).copy(alpha = 0.5f)
private val BlockquoteBg = Color.White.copy(alpha = 0.03f)

// ── Public Composables ────────────────────────────────────────────────────────

@Composable
fun AiChatMarkdownText(
    text: String,
    onAnimeClick: (animeId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is Block.Paragraph -> InlineMarkdownParagraph(block.text, onAnimeClick)
                is Block.Header -> HeaderBlock(block.text, block.level, onAnimeClick)
                is Block.BulletList -> BulletListBlock(block.items, onAnimeClick)
                is Block.OrderedList -> OrderedListBlock(block.items, onAnimeClick)
                is Block.Blockquote -> BlockquoteBlock(block.text, onAnimeClick)
                is Block.CodeBlock -> CodeBlockBlock(block.code)
                is Block.Image -> ImageBlock(block.url, block.alt)
            }
        }
    }
}

@Composable
fun AiChatStreamingText(text: String, modifier: Modifier = Modifier) {
    val annotated = remember(text) { buildInlineAnnotatedString(text) }
    Text(
        text = annotated,
        style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
        modifier = modifier,
    )
}

// ── Block Types ───────────────────────────────────────────────────────────────

private sealed class Block {
    data class Paragraph(val text: String) : Block()
    data class Header(val text: String, val level: Int) : Block()
    data class BulletList(val items: List<String>) : Block()
    data class OrderedList(val items: List<String>) : Block()
    data class Blockquote(val text: String) : Block()
    data class CodeBlock(val code: String) : Block()
    data class Image(val url: String, val alt: String) : Block()
}

// ── Parser ────────────────────────────────────────────────────────────────────

private fun parseBlocks(text: String): List<Block> {
    val lines = text.lines()
    val blocks = mutableListOf<Block>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.startsWith("```") -> {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                i++
                blocks.add(Block.CodeBlock(codeLines.joinToString("\n")))
            }
            trimmed.startsWith("### ") -> {
                blocks.add(Block.Header(trimmed.removePrefix("### ").trim(), 3))
                i++
            }
            trimmed.startsWith("## ") -> {
                blocks.add(Block.Header(trimmed.removePrefix("## ").trim(), 2))
                i++
            }
            trimmed.startsWith("# ") -> {
                blocks.add(Block.Header(trimmed.removePrefix("# ").trim(), 1))
                i++
            }
            trimmed.startsWith("> ") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("> ")) {
                    quoteLines.add(lines[i].trimStart().removePrefix("> ").trim())
                    i++
                }
                blocks.add(Block.Blockquote(quoteLines.joinToString(" ")))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.startsWith("- ") || t.startsWith("* ")) {
                        items.add(t.removePrefix("- ").removePrefix("* ").trim())
                        i++
                    } else break
                }
                blocks.add(Block.BulletList(items))
            }
            trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.matches(Regex("^\\d+\\.\\s.*"))) {
                        items.add(t.replaceFirst(Regex("^\\d+\\.\\s"), ""))
                        i++
                    } else break
                }
                blocks.add(Block.OrderedList(items))
            }
            trimmed.startsWith("![") -> {
                val imgMatch = Regex("!\\[([^]]*)\\]\\(([^)]+)\\)").find(trimmed)
                if (imgMatch != null) {
                    blocks.add(Block.Image(imgMatch.groupValues[2], imgMatch.groupValues[1]))
                    i++
                } else {
                    blocks.add(Block.Paragraph(line))
                    i++
                }
            }
            trimmed.isEmpty() -> {
                i++
            }
            else -> {
                val paraLines = mutableListOf(line)
                i++
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.isEmpty() || t.startsWith("```") || t.startsWith("#") ||
                        t.startsWith("> ") || t.startsWith("- ") || t.startsWith("* ") ||
                        t.matches(Regex("^\\d+\\.\\s.*"))
                    ) break
                    paraLines.add(lines[i])
                    i++
                }
                blocks.add(Block.Paragraph(paraLines.joinToString("\n")))
            }
        }
    }

    return blocks
}

// ── Inline Parsing ────────────────────────────────────────────────────────────

private data class InlineSegment(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val linkUrl: String? = null,
)

private fun parseInline(text: String): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    var i = 0
    val sb = StringBuilder()

    fun flush() {
        if (sb.isNotEmpty()) {
            segments.add(InlineSegment(sb.toString()))
            sb.clear()
        }
    }

    while (i < text.length) {
        when {
            text.startsWith("![", i) -> {
                val end = text.indexOf(')', i)
                if (end > i) { flush(); segments.add(InlineSegment(text.substring(i, end + 1))); i = end + 1 }
                else { sb.append(text[i]); i++ }
            }
            text[i] == '[' -> {
                val cb = text.indexOf(']', i)
                if (cb > i + 1 && cb + 1 < text.length && text[cb + 1] == '(') {
                    val cp = text.indexOf(')', cb + 2)
                    if (cp > cb + 2) {
                        flush()
                        segments.add(InlineSegment(text.substring(i + 1, cb), linkUrl = text.substring(cb + 2, cp)))
                        i = cp + 1
                    } else { sb.append(text[i]); i++ }
                } else { sb.append(text[i]); i++ }
            }
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) { flush(); segments.add(InlineSegment(text.substring(i + 2, end), bold = true)); i = end + 2 }
                else { sb.append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) { flush(); segments.add(InlineSegment(text.substring(i + 1, end), code = true)); i = end + 1 }
                else { sb.append(text[i]); i++ }
            }
            text[i] == '*' && i + 1 < text.length && text[i + 1] != '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) { flush(); segments.add(InlineSegment(text.substring(i + 1, end), italic = true)); i = end + 1 }
                else { sb.append(text[i]); i++ }
            }
            text.regionMatches(i, "anisurge://", 0, 11, ignoreCase = true) -> {
                val end = text.indexOfAny(charArrayOf(' ', '\n', ')'), i).let { if (it == -1) text.length else it }
                if (end > i) { flush(); segments.add(InlineSegment(text.substring(i, end), linkUrl = text.substring(i, end))); i = end }
                else { sb.append(text[i]); i++ }
            }
            text.regionMatches(i, "http://", 0, 7, ignoreCase = true) ||
                text.regionMatches(i, "https://", 0, 8, ignoreCase = true) -> {
                val end = text.indexOfAny(charArrayOf(' ', '\n', ')'), i).let { if (it == -1) text.length else it }
                if (end > i) { flush(); segments.add(InlineSegment(text.substring(i, end), linkUrl = text.substring(i, end))); i = end }
                else { sb.append(text[i]); i++ }
            }
            else -> { sb.append(text[i]); i++ }
        }
    }
    flush()
    return segments
}

private fun buildInlineAnnotatedString(text: String): AnnotatedString {
    val segments = parseInline(text)
    return buildAnnotatedString {
        for (seg in segments) {
            when {
                seg.linkUrl != null -> {
                    pushStringAnnotation("URL", seg.linkUrl)
                    withStyle(SpanStyle(color = LinkColor, textDecoration = TextDecoration.Underline)) {
                        append(seg.text)
                    }
                    pop()
                }
                seg.code -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = CodeBg, color = Color(0xFFFFD166))) {
                        append(seg.text)
                    }
                }
                seg.bold && seg.italic -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(seg.text)
                    }
                }
                seg.bold -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(seg.text)
                    }
                }
                seg.italic -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(seg.text)
                    }
                }
                else -> append(seg.text)
            }
        }
    }
}

// ── Block Composables ─────────────────────────────────────────────────────────

@Composable
private fun InlineMarkdownParagraph(text: String, onAnimeClick: (String) -> Unit) {
    val annotated = remember(text) { buildInlineAnnotatedString(text) }
    ClickableText(
        text = annotated,
        style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { a ->
                handleLinkClick(a.item, onAnimeClick)
            }
        },
    )
}

@Composable
private fun HeaderBlock(text: String, level: Int, onAnimeClick: (String) -> Unit) {
    val fontSize = when (level) { 1 -> 20.sp; 2 -> 17.sp; else -> 15.sp }
    val annotated = remember(text) { buildInlineAnnotatedString(text) }
    ClickableText(
        text = annotated,
        style = TextStyle(
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            lineHeight = fontSize * 1.3,
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { a ->
                handleLinkClick(a.item, onAnimeClick)
            }
        },
    )
}

@Composable
private fun BulletListBlock(items: List<String>, onAnimeClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (item in items) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "•",
                    color = Color(0xFF9B59B6),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                val annotated = remember(item) { buildInlineAnnotatedString(item) }
                ClickableText(
                    text = annotated,
                    style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
                    modifier = Modifier.weight(1f),
                    onClick = { offset ->
                        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { a ->
                            handleLinkClick(a.item, onAnimeClick)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OrderedListBlock(items: List<String>, onAnimeClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((index, item) in items.withIndex()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${index + 1}.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(end = 8.dp).width(24.dp),
                )
                val annotated = remember(item) { buildInlineAnnotatedString(item) }
                ClickableText(
                    text = annotated,
                    style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp),
                    modifier = Modifier.weight(1f),
                    onClick = { offset ->
                        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { a ->
                            handleLinkClick(a.item, onAnimeClick)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BlockquoteBlock(text: String, onAnimeClick: (String) -> Unit) {
    val annotated = remember(text) { buildInlineAnnotatedString(text) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BlockquoteBg)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(24.dp)
                .background(BlockquoteBar, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        ClickableText(
            text = annotated,
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, fontStyle = FontStyle.Italic),
            modifier = Modifier.weight(1f),
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { a ->
                    handleLinkClick(a.item, onAnimeClick)
                }
            },
        )
    }
}

@Composable
private fun CodeBlockBlock(code: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBg)
            .padding(12.dp),
    ) {
        Text(
            text = code,
            color = Color(0xFFFFD166),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun ImageBlock(url: String, alt: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = alt,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

// ── Link Handler ──────────────────────────────────────────────────────────────

private fun handleLinkClick(url: String, onAnimeClick: (String) -> Unit) {
    val animeMatch = Regex("^anisurge://anime/(\\d+)").find(url)
    if (animeMatch != null) {
        onAnimeClick(animeMatch.groupValues[1])
    }
}
