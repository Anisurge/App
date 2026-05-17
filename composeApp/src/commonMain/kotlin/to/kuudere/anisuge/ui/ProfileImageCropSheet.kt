package to.kuudere.anisuge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import kotlin.math.min
import kotlin.math.roundToInt
import to.kuudere.anisuge.platform.ChatImagePick
import to.kuudere.anisuge.platform.CHAT_IMAGE_MAX_BYTES
import to.kuudere.anisuge.platform.cropSquareBitmap
import to.kuudere.anisuge.platform.decodeImageBitmap
import to.kuudere.anisuge.platform.encodeJpeg

@Composable
fun ProfileImageCropSheet(
    sourcePick: ChatImagePick,
    onConfirm: (ChatImagePick) -> Unit,
    onCancel: () -> Unit,
) {
    var bitmap by remember(sourcePick) {
        mutableStateOf(decodeImageBitmap(sourcePick.bytes))
    }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D)),
        ) {
            if (bitmap == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Could not load image", color = Color.White)
                    TextButton(onClick = onCancel) {
                        Text("Close", color = Color.White)
                    }
                }
            } else {
                ProfileImageCropContent(
                    bitmap = bitmap!!,
                    isSaving = isSaving,
                    onCancel = onCancel,
                    onConfirm = { cropped ->
                        isSaving = true
                        val jpeg = encodeJpeg(cropped, quality = 88)
                        isSaving = false
                        if (jpeg == null || jpeg.isEmpty()) {
                            onCancel()
                            return@ProfileImageCropContent
                        }
                        if (jpeg.size.toLong() > CHAT_IMAGE_MAX_BYTES) {
                            onCancel()
                            return@ProfileImageCropContent
                        }
                        onConfirm(
                            ChatImagePick(
                                bytes = jpeg,
                                mimeType = "image/jpeg",
                                fileName = sourcePick.fileName.substringBeforeLast('.')
                                    .ifBlank { "profile" } + ".jpg",
                                sizeBytes = jpeg.size.toLong(),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileImageCropContent(
    bitmap: ImageBitmap,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onConfirm: (ImageBitmap) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(GeometrySize.Zero) }

    val cropFraction = 0.82f

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Move and zoom to fit your photo in the circle",
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged {
                    viewportSize = GeometrySize(it.width.toFloat(), it.height.toFloat())
                },
        ) {
            val cropSizePx = min(constraints.maxWidth, constraints.maxHeight).toFloat() * cropFraction
            val cropLeft = (viewportSize.width - cropSizePx) / 2f
            val cropTop = (viewportSize.height - cropSizePx) / 2f
            val cropRect = Rect(cropLeft, cropTop, cropLeft + cropSizePx, cropTop + cropSizePx)

            val fitScale = if (viewportSize.width > 0f && viewportSize.height > 0f) {
                min(
                    viewportSize.width / bitmap.width.toFloat(),
                    viewportSize.height / bitmap.height.toFloat(),
                )
            } else {
                1f
            }
            val baseOffset = Offset(
                x = (viewportSize.width - bitmap.width * fitScale) / 2f,
                y = (viewportSize.height - bitmap.height * fitScale) / 2f,
            )

            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.None,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = baseOffset.x + offset.x
                        translationY = baseOffset.y + offset.y
                        scaleX = fitScale * scale
                        scaleY = fitScale * scale
                    }
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset += pan
                        }
                    },
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val dimPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addOval(cropRect)
                }
                drawPath(dimPath, Color.Black.copy(alpha = 0.55f))
                drawOval(
                    color = Color.White,
                    topLeft = cropRect.topLeft,
                    size = cropRect.size,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !isSaving) {
                Text("Cancel", color = Color.White)
            }
            Button(
                onClick = {
                    if (viewportSize.width <= 0f) return@Button
                    val cropSizePx = min(viewportSize.width, viewportSize.height) * cropFraction
                    val cropLeft = (viewportSize.width - cropSizePx) / 2f
                    val cropTop = (viewportSize.height - cropSizePx) / 2f
                    val fitScale = min(
                        viewportSize.width / bitmap.width.toFloat(),
                        viewportSize.height / bitmap.height.toFloat(),
                    )
                    val totalScale = fitScale * scale
                    val imageLeft = (viewportSize.width - bitmap.width * fitScale) / 2f + offset.x
                    val imageTop = (viewportSize.height - bitmap.height * fitScale) / 2f + offset.y

                    val srcLeft = ((cropLeft - imageLeft) / totalScale).roundToInt()
                        .coerceIn(0, bitmap.width - 1)
                    val srcTop = ((cropTop - imageTop) / totalScale).roundToInt()
                        .coerceIn(0, bitmap.height - 1)
                    val srcSize = (cropSizePx / totalScale).roundToInt()
                        .coerceIn(1, min(bitmap.width - srcLeft, bitmap.height - srcTop))

                    onConfirm(cropSquareBitmap(bitmap, srcLeft, srcTop, srcSize))
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Use photo", color = Color.Black)
                }
            }
        }
    }
}
