package com.orangeisland.app.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Transient preview state for the in-chat PDF page viewer and text-file viewer.
 *
 * Extracted from [ChatViewModel] so the preview lifecycle (open / replace / clear)
 * is a single self-contained responsibility, independent of chat and generation
 * concerns. The two viewers are mutually exclusive in the UI but tracked separately
 * so each can be cleared without disturbing the other's last-shown content.
 */
class MediaPreviewState {
    private val _pdfPages = MutableStateFlow<List<String>>(emptyList())
    val pdfPages: StateFlow<List<String>> = _pdfPages.asStateFlow()
    private val _pdfIndex = MutableStateFlow(0)
    val pdfIndex: StateFlow<Int> = _pdfIndex.asStateFlow()

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent.asStateFlow()
    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    fun showPdf(pages: List<String>, startIndex: Int) {
        _pdfPages.value = pages
        _pdfIndex.value = startIndex
    }

    fun showFile(fileName: String, content: String) {
        _fileName.value = fileName
        _fileContent.value = content
    }

    fun clear() {
        _pdfPages.value = emptyList()
        _fileContent.value = null
    }
}
