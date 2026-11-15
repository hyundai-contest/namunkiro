package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme

@Composable fun DrivingLockV2(state: AppUiState, actions: AppActions) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            V2Title("주행 중에는 화면을 쉬어 갑니다")
            V2Caption("정차하면 보던 화면에서 이어집니다")
        }
    }
}

@Preview(widthDp = 840, heightDp = 1210)
@Composable private fun DrivingLockPreview() { NamunkiroTheme { DrivingLockV2(PreviewFixtures.home.copy(gate = Gate.DRIVING_LOCK), NoopActions) } }
