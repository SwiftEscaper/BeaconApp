package koren.swiftescaper.ui.view.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import koren.swiftescaper.domain.viewmodel.MainViewModel
import koren.swiftescaper.ui.theme.BlueGray

@Composable
fun GridScreen(viewModel: MainViewModel) {
    // brightness 값을 collect하여 UI에 적용
    val brightnessList by viewModel.brightness.collectAsState()
        Column {
            for (index in 0 until 10) {
                val brightness = if (index < brightnessList.size) brightnessList.get(index).brightness else 0
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(BlueGray.copy(alpha = brightness / 255f))
                        .padding(1.dp)
                )
            }
        }
    }