package org.koitharu.kotatsu.threads

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.ActivityThreadDetailBinding
import org.koitharu.kotatsu.databinding.DialogThreadReplyBinding

private const val LOAD_MORE_THRESHOLD = 2

@AndroidEntryPoint
class ThreadDetailActivity : BaseActivity<ActivityThreadDetailBinding>() {

	private val viewModel: ThreadDetailViewModel by viewModels()
	private val headerAdapter = ThreadHeaderAdapter()
	private val commentAdapter = ThreadCommentAdapter()
	private val concatAdapter = ConcatAdapter(headerAdapter, commentAdapter)

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
		setContentView(ActivityThreadDetailBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		setupToolbar()
		setupRecycler()
		setupComposer()
		setupFab()
		bindViewModel()
	}

	private fun setupToolbar() = with(viewBinding.toolbar) {
		title = viewModel.mangaTitle ?: getString(R.string.thread_detail_title)
		setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
	}

	private fun setupRecycler() = with(viewBinding) {
		recyclerThread.layoutManager = LinearLayoutManager(this@ThreadDetailActivity)
		recyclerThread.adapter = concatAdapter
		recyclerThread.addOnScrollListener(loadMoreListener)
		swipeRefresh.setOnRefreshListener {
			isUserRefreshing = true
			viewModel.reload()
		}
	}

	private fun setupComposer() = with(viewBinding) {
		buttonComposerSend.isEnabled = false
		inputComposerBody.addTextChangedListener { text ->
			buttonComposerSend.isEnabled = !text.isNullOrBlank()
			if (!text.isNullOrBlank()) {
				inputLayoutComposer.error = null
			}
		}
		buttonComposerSend.setOnClickListener {
			val body = inputComposerBody.text?.toString()?.trim().orEmpty()
			if (body.isBlank()) {
				inputLayoutComposer.error = getString(R.string.thread_validation_body)
			} else {
				inputLayoutComposer.error = null
				buttonComposerSend.isEnabled = false
				viewModel.postComment(body)
				inputComposerBody.text?.clear()
			}
		}
	}

	private fun setupFab() = with(viewBinding.fabReply) {
		setOnClickListener { showReplyDialog() }
	}

	private fun bindViewModel() {
		viewModel.messages.observeEvent(this, ::handleMessage)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerThread, null))
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.state.collect(::renderState)
				}
			}
		}
	}

	private fun renderState(state: ThreadDetailUiState) = with(viewBinding) {
		when (state) {
			ThreadDetailUiState.Loading -> {
				progress.isVisible = !isUserRefreshing
				swipeRefresh.isRefreshing = isUserRefreshing
				recyclerThread.isVisible = false
				textMessage.isVisible = false
				fabReply.isVisible = false
				cardComposer.isVisible = false
				inputComposerBody.isEnabled = false
				buttonComposerSend.isEnabled = false
			}

			ThreadDetailUiState.Error -> {
				progress.isVisible = false
				swipeRefresh.isRefreshing = false
				isUserRefreshing = false
				recyclerThread.isVisible = false
				textMessage.isVisible = true
				textMessage.setText(R.string.thread_load_error)
				fabReply.isVisible = false
				cardComposer.isVisible = false
				inputComposerBody.isEnabled = false
				buttonComposerSend.isEnabled = false
			}

			is ThreadDetailUiState.Content -> {
				progress.isVisible = false
				swipeRefresh.isRefreshing = false
				isUserRefreshing = false
				headerAdapter.thread = state.thread
				commentAdapter.submitList(state.comments)
				recyclerThread.isVisible = true
				textMessage.isVisible = state.comments.isEmpty()
				if (state.comments.isEmpty()) {
					textMessage.setText(R.string.thread_comments_empty)
				}
				fabReply.isVisible = state.viewer != null
				fabReply.isEnabled = state.viewer != null
				val composerEnabled = state.viewer != null
				cardComposer.isVisible = composerEnabled
				inputComposerBody.isEnabled = composerEnabled
				buttonComposerSend.isEnabled = composerEnabled && !inputComposerBody.text.isNullOrBlank()
				if (state.viewer == null) {
					inputLayoutComposer.error = null
				}
			}
		}
	}

	private fun handleMessage(message: ThreadMessage) {
		when (message) {
			is ThreadMessage.Resource -> Snackbar
				.make(viewBinding.recyclerThread, getString(message.resId, *message.args), Snackbar.LENGTH_SHORT)
				.show()

			is ThreadMessage.Plain -> Snackbar
				.make(viewBinding.recyclerThread, message.value, Snackbar.LENGTH_SHORT)
				.show()
		}
	}

	private fun showReplyDialog() {
		val binding = DialogThreadReplyBinding.inflate(layoutInflater)
		val dialog = MaterialAlertDialogBuilder(this)
			.setTitle(R.string.thread_reply)
			.setView(binding.root)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.thread_publish, null)
			.create()
		dialog.setOnShowListener {
			binding.inputLayoutBody.error = null
			val button = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
			button.setOnClickListener {
				val body = binding.inputBody.text?.toString()?.trim().orEmpty()
				if (body.isBlank()) {
					binding.inputLayoutBody.error = getString(R.string.thread_validation_body)
				} else {
					binding.inputLayoutBody.error = null
					viewModel.postComment(body)
					dialog.dismiss()
				}
			}
		}
		dialog.show()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	companion object {
		const val EXTRA_THREAD = "extraThread"
	}
}
