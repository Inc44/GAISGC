package im.bpu.gaisgc.manager

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.api.services.drive.Drive
import im.bpu.gaisgc.service.DriveService
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.pow
import org.jetbrains.skia.Image

object MediaManager {
	private const val LUMINANCE_THRESHOLD = 0.128

	private fun srgb(channel: Double): Double {
		return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
	}

	private fun relativeLuminance(r: Double, g: Double, b: Double): Double {
		return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)
	}

	fun isVideoThumbnailBlack(bytes: ByteArray): Boolean {
		return try {
			val bais = ByteArrayInputStream(bytes)
			val img = ImageIO.read(bais) ?: return false
			val width = img.width
			val height = img.height
			var luminance = 0.0
			val pixelCount = width * height
			val rgbPixels = img.getRGB(0, 0, width, height, null, 0, width)
			for (rgbPixel in rgbPixels) {
				val r = ((rgbPixel shr 16) and 0xFF) / 255.0
				val g = ((rgbPixel shr 8) and 0xFF) / 255.0
				val b = (rgbPixel and 0xFF) / 255.0
				luminance += relativeLuminance(r, g, b)
			}
			(luminance / pixelCount) < LUMINANCE_THRESHOLD
		} catch (exception: Exception) {
			false
		}
	}

	private fun getVideoDuration(videoFile: File): Double? {
		val ffmpegProcess =
			ProcessBuilder("ffmpeg", "-i", videoFile.absolutePath).redirectErrorStream(true).start()
		val ffmpegProcessIS = ffmpegProcess.inputStream
		val reader = ffmpegProcessIS.reader()
		val text = reader.readText()
		ffmpegProcess.waitFor()
		val regex = Regex("Duration: (\\d+):(\\d+):(\\d+)\\.(\\d+)")
		val match = regex.find(text) ?: return null
		val (hours, minutes, seconds, centiseconds) = match.destructured
		return hours.toLong() * 3600 +
			minutes.toLong() * 60 +
			seconds.toLong() +
			centiseconds.toDouble() / 100
	}

	private fun extractVideoFrame(videoFile: File, time: Double, imageFile: File) {
		ProcessBuilder(
				"ffmpeg",
				"-ss",
				time.toString(),
				"-i",
				videoFile.absolutePath,
				"-frames:v",
				"1",
				"-y",
				imageFile.absolutePath,
			)
			.start()
			.waitFor()
	}

	suspend fun getVideoMiddleFrame(service: Drive, id: String): ImageBitmap? {
		return try {
			val bytes = DriveService.downloadFileBytes(service, id)
			val videoFile = File.createTempFile("gaisgc", ".mkv")
			videoFile.writeBytes(bytes)
			val imageFile = File.createTempFile("gaisgc", ".png")
			val duration = getVideoDuration(videoFile)
			if (duration != null) {
				extractVideoFrame(videoFile, duration / 2, imageFile)
				if (imageFile.exists() && imageFile.length() > 0) {
					val bytes = imageFile.readBytes()
					videoFile.delete()
					imageFile.delete()
					val image = Image.makeFromEncoded(bytes).toComposeImageBitmap()
					return image
				}
			}
			videoFile.delete()
			imageFile.delete()
			null
		} catch (exception: Exception) {
			null
		}
	}
}