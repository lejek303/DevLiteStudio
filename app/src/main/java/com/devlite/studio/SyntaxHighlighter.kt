package com.devlite.studio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.devlite.studio.model.Language

/**
 * Single-pass, regex-based syntax highlighter. This intentionally
 * trades off against a full tree-sitter/AST integration — which would
 * need NDK-compiled grammars per language and is its own separate
 * project — in favor of something dependency-free that's fast enough
 * for a mobile editor and covers the common cases: keywords, strings,
 * numbers, and line comments.
 */
object SyntaxHighlighter {

    private val keywordsByLanguage: Map<Language, Set<String>> = mapOf(
        Language.PYTHON to setOf(
            "def", "class", "import", "from", "if", "elif", "else", "for", "while",
            "return", "try", "except", "with", "as", "lambda", "None", "True", "False", "self"
        ),
        Language.JAVASCRIPT to setOf(
            "function", "const", "let", "var", "if", "else", "for", "while", "return",
            "class", "import", "export", "async", "await", "true", "false", "null", "undefined"
        ),
        Language.TYPESCRIPT to setOf(
            "function", "const", "let", "var", "if", "else", "for", "while", "return",
            "class", "import", "export", "async", "await", "interface", "type", "true", "false", "null"
        ),
        Language.C to setOf(
            "int", "char", "float", "double", "void", "if", "else", "for", "while",
            "return", "struct", "typedef", "const", "static"
        ),
        Language.CPP to setOf(
            "int", "char", "float", "double", "void", "if", "else", "for", "while", "return",
            "class", "struct", "public", "private", "namespace", "template", "const", "static"
        ),
        Language.RUST to setOf(
            "fn", "let", "mut", "if", "else", "for", "while", "loop", "return", "struct",
            "enum", "impl", "trait", "match", "pub", "use", "true", "false"
        ),
        Language.GO to setOf(
            "func", "package", "import", "if", "else", "for", "return", "var", "const",
            "struct", "interface", "go", "chan", "defer", "true", "false"
        ),
        Language.JAVA to setOf(
            "public", "private", "protected", "class", "interface", "if", "else", "for",
            "while", "return", "new", "static", "final", "void", "import", "package"
        ),
        Language.KOTLIN to setOf(
            "fun", "val", "var", "if", "else", "for", "while", "return", "class", "object",
            "interface", "when", "is", "in", "import", "package", "true", "false", "null"
        )
    )

    private val KEYWORD_COLOR = Color(0xFFC792EA)
    private val STRING_COLOR = Color(0xFFC3E88D)
    private val COMMENT_COLOR = Color(0xFF676E95)
    private val NUMBER_COLOR = Color(0xFFF78C6C)

    private val tokenRegex = Regex(
        """(?<comment>//[^\n]*|#[^\n]*)|(?<string>"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|(?<number>\b\d+(?:\.\d+)?\b)|(?<word>[A-Za-z_][A-Za-z0-9_]*)"""
    )

    fun highlight(text: String, language: Language): AnnotatedString {
        val keywords = keywordsByLanguage[language] ?: emptySet()

        return AnnotatedString.Builder().apply {
            var lastEnd = 0
            for (match in tokenRegex.findAll(text)) {
                if (match.range.first > lastEnd) {
                    append(text.substring(lastEnd, match.range.first))
                }
                val groups = match.groups as MatchNamedGroupCollection
                when {
                    groups["comment"] != null -> styled(match.value, COMMENT_COLOR)
                    groups["string"] != null -> styled(match.value, STRING_COLOR)
                    groups["number"] != null -> styled(match.value, NUMBER_COLOR)
                    groups["word"] != null && match.value in keywords -> styled(match.value, KEYWORD_COLOR, bold = true)
                    else -> append(match.value)
                }
                lastEnd = match.range.last + 1
            }
            if (lastEnd < text.length) append(text.substring(lastEnd))
        }.toAnnotatedString()
    }

    private fun AnnotatedString.Builder.styled(value: String, color: Color, bold: Boolean = false) {
        withStyle(SpanStyle(color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)) {
            append(value)
        }
    }
}
