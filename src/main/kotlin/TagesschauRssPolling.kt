import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timer
import kotlin.system.exitProcess

fun main(args: Array<String>) = TagesschauRssPolling().run()

class TagesschauRssPolling {
    fun run() {
        println("Startup $javaClass")
        scheduleTimer(
                startAt = "20:15".calendar(),
                giveUpAfter = "22:00".calendar()
        )
    }

    private fun String.calendar(): Calendar {
        val splitInts = split(':').map(String::toInt)
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, splitInts[0])
            set(Calendar.MINUTE, splitInts[1])
        }
    }

    private fun scheduleTimer(startAt: Calendar, giveUpAfter: Calendar) {
        fun getTimestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        var attempt = 1
        timer(
                name = "tagesschau-rss-polling",
                daemon = false,
                startAt = startAt.time,
                period = 60000,
                action = {
                    print("\n${getTimestamp()} Try ${attempt++}")
                    if (newTagesschauAvailable()) {
                        alertUser()
                        cancel()
                        exitProcess(status = 0)
                    } else if (Date().after(giveUpAfter.time)) {
                        // Cancel and reschedule for tomorrow
                        cancel()
                        print("\nGiving up for today and reschedule for tomorrow")
                        val startAtTomorrow = startAt.apply { add(Calendar.DAY_OF_MONTH, 1) }
                        scheduleTimer(startAtTomorrow, giveUpAfter)
                    }
                }
        )
    }

    private fun alertUser() {
        "notify-send".exec(arrayOf("Zeit für die Tagesschau"))
        "cvlc".exec(arrayOf("src/main/resources/tagesschau-2000.mp3", "--play-and-exit"), true)
    }

    private fun newTagesschauAvailable(): Boolean {
        /*
            view-source:https://www.tagesschau.de/export/video-podcast/webl/tagesschau/

            <item>
            <title>03.02.2017 - tagesschau 20:00 Uhr</title>
            <description>Themen der Sendung: EU-Gipfel in Malta zu Flüchtlingsabkommen mit Libyen, Trump lockert Banken-Regulierung, USA verhängen Sanktionen gegen den Iran, Gabriel zu Gesprächen in New York, Erneut schwere Kämpfe in der Ostukraine, Ermittlungen nach Angriff am Louvre aufgenommen, Terrorverdächtiger am Frankfurter Flughafen festgenommen, Neue Bilder vom Mars, Prokop wird neuer Handball-Bundestrainer, Kokain-Fund in Hamburg, Das Wetter</description>
            <itunes:image href="http://www.tagesschau.de/multimedia/bilder/sendungsbild-236389~_v-videowebm.jpg" />
            <pubDate>Fri, 03 Feb 2017 20:00:00 +0100</pubDate>
            <enclosure url="http://media.tagesschau.de/video/2017/0203/TV-20170203-2020-3501.webl.h264.mp4" length="201214903" type="video/mp4" />
            <guid isPermaLink="false">TV-20170203-2020-3501-VL</guid>
            </item>
        */
        // https://github.com/kittinunf/Fuel#blocking-mode
        val (request, response, result) = "https://www.tagesschau.de/export/video-podcast/webl/tagesschau/".httpGet().responseString()

        val succeeded = when (result) {
            is Result.Failure -> false
            is Result.Success -> {
                // "<title>03.02.2017"
                val signOfNewEpisode = "<title>${SimpleDateFormat("dd.MM.yyyy").format(Date())}"
                val containsSignOfNewEpisode = response.toString().contains(signOfNewEpisode)
                print(" contains sign-of-new-episode \"$signOfNewEpisode\" -> $containsSignOfNewEpisode")
                containsSignOfNewEpisode
            }
        }
        return succeeded
    }

    private fun String.exec(args: Array<String>? = null, printOutput: Boolean = false): Process {
        val process: Process = when (args) {
            null -> Runtime.getRuntime().exec(this)
            else -> Runtime.getRuntime().exec(arrayOf(this, *args))
        }
        if (printOutput) printOutputAndError(process)
        return process
    }

    private fun printOutputAndError(process: Process) {
        println(process.inputStream.bufferedReader().use { it.readText() })
        println(process.errorStream.bufferedReader().use { it.readText() })
    }
}