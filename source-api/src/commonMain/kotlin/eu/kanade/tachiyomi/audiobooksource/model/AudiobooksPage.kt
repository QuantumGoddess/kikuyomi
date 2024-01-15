package eu.kanade.tachiyomi.audiobooksource.model

import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook

data class AudiobooksPage(val audiobooks: List<SAudiobook>, val hasNextPage: Boolean)
