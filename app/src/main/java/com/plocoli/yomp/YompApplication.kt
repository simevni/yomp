package com.plocoli.yomp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

/**
 * Coil3 doesn't auto-register a network fetcher, so we add one explicitly here.
 * (This was why album thumbnails weren't loading.)
 */
class YompApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
}
