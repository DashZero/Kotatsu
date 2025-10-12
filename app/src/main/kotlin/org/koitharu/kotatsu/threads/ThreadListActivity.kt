package org.koitharu.kotatsu.threads

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.ActivityThreadListBinding
import org.koitharu.kotatsu.databinding.DialogThreadComposerBinding

private const val LOAD_MORE_THRESHOLD = 2

@AndroidEntryPoint
class ThreadListActivity : BaseActivity<ActivityThreadListBinding>() {

	private val viewModel: ThreadListViewModel by viewModels()
	private val adapter = ThreadPreviewAdapter { thread ->
		viewModel.onThreadSelected(thread)
	}

	private var isUserRefreshing = false

	private val loadMoreListener = object : RecyclerView.OnScrollListener() {
		override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
			super.onScrolled(recyclerView, dx, dy)
			if (dy <= 0) {
				return
			}
			val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
			val total = layoutManager.itemCount
			if (total == 0) {
				return
			}
			val lastVisible = layoutManager.findLastVisibleItemPosition()
			if (total - lastVisible <= LOAD_MORE_THRESHOLD) {
				viewModel.loadMore()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityThreadListBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		setupToolbar()
		setupList()
		setupFab()
		bindViewModel()
	}

	private fun setupToolbar() = with(viewBinding.toolbar) {
		val titleText = viewModel.mangaTitle
		title = if (titleText.isNullOrBlank()) {
			getString(R.string.thread_list_title_generic)
		} else {
			getString(R.string.thread_list_title, titleText)
		}
		inflateMenu(R.menu.menu_thread_list)
		setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
		setOnMenuItemClickListener(::onMenuItemSelected)
		updateSortMenu(ThreadSort.RECENT_ACTIVITY)
	}

	private fun setupList() = with(viewBinding) {
		recyclerThreads.layoutManager = LinearLayoutManager(this@ThreadListActivity)
		recyclerThreads.adapter = adapter
		recyclerThreads.addOnScrollListener(loadMoreListener)
		swipeRefresh.setOnRefreshListener {
			isUserRefreshing = true
			viewModel.reload(force = true)
		}
	}

	private fun setupFab() = with(viewBinding.fabNewThread) {
		setOnClickListener { showCreateThreadDialog() }
	}

	private fun bindViewModel() {
		viewModel.messages.observeEvent(this, ::handleMessage)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerThreads, null))
		viewModel.openThread.observeEvent(this) { thread ->
			router.openThreadDetail(viewModel.mangaTitle, thread)
		}
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.state.collect(::renderState)
				}
			}
		}
	}

	private fun renderState(state: ThreadListUiState) = with(viewBinding) {
		when (state) {
			ThreadListUiState.Loading -> {
				progress.isVisible = !isUserRefreshing
				swipeRefresh.isRefreshing = isUserRefreshing
				recyclerThreads.isVisible = false
				textMessage.isVisible = false
				fabNewThread.isVisible = false
				adapter.submitList(emptyList())
			}

			ThreadListUiState.NotAuthorized -> {
				progress.isVisible = false
				swipeRefresh.isRefreshing = false
				isUserRefreshing = false
				recyclerThreads.isVisible = false
				textMessage.isVisible = true
				textMessage.setText(R.string.threads_sign_in_required)
				fabNewThread.isVisible = false
				adapter.submitList(emptyList())
			}

			ThreadListUiState.NotTracked -> {
				progress.isVisible = false
				swipeRefresh.isRefreshing = false
				isUserRefreshing = false
				recyclerThreads.isVisible = false
				textMessage.isVisible = true
				textMessage.setText(R.string.threads_track_required)
				fabNewThread.isVisible = false
				adapter.submitList(emptyList())
			}

			is ThreadListUiState.Content -> {
				progress.isVisible = false
				swipeRefresh.isRefreshing = false
				isUserRefreshing = false
				adapter.submitList(state.threads)
				recyclerThreads.isVisible = state.threads.isNotEmpty()
				textMessage.isVisible = state.threads.isEmpty()
				if (state.threads.isEmpty()) {
					textMessage.setText(R.string.threads_empty)
				}
				fabNewThread.isVisible = state.viewer != null
				fabNewThread.isEnabled = state.viewer != null
				updateSortMenu(state.sort)
			}
		}
	}

	private fun updateSortMenu(sort: ThreadSort) {
		val menu = viewBinding.toolbar.menu ?: return
		for (index in 0 until menu.size()) {
			val item = menu.getItem(index)
			item.isChecked = when (item.itemId) {
				R.id.action_sort_recent -> sort == ThreadSort.RECENT_ACTIVITY
				R.id.action_sort_newest -> sort == ThreadSort.NEWEST
				R.id.action_sort_replies -> sort == ThreadSort.MOST_REPLIES
				else -> false
			}
		}
	}

	private fun handleMessage(message: ThreadMessage) {
		when (message) {
			is ThreadMessage.Resource -> Snackbar
				.make(viewBinding.recyclerThreads, getString(message.resId, *message.args), Snackbar.LENGTH_SHORT)
				.show()

			is ThreadMessage.Plain -> Snackbar
				.make(viewBinding.recyclerThreads, message.value, Snackbar.LENGTH_SHORT)
				.show()
		}
	}

	private fun onMenuItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			R.id.action_sort_recent -> {
				item.isChecked = true
				viewModel.selectSort(ThreadSort.RECENT_ACTIVITY)
			}
			R.id.action_sort_newest -> {
				item.isChecked = true
				viewModel.selectSort(ThreadSort.NEWEST)
			}
			R.id.action_sort_replies -> {
				item.isChecked = true
				viewModel.selectSort(ThreadSort.MOST_REPLIES)
			}
			else -> return false
		}
		return true
	}

	private fun showCreateThreadDialog() {
		val binding = DialogThreadComposerBinding.inflate(layoutInflater)
		val dialog = MaterialAlertDialogBuilder(this)
			.setTitle(R.string.thread_new)
			.setView(binding.root)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.thread_publish, null)
			.create()
		dialog.setOnShowListener {
			binding.inputLayoutTitle.error = null
			binding.inputLayoutBody.error = null
			val button = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
			button.setOnClickListener {
				val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
				val body = binding.inputBody.text?.toString()?.trim().orEmpty()
				var valid = true
				if (title.isBlank()) {
					binding.inputLayoutTitle.error = getString(R.string.thread_validation_title)
					valid = false
				} else {
					binding.inputLayoutTitle.error = null
				}
				if (body.isBlank()) {
					binding.inputLayoutBody.error = getString(R.string.thread_validation_body)
					valid = false
				} else {
					binding.inputLayoutBody.error = null
				}
				if (valid) {
					viewModel.createThread(title, body)
					dialog.dismiss()
				}
			}
		}
		dialog.show()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

}
