package top.abdl_space.app.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.abdl_space.app.core.designsystem.theme.V4Dimens

/** 基础卡片：surface 面 + 14dp 圆角（tokens §5）。 */
@Composable
fun V4Card(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(V4Dimens.cardRadius),
        color = color,
        contentColor = contentColor,
    ) {
        Column(content = content)
    }
}
