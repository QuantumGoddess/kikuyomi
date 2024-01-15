package eu.kanade.tachiyomi.audiobooksource

/**
 * A factory for creating sources at runtime.
 */
interface AudiobookSourceFactory {
    /**
     * Create a new copy of the sources
     * @return The created sources
     */
    fun createSources(): List<AudiobookSource>
}
