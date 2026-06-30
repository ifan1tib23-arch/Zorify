package com.example.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LinkMetadata(
    val title: String,
    val artist: String,
    val coverUrl: String
)

object UrlHelper {
    private const val TAG = "UrlHelper"

    /**
     * Converts raw user-provided sharing URLs (such as Dropbox, Google Drive, YouTube, and Spotify)
     * into direct streamable/downloadable raw file links. Includes robust YouTube search fallbacks for Spotify track failures.
     */
    fun convertToDirectStreamUrl(url: String, title: String? = null, artist: String? = null): String {
        if (url.isBlank()) return url
        var result = url.trim()

        try {
            // Convert Dropbox links
            if (result.contains("dropbox.com")) {
                if (!result.contains("dl.dropboxusercontent.com")) {
                    result = result.replace("www.dropbox.com", "dl.dropboxusercontent.com")
                    result = result.replace("dropbox.com", "dl.dropboxusercontent.com")
                }

                // Convert dl=0 to dl=1 for direct streaming/downloading
                if (result.contains("dl=0")) {
                    result = result.replace("dl=0", "dl=1")
                } else if (!result.contains("dl=1") && !result.contains("raw=1")) {
                    if (result.contains("?")) {
                        result = "$result&dl=1"
                    } else {
                        result = "$result?dl=1"
                    }
                }
                Log.d(TAG, "Converted Dropbox URL from $url to $result")
            }
            // Convert Google Drive links
            else if (result.contains("drive.google.com")) {
                val fileIdPattern = "/file/d/([^/]+)".toRegex()
                val matchResult = fileIdPattern.find(result)
                if (matchResult != null) {
                    val fileId = matchResult.groupValues[1]
                    result = "https://drive.google.com/uc?export=download&id=$fileId"
                    Log.d(TAG, "Converted Google Drive URL from $url to $result")
                }
            }
            // Resolve Spotify track URLs
            else if (result.contains("spotify.com")) {
                val trackIdPattern = "/track/([^?/]+)".toRegex()
                val matchResult = trackIdPattern.find(result)
                if (matchResult != null) {
                    val trackId = matchResult.groupValues[1]
                    Log.d(TAG, "Resolving Spotify track: $trackId")
                    var resolvedUrl = fetchSpotifyStreamUrl(trackId)
                    
                    // Fallback to YouTube Search if direct spotifydown endpoint fails or is blocked
                    if (resolvedUrl == null) {
                        Log.w(TAG, "SpotifyDown resolution failed. Using YouTube Search fallback...")
                        val searchQuery = if (!title.isNullOrBlank()) {
                            "$title ${artist ?: ""} audio"
                        } else {
                            val metadata = fetchMetadataForUrl(result)
                            if (metadata != null) {
                                "${metadata.title} ${metadata.artist} audio"
                            } else null
                        }
                        
                        if (searchQuery != null) {
                            Log.d(TAG, "Fallback search query: $searchQuery")
                            val ytVideoId = searchYoutubeVideoId(searchQuery)
                            if (ytVideoId != null) {
                                val ytUrl = "https://www.youtube.com/watch?v=$ytVideoId"
                                Log.d(TAG, "YouTube fallback URL found: $ytUrl")
                                resolvedUrl = fetchYoutubeStreamUrl(ytUrl)
                            }
                        }
                    }
                    
                    if (resolvedUrl != null) {
                        result = resolvedUrl
                        Log.d(TAG, "Converted Spotify URL to: $result")
                    }
                }
            }
            // Resolve YouTube video URLs
            else if (result.contains("youtube.com") || result.contains("youtu.be")) {
                Log.d(TAG, "Resolving YouTube URL: $result")
                val resolvedUrl = fetchYoutubeStreamUrl(result)
                if (resolvedUrl != null) {
                    result = resolvedUrl
                    Log.d(TAG, "Converted YouTube URL to: $result")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URL $url: ${e.message}")
        }

        return result
    }

    /**
     * Automatically fetches metadata (Title, Artist, and Cover URL) from Spotify or YouTube.
     */
    fun fetchMetadataForUrl(url: String): LinkMetadata? {
        val trimmed = url.trim()
        try {
            // Spotify metadata
            if (trimmed.contains("spotify.com")) {
                val trackIdPattern = "/track/([^?/]+)".toRegex()
                val matchResult = trackIdPattern.find(trimmed)
                if (matchResult != null) {
                    val trackId = matchResult.groupValues[1]
                    val apiUrl = "https://api.spotifydown.com/download/$trackId"
                    val headers = mapOf(
                        "Referer" to "https://spotifydown.com/",
                        "Origin" to "https://spotifydown.com",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    val response = performGetRequest(apiUrl, headers)
                    if (response != null) {
                        val json = JSONObject(response)
                        if (json.optBoolean("success")) {
                            val meta = json.optJSONObject("metadata")
                            if (meta != null) {
                                return LinkMetadata(
                                    title = meta.optString("title", ""),
                                    artist = meta.optString("artists", ""),
                                    coverUrl = meta.optString("cover", "")
                                )
                            }
                        }
                    }
                }
            }
            // YouTube metadata (via free and public noembed oEmbed API)
            else if (trimmed.contains("youtube.com") || trimmed.contains("youtu.be")) {
                val encodedUrl = URLEncoder.encode(trimmed, "UTF-8")
                val apiUrl = "https://noembed.com/embed?url=$encodedUrl"
                val response = performGetRequest(apiUrl)
                if (response != null) {
                    val json = JSONObject(response)
                    val fullTitle = json.optString("title", "")
                    
                    var title = fullTitle
                    var artist = json.optString("author_name", "")
                    
                    // Attempt to split standard "Artist - Title" format in titles
                    if (fullTitle.contains(" - ")) {
                        val parts = fullTitle.split(" - ", limit = 2)
                        artist = parts[0].trim()
                        title = parts[1].trim()
                    } else if (fullTitle.contains(" – ")) {
                        val parts = fullTitle.split(" – ", limit = 2)
                        artist = parts[0].trim()
                        title = parts[1].trim()
                    }
                    
                    val thumbnail = json.optString("thumbnail_url", "")
                    return LinkMetadata(
                        title = title,
                        artist = artist,
                        coverUrl = thumbnail
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch metadata for URL $url: ${e.message}")
        }
        return null
    }

    private fun fetchSpotifyStreamUrl(trackId: String): String? {
        val url = "https://api.spotifydown.com/download/$trackId"
        val headers = mapOf(
            "Referer" to "https://spotifydown.com/",
            "Origin" to "https://spotifydown.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        val response = performGetRequest(url, headers) ?: return null
        return try {
            val json = JSONObject(response)
            if (json.optBoolean("success")) {
                val link = json.optString("link")
                if (link.isNotBlank()) {
                    Log.d(TAG, "Successfully resolved Spotify track $trackId to stream URL")
                    link
                } else null
            } else {
                Log.e(TAG, "Spotifydown API error: ${json.optString("message")}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Spotifydown response: ${e.message}")
            null
        }
    }

    private fun fetchYoutubeStreamUrl(youtubeUrl: String): String? {
        val cobaltUrl = "https://api.cobalt.tools/api/json"
        
        // Build JSON body using standard and highly compatible Cobalt fields
        val jsonBody = """
            {
                "url": "$youtubeUrl",
                "isAudioOnly": true,
                "aFormat": "mp3"
            }
        """.trimIndent()
        
        val response = performPostRequest(cobaltUrl, jsonBody) ?: return null
        return try {
            val json = JSONObject(response)
            val streamUrl = json.optString("url")
            if (streamUrl.isNotBlank()) {
                Log.d(TAG, "Successfully resolved YouTube URL to Cobalt stream URL")
                streamUrl
            } else {
                Log.e(TAG, "Cobalt API returned empty URL: $response")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Cobalt response: ${e.message}")
            null
        }
    }

    /**
     * Performs a lightweight scrape of YouTube search results page to find the top video ID.
     */
    fun searchYoutubeVideoId(query: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            val html = performGetRequest(searchUrl, headers) ?: return null
            
            // Try searching for "videoId":"..." format first (modern YT JSON state inside HTML)
            val jsonPattern = "\"videoId\":\"([a-zA-Z0-9_-]{11})\"".toRegex()
            val jsonMatch = jsonPattern.find(html)
            if (jsonMatch != null) {
                val id = jsonMatch.groupValues[1]
                Log.d(TAG, "Found YT Video ID via JSON: $id")
                return id
            }
            
            // Try searching for watch?v= format
            val watchPattern = "/watch\\?v=([a-zA-Z0-9_-]{11})".toRegex()
            val watchMatch = watchPattern.find(html)
            if (watchMatch != null) {
                val id = watchMatch.groupValues[1]
                Log.d(TAG, "Found YT Video ID via watch URL: $id")
                return id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing YouTube search scraper: ${e.message}")
        }
        return null
    }

    private fun performGetRequest(urlStr: String, headers: Map<String, String> = emptyMap()): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.e(TAG, "GET request to $urlStr returned status $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing GET request to $urlStr: ${e.message}")
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun performPostRequest(urlStr: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }
            
            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "POST request to $urlStr returned status $responseCode: $errorText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing POST request to $urlStr: ${e.message}")
        } finally {
            connection?.disconnect()
        }
        return null
    }
}
