package io.github.vkeiwon.junitgen.core

/** 들여쓰기를 관리하는 간단한 코드 작성기. 줄 단위로 쌓았다가 [render] 에서 들여쓰기를 적용한다. */
class CodeWriter {
    private val lines = mutableListOf<Pair<Int, String>>()
    private var depth = 0

    fun line(text: String) {
        lines += depth to text
    }

    fun blank() {
        lines += 0 to ""
    }

    fun block(header: String, body: CodeWriter.() -> Unit) {
        line("$header {")
        depth++
        body()
        depth--
        line("}")
    }

    /** 다른 작성기의 줄들을 현재 들여쓰기 아래에 덧붙인다. */
    fun append(other: CodeWriter) {
        other.lines.forEach { (d, text) -> lines += (if (text.isEmpty()) 0 else depth + d) to text }
    }

    fun render(indent: Int = 0, indentUnit: String = "    "): String = buildString {
        lines.forEach { (d, text) ->
            if (text.isNotEmpty()) append(indentUnit.repeat(indent + d)).append(text)
            append('\n')
        }
    }
}
