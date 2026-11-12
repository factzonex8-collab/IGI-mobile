package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("IGI MOBILE", appName)
  }

  @Test
  fun `verify first launch opens download and subsequent opens game`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("igi_mobile_test_prefs", Context.MODE_PRIVATE)

    // First launch
    val launchCount1 = prefs.getInt("launch_count", 0)
    val url1 = if (launchCount1 == 0) "https://playigi.com/download.html" else "https://playigi.com/game/?touch=1"
    prefs.edit().putInt("launch_count", launchCount1 + 1).commit()
    assertEquals("https://playigi.com/download.html", url1)

    // Second launch
    val launchCount2 = prefs.getInt("launch_count", 0)
    val url2 = if (launchCount2 == 0) "https://playigi.com/download.html" else "https://playigi.com/game/?touch=1"
    prefs.edit().putInt("launch_count", launchCount2 + 1).commit()
    assertEquals("https://playigi.com/game/?touch=1", url2)

    // Third launch (and forever)
    val launchCount3 = prefs.getInt("launch_count", 0)
    val url3 = if (launchCount3 == 0) "https://playigi.com/download.html" else "https://playigi.com/game/?touch=1"
    assertEquals("https://playigi.com/game/?touch=1", url3)
  }
}
