package btcrenaud.advancedmenus.services

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.imageio.ImageReader

/**
 * Downloads images from URLs with caching support.
 * Supports static images (PNG, JPEG, WebP) and animated GIFs (frame extraction).
 *
 * Cache location: plugins/TypeWriter/cache/images/
 */
class ImageDownloadService {

    /**
     * Represents a single frame of an image (or the entire image if static).
     *
     * @property pixels ARGB pixel data, row-major order
     * @property width Image width in pixels
     * @property height Image height in pixels
     * @property delayMs Frame delay in milliseconds (for animated GIFs)
     */
    data class ImageFrame(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val delayMs: Long = 0
    )

    /**
     * Cached image data.
     */
    data class CachedImage(
        val frames: List<ImageFrame>,
        val animated: Boolean,
        val url: String,
        val cachedAt: Long = System.currentTimeMillis()
    )

    private val cache = ConcurrentHashMap<String, CachedImage>()
    private val maxCacheSize = 50
    private val maxImageSize = 256

    /**
     * Downloads an image from a URL and displays it at the given location.
     * This is a placeholder for the full rendering pipeline.
     *
     * @param player The player to show the image to
     * @param location World location to display the image
     * @param url URL to download the image from
     * @param scale Display scale
     * @param layer Z-order layer
     */
    fun downloadAndDisplay(
        player: Player,
        location: Location,
        url: String,
        scale: Double,
        layer: Int
    ) {
        // Check cache first
        val cached = cache[url]
        if (cached != null) {
            // TODO: Render cached image as ItemDisplay with map data
            return
        }

        // Async download
        Thread {
            try {
                val image = downloadImage(url) ?: return@Thread
                cache[url] = image

                // Trim cache if too large (LRU eviction)
                if (cache.size > maxCacheSize) {
                    val oldest = cache.entries.minByOrNull { it.value.cachedAt }
                    oldest?.let { cache.remove(it.key) }
                }

                // TODO: Schedule rendering back on main thread via region scheduler
            } catch (e: Exception) {
                // Silently fail on download errors
            }
        }.start()
    }

    /**
     * Downloads and parses an image from a URL.
     * Supports static images and animated GIFs.
     */
    fun downloadImage(url: String): CachedImage? {
        return try {
            val uri = URI(url)
            val imageUrl = uri.toURL()
            val connection = imageUrl.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "TypeWriter-AdvancedMenus/2.0")

            val contentType = connection.contentType ?: ""
            val inputStream = connection.getInputStream()

            if (contentType.contains("gif") || url.lowercase().endsWith(".gif")) {
                // Parse animated GIF frames
                val frames = parseGifFrames(inputStream)
                CachedImage(frames = frames, animated = frames.size > 1, url = url)
            } else {
                // Static image
                val image = ImageIO.read(inputStream) ?: return null
                val resized = resizeImage(image)
                val pixels = resized.getRGB(0, 0, resized.width, resized.height, null, 0, resized.width)
                val frame = ImageFrame(pixels, resized.width, resized.height)
                CachedImage(frames = listOf(frame), animated = false, url = url)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses GIF frames with their individual delays.
     */
    private fun parseGifFrames(inputStream: java.io.InputStream): List<ImageFrame> {
        val frames = mutableListOf<ImageFrame>()
        try {
            val reader: ImageReader = ImageIO.getImageReadersByFormatName("gif").next()
            val iis = ImageIO.createImageInputStream(inputStream)
            reader.setInput(iis, false)

            val numFrames = reader.getNumImages(true)
            for (i in 0 until numFrames) {
                val image = reader.read(i)
                val resized = resizeImage(image)
                val pixels = resized.getRGB(0, 0, resized.width, resized.height, null, 0, resized.width)

                // Try to extract frame delay from metadata
                var delayMs = 100L // Default 100ms
                try {
                    val metadata = reader.getImageMetadata(i)
                    val tree = metadata.getAsTree("javax_imageio_gif_image_1.0")
                    val children = tree.childNodes
                    for (j in 0 until children.length) {
                        val node = children.item(j)
                        if (node.nodeName == "GraphicControlExtension") {
                            val delayAttr = node.attributes.getNamedItem("delayTime")
                            if (delayAttr != null) {
                                delayMs = (delayAttr.nodeValue.toLongOrNull() ?: 10) * 10
                            }
                        }
                    }
                } catch (_: Exception) {}

                frames.add(ImageFrame(pixels, resized.width, resized.height, delayMs))
            }

            reader.dispose()
            iis.close()
        } catch (e: Exception) {
            // If GIF parsing fails, try as static image
            inputStream.reset()
            val image = ImageIO.read(inputStream) ?: return frames
            val resized = resizeImage(image)
            val pixels = resized.getRGB(0, 0, resized.width, resized.height, null, 0, resized.width)
            frames.add(ImageFrame(pixels, resized.width, resized.height))
        }
        return frames
    }

    /**
     * Resizes an image to fit within maxImageSize while maintaining aspect ratio.
     */
    private fun resizeImage(image: BufferedImage): BufferedImage {
        if (image.width <= maxImageSize && image.height <= maxImageSize) return image

        val scale = minOf(maxImageSize.toDouble() / image.width, maxImageSize.toDouble() / image.height)
        val newWidth = (image.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (image.height * scale).toInt().coerceAtLeast(1)

        val resized = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g = resized.createGraphics()
        g.drawImage(image, 0, 0, newWidth, newHeight, null)
        g.dispose()
        return resized
    }

    /**
     * Clears the image cache.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Shuts down the service and clears all cached data.
     */
    fun shutdown() {
        cache.clear()
    }
}
