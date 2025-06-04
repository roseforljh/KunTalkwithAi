package com.example.everytalk.ui.screens.MainScreen.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties




@Composable
internal fun ConversationItemMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    popupPositionProvider: PopupPositionProvider,
    isRenameEnabled: Boolean = true
) {
    if (expanded) {
        Popup(
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = false)
        ) {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(max = 120.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        vertical = 4.dp,
                        horizontal = 8.dp
                    )
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clickable(
                                enabled = isRenameEnabled,
                                onClick = {
                                    if (isRenameEnabled) {
                                        onRenameClick()
                                        onDismissRequest()
                                    }
                                },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DriveFileRenameOutline,
                            "重命名图标",
                            tint = if (isRenameEnabled) Color.Black else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "重命名",
                            color = if (isRenameEnabled) Color.Black else Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Divider(color = Color.LightGray.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clickable(
                                onClick = {
                                    onDeleteClick()
                                    onDismissRequest()
                                },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            "删除图标",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "删除",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}