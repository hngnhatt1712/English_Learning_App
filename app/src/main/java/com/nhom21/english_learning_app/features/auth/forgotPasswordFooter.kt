package com.nhom21.english_learning_app.features.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.ui.theme.Baloo2
import com.nhom21.english_learning_app.ui.theme.Quicksand

/**
 * Component Footer chân trang dùng chung cho luồng Quên mật khẩu.
 * Hiển thị Mascot Owla cổ vũ kèm bong bóng lời nhắn và nét gạch chân trang trí.
 */
@Composable
fun ForgotPasswordFooter(
    modifier: Modifier = Modifier
) {
    val deepTealColor = Color(0xFF036268)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_owla),
            contentDescription = "Owla hỗ trợ",
            modifier = Modifier.size(92.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Text(
                text = "Đừng lo!",
                fontFamily = Baloo2,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = deepTealColor
            )

            Text(
                text = "Chúng tôi sẽ giúp bạn\nlấy lại mật khẩu nhé!",
                fontFamily = Quicksand,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = deepTealColor,
                lineHeight = 18.sp
            )

            // Đường nét vẽ cong gạch chân trang trí
            Canvas(
                modifier = Modifier
                    .width(110.dp)
                    .height(6.dp)
                    .padding(top = 2.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.5f)
                    quadraticTo(
                        size.width * 0.5f, size.height,
                        size.width, size.height * 0.2f
                    )
                }
                drawPath(
                    path = path,
                    color = deepTealColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
