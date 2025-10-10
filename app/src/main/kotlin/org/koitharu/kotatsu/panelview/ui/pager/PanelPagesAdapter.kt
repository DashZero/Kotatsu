
package org.koitharu.kotatsu.panelview.ui.pager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import org.koitharu.kotatsu.core.exceptions.resolve.ExceptionResolver
import org.koitharu.kotatsu.core.os.NetworkState
import org.koitharu.kotatsu.databinding.ItemPageBinding
import org.koitharu.kotatsu.panelview.settings.PanelViewSettings
import org.koitharu.kotatsu.reader.domain.PageLoader
import org.koitharu.kotatsu.reader.ui.config.ReaderSettings
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderAdapter

class PanelPagesAdapter(
    private val lifecycleOwner: LifecycleOwner,
    loader: PageLoader,
    readerSettingsProducer: ReaderSettings.Producer,
    networkState: NetworkState,
    exceptionResolver: ExceptionResolver,
    private val panelSettings: PanelViewSettings,
    private val listener: PanelPageHolder.Listener,
) : BaseReaderAdapter<PanelPageHolder>(
    loader = loader,
    readerSettingsProducer = readerSettingsProducer,
    networkState = networkState,
    exceptionResolver = exceptionResolver,
) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        loader: PageLoader,
        readerSettingsProducer: ReaderSettings.Producer,
        networkState: NetworkState,
        exceptionResolver: ExceptionResolver,
    ): PanelPageHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemPageBinding.inflate(inflater, parent, false)
        return PanelPageHolder(
            owner = lifecycleOwner,
            binding = binding,
            loader = loader,
            readerSettingsProducer = readerSettingsProducer,
            networkState = networkState,
            exceptionResolver = exceptionResolver,
            panelSettings = panelSettings,
            listener = listener,
        )
    }
}
