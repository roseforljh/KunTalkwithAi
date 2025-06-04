package com.example.everytalk.data.DataClass

import com.example.everytalk.model.SelectedMediaItem
import kotlinx.serialization.Serializable

import java.util.UUID


@Serializable
enum class Sender {
    User,
    AI,
    System,
    Tool
}

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    val sender: Sender,


    var reasoning: String? = null,


    var contentStarted: Boolean = false,
    var isError: Boolean = false,


    val name: String? = null,


    val timestamp: Long = System.currentTimeMillis(),
    val isPlaceholderName: Boolean = false,


    val webSearchResults: List<WebSearchResult>? = null,

    var currentWebSearchStage: String? = null,
    val htmlContent: String? = null,


    val imageUrls: List<String>? = null,


    val attachments: List<SelectedMediaItem>? = null
)