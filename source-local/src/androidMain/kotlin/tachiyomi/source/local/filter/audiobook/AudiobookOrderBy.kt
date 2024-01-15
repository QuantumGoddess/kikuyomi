package tachiyomi.source.local.filter.audiobook

import android.content.Context
import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilter
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR

sealed class AudiobookOrderBy(context: Context, selection: Selection) : AudiobookFilter.Sort(
    context.stringResource(MR.strings.local_filter_order_by),
    arrayOf(context.stringResource(MR.strings.title), context.stringResource(MR.strings.date)),
    selection,
) {
    class Popular(context: Context) : AudiobookOrderBy(context, Selection(0, true))
    class Latest(context: Context) : AudiobookOrderBy(context, Selection(1, false))
}
