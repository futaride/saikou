package ani.saikou.others

import ani.saikou.loadData
import ani.saikou.media.Media
import ani.saikou.media.Selected
import ani.saikou.parsers.*
import ani.saikou.saveData
import ani.saikou.tryWithSuspend

class SubscriptionHelper {
    companion object {
        private fun loadSelected(mediaId: Int, isAdult: Boolean, isAnime: Boolean): Selected {
            return loadData<Selected>("${mediaId}-select") ?: Selected().let {
                it.source =
                    if (isAdult) 0
                    else if (isAnime) loadData("settings_def_anime_source") ?: 0
                    else loadData("settings_def_manga_source") ?: 0
                it.preferDub = loadData("settings_prefer_dub") ?: false
                it
            }
        }

        private fun saveSelected(mediaId: Int, data: Selected) {
            saveData("$mediaId-select", data)
        }

        fun getAnimeParser(isAdult: Boolean, id: Int): AnimeParser {
            val sources = if (isAdult) HAnimeSources else AnimeSources
            val selected = loadSelected(id, isAdult, true)
            val parser = sources[selected.source]
            parser.selectDub = selected.preferDub
            return parser
        }

        suspend fun getEpisode(parser: AnimeParser, id: Int, isAdult: Boolean): Episode? {
            return tryWithSuspend {
                val selected = loadSelected(id, isAdult, true)
                val show = parser.loadSavedShowResponse(id) ?: throw Exception("Failed to load saved data of $id")
                val ep = parser.getLatestEpisode(show.link, show.extra, selected.latest)
                if (ep != null) {
                    selected.latest = ep.number.toFloat()
                    saveSelected(id, selected)
                }
                ep
            }
        }

        fun getMangaParser(isAdult: Boolean, id: Int): MangaParser {
            val sources = if (isAdult) HMangaSources else MangaSources
            val selected = loadSelected(id, isAdult, false)
            return sources[selected.source]
        }

        suspend fun getChapter(parser: MangaParser, id: Int, isAdult: Boolean): MangaChapter? {
            return tryWithSuspend {
                val selected = loadSelected(id, isAdult, false)
                val show = parser.loadSavedShowResponse(id) ?: throw Exception("Failed to load saved data of $id")
                val ep = parser.getLatestChapter(show.link, show.extra, selected.latest)
                if (ep != null) {
                    selected.latest = ep.number.toFloat()
                    saveSelected(id, selected)
                }
                ep
            }
        }

        data class SubscribeMedia(
            val isAnime: Boolean,
            val isAdult: Boolean,
            val id: Int,
            val name: String,
            val image: String?
        ) : java.io.Serializable

        private const val subscriptions = "subscriptions"
        fun getSubscriptions(): Map<Int, SubscribeMedia> = loadData(subscriptions)
            ?: mapOf<Int, SubscribeMedia>().also { saveData(subscriptions, it) }

        fun saveSubscription(media: Media, subscribed: Boolean) {
            val data = loadData<Map<Int, SubscribeMedia>>(subscriptions)!!.toMutableMap()
            if (subscribed) {
                if (!data.containsKey(media.id)) {
                    val new = SubscribeMedia(
                        media.anime != null,
                        media.isAdult,
                        media.id,
                        media.userPreferredName,
                        media.cover
                    )
                    data[media.id] = new
                }
            } else {
                data.remove(media.id)
            }
            saveData(subscriptions, data)
        }
    }
}