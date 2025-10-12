package org.koitharu.kotatsu.reviews

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ViewAnimator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
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
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.SheetReviewsBinding

private const val LOAD_MORE_THRESHOLD = 2

internal const val ARG_MANGA_ID = "mangaId"
internal const val ARG_MANGA_TITLE = "mangaTitle"

@AndroidEntryPoint
class ReviewsSheet : BaseAdaptiveSheet<SheetReviewsBinding>() {

	private val viewModel: ReviewViewModel by viewModels()

	private val adapter = ReviewListAdapter { review ->
		viewModel.onReviewSelected(review)
	}

	private val detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode != Activity.RESULT_OK) {
			return@registerForActivityResult
		}
		val data = result.data ?: return@registerForActivityResult
		@Suppress("DEPRECATION")
		val updated = data.getParcelableExtra<AniListReview>(ReviewDetailActivity.EXTRA_RESULT_REVIEW)
		if (updated != null) {
			viewModel.updateReview(updated)
		}
		val deletedId = data.getLongExtra(ReviewDetailActivity.EXTRA_RESULT_DELETED_ID, 0L)
		if (deletedId != 0L) {
			viewModel.removeReview(deletedId)
		}
	}

	private val loadMoreListener = object : RecyclerView.OnScrollListener() {
		override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
			super.onScrolled(recyclerView, dx, dy)
			if (dy <= 0) {
				return
			}
			val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
			val lastVisible = layoutManager.findLastVisibleItemPosition()
			val total = layoutManager.itemCount
			if (total == 0) {
				return
			}
			if (total - lastVisible <= LOAD_MORE_THRESHOLD) {
				viewModel.loadMore()
			}
		}
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetReviewsBinding {
		return SheetReviewsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetReviewsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.headerBar.title = viewModel.mangaTitle ?: getString(R.string.reviews_title)
		setupList(binding)
		setupActions(binding)
		observeState(binding)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onDestroyView() {
		viewBinding?.recyclerView?.removeOnScrollListener(loadMoreListener)
		super.onDestroyView()
	}

	private fun setupList(binding: SheetReviewsBinding) = with(binding) {
		recyclerView.layoutManager = LinearLayoutManager(requireContext())
		recyclerView.adapter = adapter
		recyclerView.addOnScrollListener(loadMoreListener)
		swipeRefresh.setOnRefreshListener {
			viewModel.reload(force = true)
		}
	}

	private fun setupActions(binding: SheetReviewsBinding) = with(binding) {
		buttonWrite.setOnClickListener {
			val state = viewModel.state.value as? ReviewUiState.Content ?: return@setOnClickListener
			showEditor(state.myReview)
		}
		buttonDelete.setOnClickListener {
			showDeleteConfirmation()
		}
	}

	private fun observeState(binding: SheetReviewsBinding) {
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch {
					viewModel.state.collect { state ->
						renderState(binding, state)
					}
				}
				launch {
					viewModel.isLoading.collect { isLoading ->
						if (binding.contentAnimator.displayedChild == CONTENT_CHILD) {
							binding.swipeRefresh.isRefreshing = isLoading && !adapter.isEmpty()
						} else {
							binding.swipeRefresh.isRefreshing = false
						}
					}
				}
				launch {
					viewModel.messages.collect { message ->
						when (message) {
							is ReviewMessage.Resource -> Snackbar
								.make(
									binding.recyclerView,
									getString(message.resId, *message.formatArgs),
									Snackbar.LENGTH_SHORT,
								).show()

							is ReviewMessage.Plain -> Snackbar
								.make(binding.recyclerView, message.value, Snackbar.LENGTH_SHORT)
								.show()
						}
					}
				}
			}
		}
		viewModel.openReviewDetails.observeEvent(viewLifecycleOwner) { nav ->
			detailLauncher.launch(ReviewDetailActivity.createIntent(requireContext(), nav))
		}
	}

	private fun renderState(binding: SheetReviewsBinding, state: ReviewUiState) {
		when (state) {
			ReviewUiState.Loading -> {
				setDisplayedChild(binding.contentAnimator, LOADING_CHILD)
				binding.swipeRefresh.isEnabled = false
			binding.textGateMessage.text = null
			binding.actionsContainer.isVisible = false
			binding.divider.isVisible = false
			binding.textEmpty.isVisible = false
			adapter.viewerId = null
		}

		ReviewUiState.NotAuthorized -> {
			setDisplayedChild(binding.contentAnimator, GATE_CHILD)
			binding.textGateMessage.setText(R.string.review_sign_in_required)
			binding.swipeRefresh.isEnabled = false
			binding.actionsContainer.isVisible = false
			binding.divider.isVisible = false
			binding.textEmpty.isVisible = false
			adapter.viewerId = null
		}

		ReviewUiState.NotTracked -> {
			setDisplayedChild(binding.contentAnimator, GATE_CHILD)
			binding.textGateMessage.setText(R.string.review_track_required)
			binding.swipeRefresh.isEnabled = false
			binding.actionsContainer.isVisible = false
			binding.divider.isVisible = false
			binding.textEmpty.isVisible = false
			adapter.viewerId = null
		}

			is ReviewUiState.Content -> {
				setDisplayedChild(binding.contentAnimator, CONTENT_CHILD)
				binding.swipeRefresh.isEnabled = true
				adapter.viewerId = state.viewer?.id
				Log.d(TAG, "Displaying state with ${state.reviews.size} reviews")
				adapter.submitList(state.reviews.toList())
				binding.textEmpty.isVisible = state.reviews.isEmpty()
				binding.progressLoadMore.isVisible = state.isLoadingMore
				updateActions(binding, state)
			}
		}
	}

	private fun updateActions(binding: SheetReviewsBinding, state: ReviewUiState.Content) = with(binding) {
		val hasReview = state.myReview != null
		buttonWrite.setText(if (hasReview) R.string.review_edit else R.string.review_write)
		buttonDelete.isVisible = hasReview
		divider.isVisible = true
		actionsContainer.isVisible = true
	}

	private fun showEditor(existing: AniListReview?) {
		viewLifecycleOwner.lifecycleScope.launch {
			val draft = viewModel.loadDraft()
			showReviewEditorDialog(
				context = requireContext(),
				scope = viewLifecycleOwner.lifecycleScope,
				layoutInflater = layoutInflater,
				existing = existing,
				draft = draft,
				renderMarkdown = { body -> viewModel.renderMarkdown(body) },
				onSaveDraft = { summary, body, score -> viewModel.saveDraft(summary, body, score) },
				onSubmit = { summary, body, score -> viewModel.submitReview(summary, body, score) },
			)
		}
	}

	private fun showDeleteConfirmation() {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.review_delete_confirm)
			.setMessage(R.string.review_delete_confirm_message)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.review_delete) { _, _ ->
				viewModel.deleteReview()
			}
			.show()
	}

	private fun setDisplayedChild(animator: ViewAnimator, child: Int) {
		if (animator.displayedChild != child) {
			animator.displayedChild = child
		}
	}

	private fun ReviewListAdapter.isEmpty(): Boolean = itemCount == 0

	companion object {
		private const val LOADING_CHILD = 0
		private const val GATE_CHILD = 1
		private const val CONTENT_CHILD = 2
		private const val TAG = "KotatsuReviews"
	}
}
