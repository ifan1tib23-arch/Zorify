package com.example

import com.example.data.UrlHelper
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testSpotifyUrlConversion() {
    val url = "https://open.spotify.com/track/3DFMhkPf89BV5z0HhUYa6r?si=izJeq4ryS3KdT5RvrsDnhQ"
    val directUrl = UrlHelper.convertToDirectStreamUrl(url)
    println("DEBUG: Converted direct URL: $directUrl")
    
    val meta = UrlHelper.fetchMetadataForUrl(url)
    println("DEBUG: Metadata: $meta")
    
    // Assert something to show the output in case of failure or success
    assertTrue("Converted URL should not be the same if successful", url != directUrl)
  }
}

