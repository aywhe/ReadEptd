package com.example.readeptd.parser

import kotlin.math.ceil

/**
 * ✅ 文本块数据类
 * 
 * 表示文档中的一个分页单元，包含起始和结束位置信息。
 * content 字段是可选的，可以根据配置决定是否存储实际文本内容，
 * 以节省内存（因为可以从全文中按需截取）。
 *
 * @param index 页码索引（从 0 开始）
 * @param startPos 起始字符偏移量（从文档开头计算）
 * @param endPos 结束字符偏移量（不包含该位置的字符）
 * @param content 页面文本内容（可选，默认为 null）
 */
data class TextChunk(
    val index: Int,
    val startPos: Long,
    val endPos: Long,
    val content: String? = null  // ✅ 可选的 content 字段，默认不包含
)

/**
 * ✅ 文本分页器
 * 
 * 负责将文本按行分割成适合显示的页面。支持四种分页模式：
 * 1. **ByLayoutSize**: 根据布局尺寸分页（avgCharsPerLine + maxLinesPerPage），长行会截断
 * 2. **ByLinesCount**: 根据行数分页（maxLinesPerPage + minLineCount），行内不截断
 * 3. **ByCharsCount**: 根据字符数分页（minChunkSize）
 * 4. **SingleLine**: 每行作为一页
 * 
 * 使用流程：
 * ```kotlin
 * // ByLayoutSize 模式（长行会截断）
 * val splitter = TextSplitter(avgCharsPerLine=30, maxLinesPerPage=20) { chunk ->
 *     pages.add(chunk)
 * }
 * 
 * // ByLinesCount 模式（行内不截断，达到 minLineCount 行才输出）
 * val splitter = TextSplitter(avgCharsPerLine=30, maxLinesPerPage=20, minLineCount=20) { chunk ->
 *     pages.add(chunk)
 * }
 * 
 * // ByCharsCount 模式（按字符数分页）
 * val splitter = TextSplitter(minChunkSize=512) { chunk ->
 *     pages.add(chunk)
 * }
 * 
 * // SingleLine 模式（每行一页）
 * val splitter = TextSplitter() { chunk ->
 *     pages.add(chunk)
 * }
 * 
 * splitter.processFullText(fullText)
 * splitter.flushRemaining()
 * ```
 *
 * @param avgCharsPerLine 每行平均字符数（用于计算显示行数，0 表示不使用）
 * @param maxLinesPerPage 每页最大行数（用于 ByLayoutSize 和 ByLinesCount 模式，0 表示不使用）
 * @param minLineCount 最小行数阈值（>0 时启用 ByLinesCount 模式，达到此行数才输出页面；=0 时使用 ByLayoutSize 模式）
 * @param minChunkSize 最小分块字符数（用于 ByCharsCount 模式，0 表示不使用）
 * @param includeContent 是否在 TextChunk 中包含 content 字段（默认 false，节省内存）
 * @param emitCallback 分页完成后的回调函数，接收生成的 TextChunk
 */
class TextSplitter(
    private val avgCharsPerLine: Int = 0,
    private val maxLinesPerPage: Int = 0,
    private val minLineCount: Int = 0,
    private val minChunkSize: Int = 0,
    private val includeContent: Boolean = false,  // ✅ 控制 TextChunk 是否包含 content
    private val emitCallback: suspend (TextChunk) -> Unit
) {
    // ✅ 当前分页状态
    private var index = 0  // 当前页码索引
    private var currentContent = StringBuilder()  // 当前页面的文本内容
    private var currentLines = 0  // 当前页面已添加的行数
    private var currentPosition: Long = 0  // 当前在全文中的字符位置

    init {
        // 预留初始化逻辑
    }
    
    // ✅ 预计算的分页大小（仅用于 ByLayoutSize 模式）
    private val chunkSize = avgCharsPerLine * maxLinesPerPage

    /**
     * ✅ 处理单行文本（自动选择分页模式）
     * 
     * 根据构造函数参数自动选择合适的分页策略：
     * - 如果 avgCharsPerLine > 0 且 maxLinesPerPage > 0：
     *   - 如果 minLineCount > 0 → ByLinesCount（行内不截断，达到 minLineCount 行才输出）
     *   - 否则 → ByLayoutSize（长行会截断到多页）
     * - 如果仅 minChunkSize > 0 → ByCharsCount
     * - 否则 → SingleLine
     *
     * @param line 要处理的文本行（不包含换行符）
     */
    suspend fun processLine(line: String) {
        if(avgCharsPerLine > 0 && maxLinesPerPage > 0){
            if(minLineCount > 0){
                processLineByLineCount(line)
            } else {
                processLineByPage(line)
            }
        } else if(minChunkSize > 0){
            processLineByCharCount(line)
        } else {
            processLineBySingleLine(line)
        }
    }

    /**
     * ✅ SingleLine 模式：每行作为一页
     * 
     * 将当前行添加到页面后立即输出，不进行合并。
     * 适用于需要逐行显示的场景。
     *
     * @param line 要处理的文本行
     */
    suspend fun processLineBySingleLine(line: String) {
        appendLineToCurrentPage(line)
        flushCurrentPage()
    }

    /**
     * ✅ ByLinesCount 模式：按行数分页（行内不截断）
     * 
     * 将行添加到当前页面，当行数达到 minLineCount 时输出页面。
     * 与 ByLayoutSize 的区别：
     * - ByLinesCount：不考虑每行的字符数，即使某行很长也不会截断，保证整行完整性
     * - ByLayoutSize：会根据 avgCharsPerLine 计算显示行数，超长行会被截断到多页
     * 
     * 适用场景：滚动阅读模式，希望保持段落完整性，避免在行中间断开。
     *
     * @param line 要处理的文本行
     */
    suspend fun processLineByLineCount(line: String) {
        appendLineToCurrentPage(line)
        if(currentLines >= minLineCount){
            flushCurrentPage()
        }
    }

    /**
     * ✅ ByCharsCount 模式：按字符数分页
     * 
     * 将行添加到当前页面，当字符数达到 minChunkSize 时输出页面。
     * 不考虑行数，只关注总字符数。
     *
     * @param line 要处理的文本行
     */
    suspend fun processLineByCharCount(line: String) {
        appendLineToCurrentPage(line)
        if(currentContent.length >= minChunkSize){
            flushCurrentPage()
        }
    }

    /**
     * ✅ ByLayoutSize 模式：根据布局尺寸分页
     * 
     * 综合考虑每行字符数和每页行数，模拟真实的排版效果。
     * 处理逻辑：
     * 1. 计算当前行需要的行数
     * 2. 如果能放入当前页 → 直接添加
     * 3. 如果不能放入：
     *    - 先输出当前页（避免总是填满整页）
     *    - 如果当前行能放入空页 → 添加到新页
     *    - 如果当前行太长 → 拆分成多个完整页
     *
     * @param line 要处理的文本行
     */
    suspend fun processLineByPage(line: String) {

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

    /**
     * ✅ 输出剩余内容
     * 
     * 在处理完所有行后调用，确保最后一页被输出。
     * 如果当前页面为空但已有其他页，输出一个换行符作为占位。
     */
    suspend fun flushRemaining() {
        var text = currentContent.toString()
        if (text.isEmpty() && index > 0) {
            text = "\n"
        }
        if(text.isNotEmpty()) {
            autoEmit(text)
        }
    }

    /**
     * ✅ 自动输出页面
     * 
     * 创建 TextChunk 并通过回调发送出去。
     * 根据 includeContent 配置决定是否包含文本内容。
     *
     * @param pageText 页面文本内容
     */
    private suspend fun autoEmit(pageText: String) {
        val startPos = currentPosition
        val endPos = startPos + pageText.length
        currentPosition = endPos
        emitCallback(
            TextChunk(
                index = getCurrentIndex(),
                startPos = startPos,
                endPos = endPos,
                content = if (includeContent) pageText else null  // ✅ 根据配置决定是否包含 content
            )
        )
        incrementIndex()
    }

    /**
     * ✅ 计算文本行需要的显示行数
     * 
     * 根据 avgCharsPerLine 计算一行文本在屏幕上需要多少行来显示。
     * 空行也算作 1 行。
     *
     * @param line 文本行
     * @return 需要的显示行数
     */
    private fun calculateLinesNeeded(line: String): Int {
        return if (line.isEmpty()) {
            1
        } else {
            ceil(line.length.toDouble() / avgCharsPerLine).toInt()
        }
    }

    /**
     * ✅ 输出当前页面
     * 
     * 将 currentContent 的内容通过 autoEmit 输出，然后清空状态。
     * 只在内容非空时执行。
     */
    private suspend fun flushCurrentPage() {
        if (currentContent.isNotEmpty()) {
            val content = currentContent.toString()
            autoEmit(content)
            currentContent.clear()
            currentLines = 0
        }
    }

    /**
     * ✅ 填充完整页直到处理完超长行
     * 
     * 当单行文本超过一页容量时，将其拆分成多个完整页。
     * 每次提取 chunkSize 个字符作为一个页面，剩余部分添加到当前页。
     *
     * @param line 超长文本行
     */
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

    /**
     * ✅ 将行添加到当前页面
     * 
     * 追加行内容和换行符，并更新行数统计。
     *
     * @param line 要添加的文本行
     */
    private fun appendLineToCurrentPage(line: String) {
        currentContent.append(line).append('\n')
        currentLines += calculateLinesNeeded(line)
    }

    /**
     * ✅ 获取当前页码索引
     * 
     * @return 当前页码（从 0 开始）
     */
    fun getCurrentIndex(): Int {
        //return if (index > 0) index + 1 else 0
        return index
    }

    /**
     * ✅ 递增页码索引
     */
    private fun incrementIndex() {
        index++
    }

    /**
     * ✅ 获取剩余未输出的内容
     * 
     * @return 当前页面的文本内容
     */
    fun getRemainContent(): String {
        return currentContent.toString()
    }
    /**
     * ✅ 直接处理完整文本（用于基于全文的分页）
     * 
     * 将完整文本按换行符分割成行，然后逐行处理。
     * 这是推荐的入口方法，比逐行调用 processLine 更方便。
     *
     * @param fullText 完整文本内容
     */
    suspend fun processFullText(fullText: String) {
        val lines = fullText.split("\n")
        for (line in lines) {
            processLine(line)
        }
    }
}