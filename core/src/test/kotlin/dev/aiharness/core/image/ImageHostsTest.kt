package dev.aiharness.core.image

import dev.aiharness.core.RecordingImageGenerator
import dev.aiharness.core.model.ImageGenSettings
import dev.aiharness.core.model.ImageHost
import dev.aiharness.core.model.Persona
import dev.aiharness.core.storage.inmemory.InMemoryBlobStore
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Base64
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageHostsTest {
    private val server = MockWebServer()
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    private val b64 = Base64.getEncoder().encodeToString(png)

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private fun base() = server.url("/").toString().trimEnd('/')

    private fun request(host: ImageHost, model: String = "") = ImageRequest(
        prompt = "1girl, reading, watercolor",
        negativePrompt = "blurry",
        model = model,
        width = 832,
        height = 1216,
        steps = 28,
        scale = 6.0,
        sampler = "Euler a",
        noiseSchedule = "karras",
        qualityToggle = false,
        ucPreset = "none",
        seed = 42,
        host = host,
        baseUrl = base(),
    )

    private fun bodyOf(r: okhttp3.mockwebserver.RecordedRequest) = HarnessJson.parseToJsonElement(r.body.readUtf8()).jsonObject

    @Test
    fun forgeAndAutomatic1111() = runTest {
        server.enqueue(MockResponse().setBody("""{"images":["$b64"],"info":"{}"}"""))
        val images = StableDiffusionImageGenerator().generate(request(ImageHost.STABLE_DIFFUSION, model = "animagine.safetensors"), null)

        assertContentEquals(png, images.single().bytes)
        val sent = server.takeRequest()
        assertEquals("/sdapi/v1/txt2img", sent.path)
        val body = bodyOf(sent)
        assertEquals("1girl, reading, watercolor", body["prompt"]!!.jsonPrimitive.content)
        assertEquals(832, body["width"]!!.jsonPrimitive.int)
        assertEquals("animagine.safetensors", body["override_settings"]!!.jsonObject["sd_model_checkpoint"]!!.jsonPrimitive.content)
    }

    @Test
    fun comfyUiQueuesWaitsAndFetches() = runTest {
        server.enqueue(MockResponse().setBody("""{"prompt_id":"abc","number":1}"""))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"abc":{"outputs":{"9":{"images":[{"filename":"fumibako_001.png","subfolder":"","type":"output"}]}}}}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png)))
        val images = ComfyUiImageGenerator(pollMillis = 1).generate(request(ImageHost.COMFYUI, model = "sdxl.safetensors").copy(sampler = "euler_ancestral"), null)

        assertContentEquals(png, images.single().bytes)
        val queued = bodyOf(server.takeRequest())["prompt"]!!.jsonObject
        assertEquals("1girl, reading, watercolor", queued["6"]!!.jsonObject["inputs"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(1216, queued["5"]!!.jsonObject["inputs"]!!.jsonObject["height"]!!.jsonPrimitive.int, "numbers are filled in as numbers")
        assertEquals("sdxl.safetensors", queued["4"]!!.jsonObject["inputs"]!!.jsonObject["ckpt_name"]!!.jsonPrimitive.content)
        assertEquals("/history/abc", server.takeRequest().path)
        server.takeRequest()
        assertContains(server.takeRequest().path!!, "/view?filename=fumibako_001.png")
    }

    @Test
    fun openAiTakesTheNearestSizeAndAKey() = runTest {
        server.enqueue(MockResponse().setBody("""{"created":1,"data":[{"b64_json":"$b64"}]}"""))
        val images = OpenAiImageGenerator().generate(request(ImageHost.OPENAI, model = "gpt-image-1"), "sk-test")

        assertContentEquals(png, images.single().bytes)
        val sent = server.takeRequest()
        assertEquals("/v1/images/generations", sent.path)
        assertEquals("Bearer sk-test", sent.getHeader("Authorization"))
        val body = bodyOf(sent)
        assertEquals("1024x1536", body["size"]!!.jsonPrimitive.content)
        assertEquals("auto", body["quality"]!!.jsonPrimitive.content)
        assertContains(body["prompt"]!!.jsonPrimitive.content, "Avoid: blurry.")
    }

    @Test
    fun googleGeminiAndImagen() = runTest {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"Here you go"},{"inlineData":{"mimeType":"image/png","data":"$b64"}}]}}]}"""))
        val gemini = GoogleImageGenerator().generate(request(ImageHost.GOOGLE, model = "gemini-2.5-flash-image"), "g-key")
        assertContentEquals(png, gemini.single().bytes)
        val sent = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash-image:generateContent", sent.path)
        assertEquals("g-key", sent.getHeader("x-goog-api-key"))
        val config = bodyOf(sent)["generationConfig"]!!.jsonObject
        assertEquals("IMAGE", config["responseModalities"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("3:4", config["imageConfig"]!!.jsonObject["aspectRatio"]!!.jsonPrimitive.content)

        server.enqueue(MockResponse().setBody("""{"predictions":[{"bytesBase64Encoded":"$b64","mimeType":"image/png"}]}"""))
        val imagen = GoogleImageGenerator().generate(request(ImageHost.GOOGLE, model = "imagen-4.0-generate-001"), "g-key")
        assertContentEquals(png, imagen.single().bytes)
        assertEquals("/v1beta/models/imagen-4.0-generate-001:predict", server.takeRequest().path)
    }

    @Test
    fun aPersonasOwnProfileAndSentencesForHostsThatTakeDescriptions() = runTest {
        val recorder = RecordingImageGenerator()
        val keys = mapOf("oa" to "sk-test")
        val service = ImageService(recorder, InMemoryBlobStore()) { keys[it] }
        val nai = ImageProfiles.preset(ImageHost.NOVELAI, "nai").copy(credentialId = "nai")
        val openAi = ImageProfiles.preset(ImageHost.OPENAI, "oa").copy(credentialId = "oa")
        val settings = ImageGenSettings(profiles = listOf(nai, openAi), defaultProfileId = "nai", defaultStyle = "watercolor")
        val mira = Persona("p", "Mira", selfPrompt = "short black hair, glasses", defaultOutfit = "green cardigan", imageProfileId = "oa", createdAt = 0, updatedAt = 0)

        service.generate(settings, "watering plants", persona = mira)

        val sent = recorder.requests.single()
        assertEquals(ImageHost.OPENAI, sent.host)
        assertEquals("gpt-image-1", sent.model)
        assertEquals("Drawn in the style of watercolor. Mira: short black hair, glasses. Wearing green cardigan. Watering plants.", sent.prompt)
        assertTrue(settings.ready)
    }
}
