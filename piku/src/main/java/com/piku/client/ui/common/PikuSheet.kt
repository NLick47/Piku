package com.piku.client.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.ui.theme.MenuPopupBgDark
import com.piku.client.ui.theme.MenuPopupBgLight
import com.piku.client.ui.theme.PikuColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PikuBottomSheet(
    onDismissRequest: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        properties = properties,
        containerColor = if (dark) MenuPopupBgDark else MenuPopupBgLight,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        modifier = modifier,
    ) {
        val scrollModifier = if (scrollable) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, top = 6.dp)
                .then(scrollModifier),
        ) {
            PikuSheetHandle()
            content()
        }
    }
}

@Composable
fun PikuSheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PikuColors.textSecondary.copy(alpha = 0.3f)),
        )
    }
}

@Composable
fun PikuSheetTitle(text: String) {
    Text(
        text = text,
        color = PikuColors.textPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun PikuSheetSubtitle(text: String) {
    Text(
        text = text,
        color = PikuColors.textSecondary,
        fontSize = 12.sp,
    )
}
