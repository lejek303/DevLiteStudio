package com.devlite.studio.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devlite.studio.model.Language

private val BRACKET_OPENERS = mapOf('(' to ')', '{' to '}', '[' to ']')

/**
 * A code-focused text editor: a line-number gutter synced to scroll
 * position, regex-based syntax highlighting applied via
 * VisualTransformation (so native cursor/selection behavior is kept
 * intact), auto-indent on newline, and bracket/quote auto-closing.
 *
 * [tabId] must be stable per open file and change only when switching
 * tabs — it's used to key the internal cursor state so that typing
 * doesn't reset the caret to the end of the text on every keystroke.
 */
@Composable
fun CodeEditorView(
    tabId: String,
    content: String,
    language: Language,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fieldValue by remember(tabId) {
        mutableStateOf(TextFieldValue(content, TextRange(content.length)))
    }
    val lineCount = remember(fieldValue.text) { fieldValue.text.count { it == '\n' } + 1 }
    val scrollState = rememberScrollState()

    Row(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        LineNumberGutter(lineCount = lineCount, scrollState = scrollState)

        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val transformed = applyAutoIndentAndBrackets(fieldValue, newValue)
                fieldValue = transformed
                onContentChange(transformed.text)
            },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 8.dp, end = 12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFFEEFFFF)
            ),
            cursorBrush = SolidColor(Color(0xFF89DDFF)),
            visualTransformation = { text ->
                TransformedText(SyntaxHighlighter.highlight(text.text, language), OffsetMapping.Identity)
            }
        )
    }
}

@Composable
private fun LineNumberGutter(lineCount: Int, scrollState: ScrollState) {
    Column(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .verticalScroll(scrollState, enabled = false)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(end = 6.dp)
    ) {
        for (line in 1..lineCount) {
            Text(
                text = line.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFF546E7A),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Handles two editor conveniences on every keystroke:
 *  - Auto-indent: pressing Enter carries the previous line's leading
 *    whitespace forward, plus one extra indent level if that line
 *    ended with an opening bracket.
 *  - Bracket/quote auto-closing: typing an opener inserts its match
 *    and leaves the cursor between the pair.
 * Anything that isn't a single-character insert (pastes, selections,
 * deletions) passes through untouched.
 */
private fun applyAutoIndentAndBrackets(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    val isSingleCharInsert = new.text.length == old.text.length + 1 &&
        new.selection.collapsed && old.selection.collapsed

    if (!isSingleCharInsert) return new

    val insertPos = new.selection.start - 1
    if (insertPos < 0) return new
    val insertedChar = new.text.getOrNull(insertPos) ?: return new

    if (insertedChar == '\n') {
        val lineStart = new.text.lastIndexOf('\n', insertPos - 1).let { if (it == -1) 0 else it + 1 }
        val previousLine = new.text.substring(lineStart, insertPos)
        val leadingWhitespace = previousLine.takeWhile { it == ' ' || it == '\t' }
        val extraIndent = if (previousLine.trimEnd().lastOrNull() in listOf('{', '(', '[')) "    " else ""
        val indent = leadingWhitespace + extraIndent
        if (indent.isEmpty()) return new

        val newText = new.text.substring(0, insertPos + 1) + indent + new.text.substring(insertPos + 1)
        return TextFieldValue(newText, TextRange(insertPos + 1 + indent.length))
    }

    BRACKET_OPENERS[insertedChar]?.let { closer ->
        val newText = new.text.substring(0, insertPos + 1) + closer + new.text.substring(insertPos + 1)
        return TextFieldValue(newText, TextRange(insertPos + 1))
    }

    if (insertedChar == '"' || insertedChar == '\'') {
        val nextChar = new.text.getOrNull(insertPos + 1)
        if (nextChar != insertedChar) {
            val newText = new.text.substring(0, insertPos + 1) + insertedChar + new.text.substring(insertPos + 1)
            return TextFieldValue(newText, TextRange(insertPos + 1))
        }
    }

    return new
}
