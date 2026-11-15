package com.namunkiro.ui.v2

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.namunkiro.ui.preview.PreviewFixtures
import com.namunkiro.ui.state.*
import com.namunkiro.ui.theme.NamunkiroTheme

@Composable fun DemoChip(state: AppUiState, actions: AppActions) {
    val person = state.demoPersonaName.orEmpty()
    if (state.demoMode) Button(onClick = actions::exitDemo, modifier = Modifier.heightIn(min = 76.dp), shape = RoundedCornerShape(14.dp)) {
        Text("[모의] ${if (person.isEmpty()) "" else "$person "}예시 보는 중 · 내 데이터로", fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Preview(widthDp = 840)
@Composable private fun DemoChipPreview() { NamunkiroTheme { DemoChip(PreviewFixtures.home, NoopActions) } }
