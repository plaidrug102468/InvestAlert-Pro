package com.investalert.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object NewsResearchProvider {
    suspend fun headlines(query: String, limit: Int = 4): List<String> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("$query finance OR stock OR market when:7d", Charsets.UTF_8.name())
        val url = URL("https://news.google.com/rss/search?q=$q&hl=en-ZA&gl=ZA&ceid=ZA:en")
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "InvestAlert/1.3")
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
        }
        try {
            if (c.responseCode !in 200..299) return@withContext emptyList()
            c.inputStream.use { input ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(input, "UTF-8")
                val out = mutableListOf<String>()
                var event = parser.eventType
                var inItem = false
                while (event != XmlPullParser.END_DOCUMENT && out.size < limit) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "item" -> inItem = true
                            "title" -> if (inItem) {
                                val title = parser.nextText().trim()
                                if (title.isNotBlank()) out += title
                            }
                        }
                        XmlPullParser.END_TAG -> if (parser.name == "item") inItem = false
                    }
                    event = parser.next()
                }
                out
            }
        } finally {
            c.disconnect()
        }
    }
}
