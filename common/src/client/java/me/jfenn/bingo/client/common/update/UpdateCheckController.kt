package me.jfenn.bingo.client.common.update

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.jfenn.bingo.client.platform.IClient
import me.jfenn.bingo.client.platform.event.model.ClientServerEvent
import me.jfenn.bingo.client.platform.event.model.ClientTickEvent
import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.jsonUnpretty
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.text.Formatting
import me.jfenn.bingo.platform.text.HoverAction
import me.jfenn.bingo.platform.text.TextAction
import org.slf4j.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Checks GitHub (once per launch) for a newer Bingout release. If the installed version is out of date,
 * the player is told in chat shortly after joining a world, with a link to the download page.
 */
internal class UpdateCheckController(
    private val log: Logger,
    private val client: IClient,
    private val text: TextProvider,
    eventBus: IEventBus,
) {

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/Dyrox/bingout/releases/latest"
        private const val RELEASES_PAGE = "https://github.com/Dyrox/bingout/releases/latest"

        /** Ticks to wait after joining, so that the message isn't buried by the server's join messages */
        private const val NOTIFY_DELAY_TICKS = 60

        private val VERSION_REGEX = Regex("""(\d+)\.(\d+)\.(\d+)""")

        /** "2.16.8-build.ci-18-1+mc26.3" or "v2.16.8" -> [2, 16, 8] */
        fun parseVersion(version: String): List<Int>? {
            return VERSION_REGEX.find(version)?.groupValues?.drop(1)?.map { it.toInt() }
        }

        fun isNewer(latest: List<Int>, current: List<Int>): Boolean {
            for ((l, c) in latest.zip(current)) {
                if (l != c) return l > c
            }
            return false
        }
    }

    private class Release(val version: String, val url: String)

    private var checkStarted = false
    private var newerRelease: Release? = null
    private var notified = false
    private var inWorld = false
    private var ticksInWorld = 0

    private fun startCheck() {
        if (checkStarted) return
        checkStarted = true

        val current = client.modVersion ?: return
        val currentParsed = parseVersion(current) ?: return

        val request = HttpRequest.newBuilder(URI(LATEST_RELEASE_API))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Bingout-update-check")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() != 200) {
                    log.info("[UpdateCheckController] Could not check for updates (HTTP {})", response.statusCode())
                    return@thenAccept
                }
                val release = jsonUnpretty.parseToJsonElement(response.body()).jsonObject
                val tag = release["tag_name"]?.jsonPrimitive?.content ?: return@thenAccept
                val url = release["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE
                val latestParsed = parseVersion(tag) ?: return@thenAccept

                if (isNewer(latestParsed, currentParsed)) {
                    log.info("[UpdateCheckController] Bingout {} is available (installed: {})", tag, current)
                    client.execute { newerRelease = Release(tag, url) }
                }
            }
            .exceptionally { e ->
                log.info("[UpdateCheckController] Could not check for updates: {}", e.message)
                null
            }
    }

    private fun notifyPlayer(release: Release) {
        val current = client.modVersion?.let { parseVersion(it)?.joinToString(".") } ?: "?"
        val download = text.string(StringKey.UpdateDownload).formatted(Formatting.AQUA, Formatting.UNDERLINE)
        download.setClickEvent(TextAction.OpenUrl(URI(release.url)))
        download.setHoverEvent(HoverAction.ShowText(text.string(StringKey.UpdateDownloadTooltip)))

        val message = text.empty()
            .append(text.literal("[Bingout] ").formatted(Formatting.GOLD))
            .append(text.string(StringKey.UpdateAvailable, release.version, "v$current").formatted(Formatting.YELLOW))
            .append(download)
        client.player.sendChatMessage(message)
    }

    init {
        eventBus.register(ClientServerEvent.Join) {
            client.execute {
                inWorld = true
                ticksInWorld = 0
                startCheck()
            }
        }

        eventBus.register(ClientServerEvent.Disconnect) {
            client.execute { inWorld = false }
        }

        eventBus.register(ClientTickEvent.End) {
            if (!inWorld || notified) return@register
            ticksInWorld++
            val release = newerRelease ?: return@register
            if (ticksInWorld < NOTIFY_DELAY_TICKS) return@register
            notified = true
            notifyPlayer(release)
        }
    }
}
