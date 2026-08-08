package com.plocoli.yomp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

/**
 * Coil3 는 네트워크 이미지용 페처를 자동 등록하지 않으므로 여기서 명시적으로 붙인다.
 * (앨범 썸네일이 안 뜨던 원인)
 */
class YompApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
}
