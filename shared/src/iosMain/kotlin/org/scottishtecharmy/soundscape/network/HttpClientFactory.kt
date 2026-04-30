package org.scottishtecharmy.soundscape.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLRequestReturnCacheDataDontLoad
import platform.Foundation.NSURLRequestUseProtocolCachePolicy

fun createIosVectorTileClient(
    baseUrl: String,
    hasNetwork: () -> Boolean = { true },
): VectorTileClient {
    val tileCache = NSURLCache(
        memoryCapacity = 10uL * 1024uL * 1024uL,
        diskCapacity = 100uL * 1024uL * 1024uL,
        diskPath = "vector_tiles"
    )
    val httpClient = HttpClient(Darwin) {
        engine {
            configureSession {
                setURLCache(tileCache)
            }
            configureRequest {
                setCachePolicy(
                    if (hasNetwork()) NSURLRequestUseProtocolCachePolicy
                    else NSURLRequestReturnCacheDataDontLoad
                )
            }
        }
        expectSuccess = false
    }
    return VectorTileClient(httpClient, baseUrl)
}

fun createIosPhotonSearchClient(baseUrl: String): PhotonSearchClient {
    val httpClient = HttpClient(Darwin) {
        expectSuccess = false
    }
    return PhotonSearchClient(httpClient, baseUrl)
}
