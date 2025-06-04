package com.example.everytalk.ui.screens.MainScreen.drawer

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color




@Composable
internal fun DeleteConfirmationDialog(
    showDialog: Boolean,
    selectedItemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (selectedItemCount > 1) "确定删除所有所选项？" else if (selectedItemCount == 1) "确定删除所选项？" else "确定删除此项？") },

            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)
                ) { Text("取消") }
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black
        )
    }
}




@Composable
internal fun ClearAllConfirmationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("确定清空所有聊天记录？") },
            text = { Text("此操作无法撤销，所有聊天记录将被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("确定清空") }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)
                ) { Text("取消") }
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black
        )
    }
}