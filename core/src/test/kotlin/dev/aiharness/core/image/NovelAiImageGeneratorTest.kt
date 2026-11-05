package dev.aiharness.core.image

import dev.aiharness.core.model.ImageGenSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NovelAiImageGeneratorTest {
    private val server = MockWebServer()
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private fun zipOf(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun request(model: String = "nai-diffusion-4-5-full") = ImageRequest(
        prompt = "1girl, garden",
        negativePrompt = "blurry",
        model = model,
        width = 832,
        height = 1216,
        steps = 23,
        scale = 5.0,
        sampler = "k_euler_ancestral",
        noiseSchedule = "karras",
        qualityToggle = true,
        ucPreset = "heavy",
        seed = 42,
    )

    @Test
    fun sendsMultipartRequestAndUnzipsImages() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/x-zip-compressed").setBody(Buffer().write(zipOf("image_0.png" to png))))
        val images = NovelAiImageGenerator(endpoint = server.url("/").toString()).generate(request(), "pst-token")

        assertEquals(1, images.size)
        assertEquals("image/png", images[0].mimeType)
        assertTrue(images[0].bytes.contentEquals(png))
        assertEquals(42L, images[0].seed)

        val recorded = server.takeRequest()
        assertEquals("/ai/generate-image", recorded.path)
        assertEquals("Bearer pst-token", recorded.getHeader("Authorization"))
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = recorded.body.readUtf8()
        assertContains(body, "name=\"request\"; filename=\"blob\"")
        assertContains(body, "\"v4_prompt\"")
    }

    @Test
    fun appliesQualityTagsAndNegativePresetsLikeTheWebClient() {
        val json = NovelAiImageGenerator.buildRequestJson(request(), 7)
        val input = json["input"]!!.jsonPrimitive.content
        assertEquals("1girl, garden, location, very aesthetic, masterpiece, no text", input)
        val params = json["parameters"]!!.jsonObject
        assertEquals(4, params["params_version"]!!.jsonPrimitive.content.toInt())
        assertEquals("heavy", params["ucPresetId"]!!.jsonPrimitive.content)
        val negative = params["negative_prompt"]!!.jsonPrimitive.content
        assertTrue(negative.startsWith("nsfw, lowres") && negative.endsWith(", blurry"))
        val caption = params["v4_prompt"]!!.jsonObject["caption"]!!.jsonObject["base_caption"]!!.jsonPrimitive.content
        assertEquals(input, caption)

        // Asking for nsfw removes it from the negative prompt, as novelai.net does.
        val nsfw = NovelAiImageGenerator.buildRequestJson(request().copy(prompt = "nsfw, 1girl"), 7)
        assertFalse(nsfw["parameters"]!!.jsonObject["negative_prompt"]!!.jsonPrimitive.content.contains("nsfw"))
    }

    @Test
    fun orientationSizes() {
        val settings = ImageGenSettings(width = 832, height = 1216)
        assertEquals(1216 to 832, ImageService.sizeFor(settings, "landscape"))
        assertEquals(832 to 1216, ImageService.sizeFor(settings, "portrait"))
        val (w, h) = ImageService.sizeFor(settings, "square")
        assertEquals(w, h)
        assertEquals(0, w % 64)
    }
}
