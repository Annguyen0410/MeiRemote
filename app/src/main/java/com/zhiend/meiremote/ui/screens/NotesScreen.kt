package com.zhiend.meiremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhiend.meiremote.data.AppState
import com.zhiend.meiremote.ui.components.MeiPageHeader
import com.zhiend.meiremote.ui.components.QuickCapturePanel

@Composable
fun NotesScreen(state: AppState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeiPageHeader("Gửi nhanh", "Ghi chú, nhiệm vụ hay liên kết — gửi thẳng lên desktop")
        QuickCapturePanel(state)
    }
}
