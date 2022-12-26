import java.io.EOFException

class ParserContext constructor(private val input: String) {
    var pos = 0
    private val whitespace = arrayOf(' ', '\r', '\t', '\n')
    private fun readWhitespace() {
        while (pos < input.length) {
            if (input[pos] in whitespace) {
                pos += 1
            } else {
                break
            }
        }
        if (pos >= input.length) {
            throw EOFException()
        }
    }

    fun remaining(): String {
        return input.substring(pos)
    }

    fun nextToken(receiver: Regex): String {
        readWhitespace()
        val token = receiver.matchAt(input, pos) ?: throw EOFException("except $receiver at ${input.substring(pos)}")
        pos = token.range.last + 1
        if (token.groupValues.size > 1) {
            return token.groupValues[1]
        }
        return token.value
    }


    fun readNextChar(): Char {
        readWhitespace()
        return input[pos]
    }

    fun expectNextChar(c: Char): Boolean {
        readWhitespace()
        return if (input[pos] == c) {
            pos += 1
            true
        } else {
            false
        }
    }
}