package com.example.everytalk.ui.screens.MainScreen.drawer

import androidx.compose.ui.geometry.Offset
import com.example.everytalk.data.DataClass.Message


internal sealed class CustomRippleState {
    object Idle : CustomRippleState()
    data class Animating(val pressPosition: Offset) : CustomRippleState()
}


internal data class FilteredConversationItem(
    val originalIndex: Int,
    val conversation: List<Message>,
)