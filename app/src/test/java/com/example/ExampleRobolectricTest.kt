package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.URL_GAME
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
  fun `verify direct game url is configured`() {
    assertEquals("https://playigi.com/game/?touch=1", URL_GAME)
  }
}
