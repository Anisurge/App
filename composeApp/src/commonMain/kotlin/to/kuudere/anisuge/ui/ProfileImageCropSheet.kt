package to.kuudere.anisuge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import to.kuudere.anisuge.platform.ChatImagePick
import to.kuudere.anisuge.platform.CHAT_IMAGE_MAX_BYTES
import to.kuudere.anisuge.platform.cropSquareBitmap
import to.kuudere.anisuge.platform.decodeImageBitmap
import to.kuudere.anisuge.platform.encodeJpeg

private const val MIN_CROP_PX = 96f
private const val INITIAL_CROP_FRACTION = 0.88f

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

private enum class CropHandle {
    None,
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

@Composable
private fun ProfileImageCropContent(
    bitmap: ImageBitmap,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onConfirm: (ImageBitmap) -> Unit,
) {
    var viewportSize by remember { mutableStateOf(GeometrySize.Zero) }
    var cropCenter by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var cropSize by remember(bitmap) { mutableFloatStateOf(0f) }
    var cropInitialized by remember(bitmap) { mutableStateOf(false) }
    var activeHandle by remember { mutableStateOf(CropHandle.None) }

    LaunchedEffect(viewportSize, bitmap) {
        if (cropInitialized || viewportSize.width <= 0f) return@LaunchedEffect
        val imageRect = fitImageRect(bitmap, viewportSize)
        val maxSquare = min(imageRect.width, imageRect.height) * INITIAL_CROP_FRACTION
        cropSize = maxSquare
        cropCenter = imageRect.center
        cropInitialized = true
    }

    val imageRect = if (viewportSize.width > 0f) {
        fitImageRect(bitmap, viewportSize)
    } else {
        Rect.Zero
    }
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 22.dp.toPx() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Drag the square to move. Drag a corner to resize. The circle shows your profile preview.",
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged {
                    viewportSize = GeometrySize(it.width.toFloat(), it.height.toFloat())
                },
        ) {
            if (viewportSize.width > 0f && cropInitialized) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bitmap) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    activeHandle = hitTestCropHandle(
                                        point = start,
                                        crop = cropRectFromCenter(cropCenter, cropSize),
                                        handleRadius = handleRadiusPx,
                                    )
                                },
                                onDrag = { change, dragAmount ->
                                    val handle = activeHandle
                                    if (handle == CropHandle.None) return@detectDragGestures
                                    val current = cropRectFromCenter(cropCenter, cropSize)
                                    val next = when (handle) {
                                        CropHandle.Move -> {
                                            clampCropSquare(
                                                center = cropCenter + dragAmount,
                                                size = cropSize,
                                                imageRect = imageRect,
                                            )
                                        }
                                        CropHandle.TopLeft -> resizeCropSquare(
                                            anchor = Offset(current.right, current.bottom),
                                            finger = change.position,
                                            imageRect = imageRect,
                                            growX = -1,
                                            growY = -1,
                                        )
                                        CropHandle.TopRight -> resizeCropSquare(
                                            anchor = Offset(current.left, current.bottom),
                                            finger = change.position,
                                            imageRect = imageRect,
                                            growX = 1,
                                            growY = -1,
                                        )
                                        CropHandle.BottomLeft -> resizeCropSquare(
                                            anchor = Offset(current.right, current.top),
                                            finger = change.position,
                                            imageRect = imageRect,
                                            growX = -1,
                                            growY = 1,
                                        )
                                        CropHandle.BottomRight -> resizeCropSquare(
                                            anchor = Offset(current.left, current.top),
                                            finger = change.position,
                                            imageRect = imageRect,
                                            growX = 1,
                                            growY = 1,
                                        )
                                        CropHandle.None -> return@detectDragGestures
                                    }
                                    cropCenter = next.first
                                    cropSize = next.second
                                    change.consume()
                                },
                                onDragEnd = { activeHandle = CropHandle.None },
                                onDragCancel = { activeHandle = CropHandle.None },
                            )
                        },
                ) {
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(
                            imageRect.left.roundToInt(),
                            imageRect.top.roundToInt(),
                        ),
                        dstSize = IntSize(
                            imageRect.width.roundToInt().coerceAtLeast(1),
                            imageRect.height.roundToInt().coerceAtLeast(1),
                        ),
                    )

                    val crop = cropRectFromCenter(cropCenter, cropSize)
                    val dimPath = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addRect(crop)
                    }
                    drawPath(dimPath, Color.Black.copy(alpha = 0.58f))

                    drawRect(
                        color = Color.White,
                        topLeft = crop.topLeft,
                        size = crop.size,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )

                    val guideInset = crop.width * 0.04f
                    val guideSize = crop.width - guideInset * 2f
                    val guideLeft = crop.left + guideInset
                    val guideTop = crop.top + guideInset
                    drawOval(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(guideLeft, guideTop),
                        size = androidx.compose.ui.geometry.Size(guideSize, guideSize),
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    val handleRadius = 10.dp.toPx()
                    listOf(
                        Offset(crop.left, crop.top),
                        Offset(crop.right, crop.top),
                        Offset(crop.left, crop.bottom),
                        Offset(crop.right, crop.bottom),
                    ).forEach { corner ->
                        drawCircle(
                            color = Color.White,
                            radius = handleRadius,
                            center = corner,
                        )
                        drawCircle(
                            color = Color(0xFF0D0D0D),
                            radius = handleRadius * 0.55f,
                            center = corner,
                        )
                    }
                }
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
                    if (!cropInitialized || viewportSize.width <= 0f) return@Button
                    val pixels = mapCropRectToBitmap(
                        bitmap = bitmap,
                        imageRect = imageRect,
                        cropRect = cropRectFromCenter(cropCenter, cropSize),
                    ) ?: return@Button
                    onConfirm(
                        cropSquareBitmap(
                            bitmap,
                            pixels.first,
                            pixels.second,
                            pixels.third,
                        ),
                    )
                },
                enabled = !isSaving && cropInitialized,
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

private fun fitImageRect(bitmap: ImageBitmap, viewport: GeometrySize): Rect {
    val scale = min(
        viewport.width / bitmap.width.toFloat(),
        viewport.height / bitmap.height.toFloat(),
    )
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    val left = (viewport.width - width) / 2f
    val top = (viewport.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun cropRectFromCenter(center: Offset, size: Float): Rect {
    val half = size / 2f
    return Rect(center.x - half, center.y - half, center.x + half, center.y + half)
}

private fun clampCropSquare(
    center: Offset,
    size: Float,
    imageRect: Rect,
): Pair<Offset, Float> {
    val maxSize = min(imageRect.width, imageRect.height)
    val clampedSize = size.coerceIn(MIN_CROP_PX, maxSize)
    val half = clampedSize / 2f
    val clampedCenter = Offset(
        x = center.x.coerceIn(imageRect.left + half, imageRect.right - half),
        y = center.y.coerceIn(imageRect.top + half, imageRect.bottom - half),
    )
    return clampedCenter to clampedSize
}

private fun resizeCropSquare(
    anchor: Offset,
    finger: Offset,
    imageRect: Rect,
    growX: Int,
    growY: Int,
): Pair<Offset, Float> {
    val dx = (finger.x - anchor.x) * growX
    val dy = (finger.y - anchor.y) * growY
    val rawSize = min(abs(dx), abs(dy))
    val maxSize = min(
        if (growX > 0) imageRect.right - anchor.x else anchor.x - imageRect.left,
        if (growY > 0) imageRect.bottom - anchor.y else anchor.y - imageRect.top,
    )
    val size = rawSize.coerceIn(MIN_CROP_PX, maxSize)
    val center = Offset(
        x = anchor.x + growX * size / 2f,
        y = anchor.y + growY * size / 2f,
    )
    return clampCropSquare(center, size, imageRect)
}

private fun hitTestCropHandle(
    point: Offset,
    crop: Rect,
    handleRadius: Float,
): CropHandle {
    val corners = listOf(
        CropHandle.TopLeft to Offset(crop.left, crop.top),
        CropHandle.TopRight to Offset(crop.right, crop.top),
        CropHandle.BottomLeft to Offset(crop.left, crop.bottom),
        CropHandle.BottomRight to Offset(crop.right, crop.bottom),
    )
    for ((handle, corner) in corners) {
        if ((point - corner).getDistance() <= handleRadius) return handle
    }
    return if (crop.contains(point)) CropHandle.Move else CropHandle.None
}

private fun mapCropRectToBitmap(
    bitmap: ImageBitmap,
    imageRect: Rect,
    cropRect: Rect,
): Triple<Int, Int, Int>? {
    if (imageRect.width <= 0f || imageRect.height <= 0f) return null
    val scale = imageRect.width / bitmap.width.toFloat()
    val srcLeft = ((cropRect.left - imageRect.left) / scale).roundToInt()
        .coerceIn(0, bitmap.width - 1)
    val srcTop = ((cropRect.top - imageRect.top) / scale).roundToInt()
        .coerceIn(0, bitmap.height - 1)
    val srcSize = (cropRect.width / scale).roundToInt()
        .coerceIn(1, min(bitmap.width - srcLeft, bitmap.height - srcTop))
    return Triple(srcLeft, srcTop, srcSize)
}
