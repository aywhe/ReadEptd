package com.example.readeptd.bookmark

import com.example.readeptd.dao.BookmarkEntity
import org.json.JSONObject

object BookmarkMapper {
    private const val KEY_MIMETYPE_EPUB = "application/epub+zip"
    private const val KEY_MIMETYPE_PDF = "application/pdf"
    private const val KEY_MIMETYPE_TXT = "text/plain"
    private const val KEY_EPUB_CFI = "key_epub_cfi"
    private const val KEY_EPUB_LOC = "key_epub_loc"
    private const val KEY_PDF_PAGE = "key_pdf_page"
    private const val KEY_TXT_START_POS = "key_txt_start_pos"
    private const val KEY_TXT_END_POS = "key_txt_end_pos"

    fun dataToEntity(data: BookmarkData): BookmarkEntity {
        val (type, pos) = when (data) {
            is BookmarkData.Epub -> KEY_MIMETYPE_EPUB to JSONObject().apply {
                put(KEY_EPUB_CFI, data.cfi)
                put(KEY_EPUB_LOC, data.loc)
            }.toString()

            is BookmarkData.Pdf -> KEY_MIMETYPE_PDF to JSONObject().apply {
                put(KEY_PDF_PAGE, data.pageNumber)
            }.toString()

            is BookmarkData.Txt -> KEY_MIMETYPE_TXT to JSONObject().apply {
                put(KEY_TXT_START_POS, data.startPos)
                put(KEY_TXT_END_POS, data.endPos)
            }.toString()
        }
        return BookmarkEntity(
            id = data.id,
            bookId = data.bookId,
            note = data.note,
            createdTime = data.createdTime,
            mimeType = type,
            position = pos
        )
    }

    fun entityToData(entity: BookmarkEntity): BookmarkData {
        val jsonPos = JSONObject(entity.position)
        return when (entity.mimeType) {
            KEY_MIMETYPE_EPUB -> BookmarkData.Epub(
                id = entity.id,
                bookId = entity.bookId,
                note = entity.note,
                createdTime = entity.createdTime,
                cfi = jsonPos.getString(KEY_EPUB_CFI),
                loc = jsonPos.getLong(KEY_EPUB_LOC)
            )
            KEY_MIMETYPE_PDF -> BookmarkData.Pdf(
                id = entity.id,
                bookId = entity.bookId,
                note = entity.note,
                createdTime = entity.createdTime,
                pageNumber = jsonPos.getInt(KEY_PDF_PAGE)
            )
            KEY_MIMETYPE_TXT -> BookmarkData.Txt(
                id = entity.id,
                bookId = entity.bookId,
                note = entity.note,
                createdTime = entity.createdTime,
                startPos = jsonPos.getLong(KEY_TXT_START_POS),
                endPos = jsonPos.getLong(KEY_TXT_END_POS)
            )
            else -> throw IllegalArgumentException("Unknown bookmark type: ${entity.mimeType}")
        }
    }
}