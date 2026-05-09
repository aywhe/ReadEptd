package com.example.readeptd.parser

import kotlin.math.ceil

data class TextChunk(
    val content: String,
    val index: Int,
    val startPos: Long,
    val endPos: Long
)

/**
 * 文本分页器
 * 负责将文本行分割成适合显示的页面
 */
class TextSplitter(
    private val avgCharsPerLine: Int,
    private val maxLinesPerPage: Int,
    private val emitCallback: suspend (TextChunk) -> Unit
) {
    private var index = 0
    private var currentContent = StringBuilder()
    private var currentLines = 0
    private var currentPosition: Long = 0

    init {

    }
    private val chunkSize = avgCharsPerLine * maxLinesPerPage

    suspend fun processLine(line: String) {

        val linesNeeded = calculateLinesNeeded(line)

        if(currentLines + linesNeeded <= maxLinesPerPage) {
            appendLineToCurrentPage(line)
        }
        else {
            if(currentContent.isNotEmpty()){
                // 输出当前页面，避免总是填满整页
                flushCurrentPage()
            }
            if (linesNeeded <= maxLinesPerPage) {
                appendLineToCurrentPage(line)
            }
            else {
                fillPagesUntil(line)
            }
        }
    }

    suspend fun flushRemaining() {
        var text = currentContent.toString()
        if (text.isEmpty() && index > 0) {
            text = "\n"
        }
        if(text.isNotEmpty()) {
            autoEmit(text)
        }
    }

    private suspend fun autoEmit(pageText: String) {
        val startPos = currentPosition
        val endPos = startPos + pageText.length
        currentPosition = endPos
        emitCallback(
            TextChunk(
                content = pageText,
                index = getCurrentIndex(),
                startPos = startPos,
                endPos =endPos
            )
        )
        incrementIndex()
    }

    private fun calculateLinesNeeded(line: String): Int {
        return if (line.isEmpty()) {
            1
        } else {
            ceil(line.length.toDouble() / avgCharsPerLine).toInt()
        }
    }

    private suspend fun flushCurrentPage() {
        if (currentContent.isNotEmpty()) {
            val content = currentContent.toString()
            autoEmit(content)
            currentContent.clear()
            currentLines = 0
        }
    }

    private suspend fun fillPagesUntil(line: String) {
        val lineLength = line.length
        var startIndex = 0

        // 处理完整的页块
        while (startIndex + chunkSize <= lineLength) {
            val endIndex = startIndex + chunkSize
            val pageTextBuilder = StringBuilder(chunkSize + 1)
            pageTextBuilder.append(line, startIndex, endIndex)
            val pageText = pageTextBuilder.toString()
            autoEmit(pageText)
            startIndex = endIndex
        }

        // 处理剩余部分
        if (startIndex < lineLength) {
            val remainingText = line.substring(startIndex)
            appendLineToCurrentPage(remainingText)
        }
    }

    private fun appendLineToCurrentPage(line: String) {
        currentContent.append(line).append('\n')
        currentLines += calculateLinesNeeded(line)
    }

    fun getCurrentIndex(): Int {
        //return if (index > 0) index + 1 else 0
        return index
    }

    private fun incrementIndex() {
        index++
    }

    fun getRemainContent(): String {
        return currentContent.toString()
    }
}