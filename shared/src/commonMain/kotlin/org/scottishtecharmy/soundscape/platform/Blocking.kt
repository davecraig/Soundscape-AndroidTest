package org.scottishtecharmy.soundscape.platform

/**
 * Runs [block] to completion on this thread. For small resource reads which a screen needs for
 * its first frame, as Compose Resources' own stringResource does; previews only draw one frame.
 */
expect fun <T> runBlockingForResource(block: suspend () -> T): T
