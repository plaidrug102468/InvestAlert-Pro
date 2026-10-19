package com.investalert.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class NewsItem(
    val title: String,
    val url: String,
    val source: String
) {
    val key: String get() = (title + "|" + url).hashCode().toString()
}

object NewsResearchProvider {
    suspend fun news(query: String, limit: Int = 5): List<NewsItem> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("$query finance OR stock OR market when:7d", Charsets.UTF_8.name())
        val url = URL("https://news.google.com/rss/search?q=$q&hl=en-ZA&gl=ZA&ceid=ZA:en")
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9_000
            readTimeout = 9_000
            setRequestProperty("User-Agent", "InvestAlert/1.5")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
        }

        try {
            if (c.responseCode !in 200..299) return@withContext emptyList()
            c.inputStream.use { input ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(input, "UTF-8")
                val out = mutableListOf<NewsItem>()
                var event = parser.eventType
                var inItem = false
                var title = ""
                var link = ""
                var source = ""

                while (event != XmlPullParser.END_DOCUMENT && out.size < limit) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "item" -> {
                                inItem = true
                                title = ""
                                link = ""
                                source = ""
                            }
                            "title" -> if (inItem) title = parser.nextText().trim()
                            "link" -> if (inItem) link = parser.nextText().trim()
                            "source" -> if (inItem) source = parser.nextText().trim()
                        }
                        XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                            if (title.isNotBlank() && link.isNotBlank()) {
                                out += NewsItem(
                                    title = title,
                                    url = link,
                                    source = source.ifBlank { title.substringAfterLast(" - ", "News") }
                                )
                            }
                            inItem = false
                        }
                    }
                    event = parser.next()
                }
                out.distinctBy { it.key }
            }
        } finally {
            c.disconnect()
        }
    }

    suspend fun headlines(query: String, limit: Int = 4): List<String> =
        news(query, limit).map { it.title }
}
