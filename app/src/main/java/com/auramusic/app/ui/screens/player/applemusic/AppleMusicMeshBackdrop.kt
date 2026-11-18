package com.auramusic.app.ui.screens.player.applemusic

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.auramusic.app.ui.theme.isLyricsBlurSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

private const val MESH_GRID = 6
private const val MESH_TEX = 32
private const val MESH_SOURCE_PX = 120
private const val MESH_VIBRANCE = 1.12f
private const val MESH_FLOOR = 0.045f
private const val MESH_FADE_MS = 900
private val MESH_BLUR = 44.dp
private val BACKDROP_BLUR_RADIUS = 80.dp
private const val BACKDROP_TINT_ALPHA = 0.62f
private val MeshFallback = Color(0xFF121212)

private const val TOP_WASH_STEPS = 24
private const val TOP_WASH_MIN_ALPHA = 0.16f
private const val TOP_WASH_MAX_ALPHA = 0.65f
private const val TOP_WASH_FADE_MS = 900

/**
 * Mesh extracted from an album artwork:
 * - [image]: 32x32 smoothstep-resampled color grid
 * - [topLuminance]: Rec.709 luminance (0..1) of the artwork's top band before lifting
 * - [topEdge]: 32x1 top-band row held above the artwork's top edge
 */
@Immutable
class AppleMusicMesh(
    val image: ImageBitmap,
    val topLuminance: Float,
    val topEdge: ImageBitmap,
)

/**
 * Outer full-bleed frosted artwork backdrop with a 3-stop artwork-tinted gradient overlay.
 * Uses hardware RenderEffect blur (80dp) on API 31+ via [isLyricsBlurSupported], and a fast
 * software downscale box-blur fallback + rich 3-stop darkened seed gradient on API 26–30.
 */
@Composable
fun BoxScope.AppleMusicArtworkBackdrop(
    artworkUrl: String,
    seedColor: Color,
    modifier: Modifier = Modifier,
    onArtworkBitmap: (ImageBitmap) -> Unit = {},
) {
    val context = LocalContext.current
    var resolvedUrl by remember(artworkUrl) { mutableStateOf(artworkUrl) }
    var fallbackBlurredBitmap by remember(artworkUrl) { mutableStateOf<ImageBitmap?>(null) }

    val backdropBrush = remember(seedColor) {
        Brush.verticalGradient(
            0f to appleMusicGradientColorAt(seedColor, 0f),
            0.48f to appleMusicGradientColorAt(seedColor, 0.48f),
            1f to appleMusicGradientColorAt(seedColor, 1f),
        )
    }

    val blurSupported = isLyricsBlurSupported()

    if (resolvedUrl.isNotBlank()) {
        val request = remember(resolvedUrl) {
            ImageRequest.Builder(context)
                .data(resolvedUrl)
                .allowHardware(false)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        if (blurSupported) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { success ->
                    drawableToSoftwareImageBitmap(success.result.drawable)?.let { bmp ->
                        onArtworkBitmap(bmp)
                    }
                },
                onError = {
                    val fallback = resolvedUrl.replace("maxresdefault", "hqdefault")
                    if (fallback != resolvedUrl) resolvedUrl = fallback
                },
                modifier = modifier
                    .fillMaxSize()
                    .blur(BACKDROP_BLUR_RADIUS, BlurredEdgeTreatment.Unbounded),
            )
        } else {
            // API 26–30: keep a hidden/zero-alpha Coil loader to decode the bitmap safely,
            // then render a fast CPU box-blurred 48x48 ImageBitmap so older devices look frosted.
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { success ->
                    drawableToSoftwareImageBitmap(success.result.drawable)?.let { bmp ->
                        onArtworkBitmap(bmp)
                        fallbackBlurredBitmap = boxBlurThumbnail(bmp.asAndroidBitmap())
                    }
                },
                onError = {
                    val fallback = resolvedUrl.replace("maxresdefault", "hqdefault")
                    if (fallback != resolvedUrl) resolvedUrl = fallback
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f),
            )
            fallbackBlurredBitmap?.let { blurred ->
                Image(
                    bitmap = blurred,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = modifier.fillMaxSize(),
                )
            }
        }
    }

    val tintAlpha = if (blurSupported || fallbackBlurredBitmap != null) BACKDROP_TINT_ALPHA else 0.92f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(tintAlpha)
            .background(backdropBrush),
    )
}

/**
 * Computes and remembers [AppleMusicMesh] on [Dispatchers.Default] for [artwork].
 * Retains the previous mesh until the new one finishes so skips crossfade smoothly.
 */
@Composable
fun rememberAppleMusicMesh(
    artwork: ImageBitmap?,
    seed: Int,
): AppleMusicMesh? {
    var mesh by remember { mutableStateOf<AppleMusicMesh?>(null) }
    LaunchedEffect(artwork, seed) {
        val source = artwork ?: return@LaunchedEffect
        val computed = withContext(Dispatchers.Default) {
            runCatching { meshOf(source, seed) }.getOrNull()
        }
        if (computed != null) {
            mesh = computed
        }
    }
    return mesh
}

/**
 * Draws the smoothstep-resampled [mesh] hung from [seam], crossfading over 900ms when the mesh
 * changes. Holds [AppleMusicMesh.topEdge] down to [topHold], the seam row down to [seam], and the
 * full shifted mesh below [seam]. Uses 44dp blur on API 31+ and bilinear GPU stretch on API 26–30.
 */
@Composable
fun AppleMusicMeshBackdrop(
    mesh: AppleMusicMesh?,
    seam: Dp,
    modifier: Modifier = Modifier,
    topHold: Dp = 0.dp,
) {
    var shown by remember { mutableStateOf(mesh) }
    var incoming by remember { mutableStateOf<AppleMusicMesh?>(null) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(mesh) {
        val next = mesh ?: return@LaunchedEffect
        incoming?.let { shown = it }
        incoming = null
        val current = shown
        if (next === current) return@LaunchedEffect
        if (current == null) {
            shown = next
            return@LaunchedEffect
        }
        incoming = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(MESH_FADE_MS, easing = FastOutSlowInEasing))
        shown = next
        incoming = null
    }

    val blurModifier = if (isLyricsBlurSupported()) Modifier.blur(MESH_BLUR) else Modifier

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MeshFallback)
            .then(blurModifier),
    ) {
        val seamY = seam.toPx().coerceIn(0f, size.height)
        val topHoldY = topHold.toPx().coerceIn(0f, seamY)
        shown?.let { drawMesh(it, seamY, topHoldY, alpha = 1f) }
        incoming?.let { drawMesh(it, seamY, topHoldY, alpha = fade.value) }
        drawRect(
            brush = appleMusicSmoothScrimBrush(
                from = Color.Black.copy(alpha = 0.06f),
                to = Color.Black.copy(alpha = 0.30f),
            ),
        )
    }
}

/**
 * Gradient scrim under the status bar & grabber whose opacity adapts (16%–65%) to [topLuminance]
 * so white system icons stay legible against bright album covers.
 */
@Composable
fun AppleMusicTopStatusWash(
    topBandHeight: Dp,
    topLuminance: Float,
    modifier: Modifier = Modifier,
) {
    if (topBandHeight <= 0.dp) return
    val topWashAlpha by animateFloatAsState(
        targetValue = TOP_WASH_MIN_ALPHA + (TOP_WASH_MAX_ALPHA - TOP_WASH_MIN_ALPHA) * topLuminance.coerceIn(0f, 1f),
        animationSpec = tween(TOP_WASH_FADE_MS),
        label = "appleMusicTopWash",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBandHeight)
            .background(topWashBrush(topWashAlpha)),
    )
}

/**
 * 24-stop status bar protection gradient falling off at `(1 - p)^1.5`.
 */
fun topWashBrush(alpha: Float): Brush =
    Brush.verticalGradient(
        colorStops = Array(TOP_WASH_STEPS + 1) { i ->
            val p = i / TOP_WASH_STEPS.toFloat()
            p to Color.Black.copy(alpha = (alpha * (1f - p).pow(1.5f)).coerceIn(0f, 1f))
        },
    )

private fun DrawScope.drawMesh(
    mesh: AppleMusicMesh,
    seamY: Float,
    topHoldY: Float,
    alpha: Float,
) {
    if (alpha <= 0.001f) return
    val width = size.width.roundToInt().coerceAtLeast(1)
    val seam = seamY.roundToInt().coerceAtLeast(0)
    val topHold = topHoldY.roundToInt().coerceIn(0, seam)
    val quality = if (isLyricsBlurSupported()) FilterQuality.Low else FilterQuality.High

    if (topHold > 0) {
        drawImage(
            image = mesh.topEdge,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(MESH_TEX, 1),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, topHold),
            alpha = alpha,
            filterQuality = quality,
        )
    }
    if (seam > topHold) {
        drawImage(
            image = mesh.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(MESH_TEX, 1),
            dstOffset = IntOffset(0, topHold),
            dstSize = IntSize(width, seam - topHold),
            alpha = alpha,
            filterQuality = quality,
        )
    }
    drawImage(
        image = mesh.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(MESH_TEX, MESH_TEX),
        dstOffset = IntOffset(0, seam),
        dstSize = IntSize(width, (size.height.roundToInt() - seam).coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = quality,
    )
}

private fun meshOf(
    artwork: ImageBitmap,
    seed: Int,
): AppleMusicMesh? {
    if (artwork.width < 1 || artwork.height < 1) return null
    val width = MESH_SOURCE_PX
    val height = (MESH_SOURCE_PX.toLong() * artwork.height / artwork.width)
        .toInt()
        .coerceIn(1, MESH_SOURCE_PX * 2)

    val srcAndroid = artwork.asAndroidBitmap()
    val softwareSrc = if (srcAndroid.config == Bitmap.Config.HARDWARE) {
        srcAndroid.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        srcAndroid
    }
    val scaled = if (softwareSrc.width == width && softwareSrc.height == height) {
        softwareSrc
    } else {
        Bitmap.createScaledBitmap(softwareSrc, width, height, true)
    }
    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)
    if (scaled !== softwareSrc) scaled.recycle()
    if (softwareSrc !== srcAndroid) softwareSrc.recycle()

    val cols = MESH_GRID
    val rows = MESH_GRID
    val cells = cols * rows
    val red = LongArray(cells)
    val green = LongArray(cells)
    val blue = LongArray(cells)
    val count = IntArray(cells)

    for (y in 0 until height) {
        // Bottom-up so row 0 is the artwork's bottom edge
        val rowBase = ((height - 1 - y) * rows / height) * cols
        for (x in 0 until width) {
            val cell = rowBase + x * cols / width
            val pixel = pixels[y * width + x]
            red[cell] += ((pixel shr 16) and 0xFF).toLong()
            green[cell] += ((pixel shr 8) and 0xFF).toLong()
            blue[cell] += (pixel and 0xFF).toLong()
            count[cell]++
        }
    }

    val grid = IntArray(cells) { cell ->
        val n = count[cell].coerceAtLeast(1)
        argb((red[cell] / n).toInt(), (green[cell] / n).toInt(), (blue[cell] / n).toInt()).lifted()
    }

    val topRow = (rows - 1) * cols until rows * cols
    val topPixels = topRow.sumOf { count[it].toLong() }.coerceAtLeast(1L)
    val topLuminance =
        (topRow.sumOf { 0.2126 * red[it] + 0.7152 * green[it] + 0.0722 * blue[it] } / topPixels / 255.0)
            .toFloat()
            .coerceIn(0f, 1f)

    val texels = grid.rotatedBelowSeam(cols, rows, seed).resampled(cols, rows, MESH_TEX, MESH_TEX)
    val topTexels = grid.copyOfRange((rows - 1) * cols, rows * cols).resampled(cols, 1, MESH_TEX, 1)

    val image = Bitmap.createBitmap(texels, MESH_TEX, MESH_TEX, Bitmap.Config.ARGB_8888).asImageBitmap()
    val topEdge = Bitmap.createBitmap(topTexels, MESH_TEX, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
    return AppleMusicMesh(image = image, topLuminance = topLuminance, topEdge = topEdge)
}

private fun IntArray.rotatedBelowSeam(
    cols: Int,
    rows: Int,
    seed: Int,
): IntArray {
    if (rows <= 1) return this
    val random = Random(seed)
    val mirror = random.nextBoolean()
    val shift = random.nextInt(cols)
    val out = copyOf()
    for (row in 1 until rows) {
        val base = row * cols
        for (x in 0 until cols) {
            val src = if (mirror) cols - 1 - x else x
            out[base + x] = this[base + (src + shift) % cols]
        }
    }
    return out
}

private fun IntArray.resampled(
    cols: Int,
    rows: Int,
    outWidth: Int,
    outHeight: Int,
): IntArray {
    val out = IntArray(outWidth * outHeight)
    for (ty in 0 until outHeight) {
        val fy = (ty + 0.5f) / outHeight * rows - 0.5f
        val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val wy = smoothstep(fy - y0)
        for (tx in 0 until outWidth) {
            val fx = (tx + 0.5f) / outWidth * cols - 0.5f
            val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
            val x1 = (x0 + 1).coerceAtMost(cols - 1)
            val wx = smoothstep(fx - x0)
            val top = lerpArgb(this[y0 * cols + x0], this[y0 * cols + x1], wx)
            val bottom = lerpArgb(this[y1 * cols + x0], this[y1 * cols + x1], wx)
            out[ty * outWidth + tx] = lerpArgb(top, bottom, wy)
        }
    }
    return out
}

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpArgb(
    from: Int,
    to: Int,
    t: Float,
): Int {
    if (t <= 0f) return from
    if (t >= 1f) return to
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
    }
    return argb(channel(16), channel(8), channel(0))
}

private fun argb(
    red: Int,
    green: Int,
    blue: Int,
): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

private fun Int.lifted(): Int {
    val r = ((this shr 16) and 0xFF) / 255f
    val g = ((this shr 8) and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val lightness = (maxC + minC) / 2f
    val delta = maxC - minC
    if (delta == 0f) {
        val grey = (lightness.coerceAtLeast(MESH_FLOOR) * 255f).roundToInt()
        return argb(grey, grey, grey)
    }
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue =
        when (maxC) {
            r -> ((g - b) / delta).mod(6f)
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f
    val color = Color.hsl(hue, (saturation * MESH_VIBRANCE).coerceIn(0f, 1f), lightness.coerceIn(MESH_FLOOR, 1f))
    return argb(
        (color.red * 255f).roundToInt(),
        (color.green * 255f).roundToInt(),
        (color.blue * 255f).roundToInt(),
    )
}

internal fun drawableToSoftwareImageBitmap(drawable: Drawable?): ImageBitmap? {
    drawable ?: return null
    return runCatching {
        val raw = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
        val software = if (raw.config == Bitmap.Config.HARDWARE) {
            raw.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            raw
        }
        software?.asImageBitmap()
    }.getOrNull()
}

private fun boxBlurThumbnail(source: Bitmap): ImageBitmap {
    val size = 48
    val software = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: source
    } else {
        source
    }
    val scaled = Bitmap.createScaledBitmap(software, size, size, true)
    val pixels = IntArray(size * size)
    scaled.getPixels(pixels, 0, size, 0, 0, size, size)
    if (scaled !== software) scaled.recycle()
    if (software !== source) software.recycle()

    val temp = IntArray(size * size)
    val radius = 5
    repeat(3) {
        // Horizontal pass
        for (y in 0 until size) {
            val rowOffset = y * size
            for (x in 0 until size) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, size - 1)
                    val p = pixels[rowOffset + nx]
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                temp[rowOffset + x] = argb(r / count, g / count, b / count)
            }
        }
        // Vertical pass
        for (x in 0 until size) {
            for (y in 0 until size) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, size - 1)
                    val p = temp[ny * size + x]
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                pixels[y * size + x] = argb(r / count, g / count, b / count)
            }
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
