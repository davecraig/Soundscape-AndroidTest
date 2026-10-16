package org.scottishtecharmy.soundscape.platform

import kotlinx.coroutines.runBlocking

actual fun <T> runBlockingForResource(block: suspend () -> T): T = runBlocking { block() }
