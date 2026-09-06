package nz.eloque.quits.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.QrLogoPadding
import io.github.alexzhirkevich.qrose.options.QrLogoShape
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.cd_invite_qr
import nz.eloque.quits.resources.logo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The invite [link] as a scannable QR code.
 */
@Composable
fun InviteQr(
    link: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val logoPainter = painterResource(Res.drawable.logo)
    val qrPainter =
        rememberQrCodePainter(link) {
            // The center logo eats modules. A lower level here makes the code unscannable.
            errorCorrectionLevel = QrErrorCorrectionLevel.High
            colors {
                dark = QrBrush.solid(Color.Black)
                light = QrBrush.solid(Color.White)
            }
            logo {
                painter = logoPainter
                this.size = 0.18f
                padding = QrLogoPadding.Natural(0.1f)
                shape = QrLogoShape.roundCorners(0.25f)
            }
        }

    Surface(
        modifier = modifier,
        color = Color.White,
        shape = MaterialTheme.shapes.medium,
    ) {
        Image(
            painter = qrPainter,
            contentDescription = stringResource(Res.string.cd_invite_qr),
            modifier = Modifier.padding(12.dp).size(size),
        )
    }
}
