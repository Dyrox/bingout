package me.jfenn.bingo.integrations.chunky

internal object DummyChunky : IChunkyApi {
    override fun clearAllTasks() {}
    override fun startPregen() {}
    override fun cancelTasks() {}
}