package me.jfenn.bingo.common.dev

/** Signals between the dev test tools of the server and the client (the same JVM in singleplayer) */
object DevSignals {
    /** Set when a test script ends with `exit` (see DevScriptRunner); the client's DevAutoplay then quits the game */
    @Volatile
    @JvmStatic
    var exitRequested = false
}
