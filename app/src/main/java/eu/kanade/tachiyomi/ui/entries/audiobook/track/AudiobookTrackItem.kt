package eu.kanade.tachiyomi.ui.entries.audiobook.track

import eu.kanade.tachiyomi.data.track.Tracker
import tachiyomi.domain.track.audiobook.model.AudiobookTrack

data class AudiobookTrackItem(val track: AudiobookTrack?, val tracker: Tracker)
