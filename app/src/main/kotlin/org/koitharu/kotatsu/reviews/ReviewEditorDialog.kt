package org.koitharu.kotatsu.reviews

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.databinding.DialogReviewEditorBinding
import kotlin.math.roundToInt

internal const val MIN_SUMMARY_LENGTH = 20
internal const val MAX_SUMMARY_LENGTH = 120
internal const val MIN_BODY_LENGTH = 2200

fun showReviewEditorDialog(
	context: Context,
	scope: LifecycleCoroutineScope,
	layoutInflater: LayoutInflater,
	existing: AniListReview?,
	draft: ReviewDraft?,
	renderMarkdown: suspend (String) -> Result<String>,
	onSaveDraft: (summary: String, body: String, score: Int) -> Unit,
	onSubmit: (summary: String, body: String, score: Int) -> Unit
) {
	val binding = DialogReviewEditorBinding.inflate(layoutInflater)
	val dialog = MaterialAlertDialogBuilder(context)
		.setTitle(if (existing == null) R.string.review_write else R.string.review_edit)
		.setView(binding.root)
		.setNegativeButton(android.R.string.cancel, null)
		.setPositiveButton(if (existing == null) R.string.review_write else R.string.review_edit, null)
		.create()

	val initialSummary = draft?.summary ?: existing?.summary.orEmpty()
	val initialBody = draft?.body ?: existing?.body.orEmpty()
	val initialScore = draft?.score ?: existing?.score ?: 70
	var lastSavedState = EditorSnapshot(initialSummary, initialBody, initialScore)

	fun currentState(): EditorSnapshot = EditorSnapshot(
		binding.inputSummary.text?.toString().orEmpty().trim(),
		binding.inputBody.text?.toString().orEmpty().trim(),
		binding.sliderScore.value.roundToInt(),
	)

	fun hasUnsavedChanges(): Boolean = currentState() != lastSavedState

	fun saveDraftInternal() {
		val state = currentState()
		onSaveDraft(state.summary, state.body, state.score)
		lastSavedState = state
	}

	fun promptForDraftIfNeeded(onDiscard: () -> Unit) {
		if (!hasUnsavedChanges()) {
			onDiscard()
			return
		}
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.review_unsaved_changes_title)
			.setMessage(R.string.review_unsaved_changes_message)
			.setPositiveButton(R.string.review_save_draft) { _, _ ->
				saveDraftInternal()
				onDiscard()
			}
			.setNegativeButton(R.string.review_discard) { _, _ ->
				onDiscard()
			}
			.setNeutralButton(android.R.string.cancel, null)
			.show()
	}

	dialog.setOnShowListener {
		dialog.setCanceledOnTouchOutside(false)
		binding.buttonPreview.setOnClickListener {
			val body = binding.inputBody.text?.toString().orEmpty()
			if (body.isBlank()) {
				binding.cardPreview.isVisible = false
				return@setOnClickListener
			}
			binding.buttonPreview.isEnabled = false
			binding.textPreview.setText(R.string.review_markdown_loading)
			binding.cardPreview.isVisible = true
			scope.launch {
				val result = renderMarkdown(body)
				binding.buttonPreview.isEnabled = true
				result.onSuccess { html ->
					binding.textPreview.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
					binding.textPreview.movementMethod = android.text.method.LinkMovementMethod.getInstance()
				}.onFailure {
					binding.textPreview.setText(R.string.review_preview_error)
				}
			}
		}
		dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
			if (applyEditor(binding, context) { summary, body, score ->
				onSubmit(summary, body, score)
				lastSavedState = EditorSnapshot(summary, body, score)
			}) {
				dialog.dismiss()
			}
		}
		dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
			promptForDraftIfNeeded {
				dialog.dismiss()
			}
		}
		binding.buttonSaveDraft.setOnClickListener {
			saveDraftInternal()
		}

		val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)

		fun updateValidationState() {
			val summary = binding.inputSummary.text?.toString().orEmpty().trim()
			val body = binding.inputBody.text?.toString().orEmpty().trim()
			val score = binding.sliderScore.value.roundToInt()
			val isSummaryShort = summary.length < MIN_SUMMARY_LENGTH
			val isSummaryLong = summary.length > MAX_SUMMARY_LENGTH
			val isBodyShort = body.length < MIN_BODY_LENGTH

			binding.layoutSummary.error = when {
				isSummaryShort -> context.getString(R.string.review_validation_summary, MIN_SUMMARY_LENGTH)
				isSummaryLong -> context.getString(R.string.review_summary_length_error)
				else -> null
			}
			binding.layoutBody.error = if (isBodyShort) {
				context.getString(R.string.review_body_too_short)
			} else {
				null
			}
			positiveButton.isEnabled = !isSummaryShort && !isSummaryLong && !isBodyShort
			binding.buttonPreview.isEnabled = body.isNotBlank()
			binding.buttonSaveDraft.isEnabled = true
			updateScoreLabel(binding, binding.sliderScore, score, context)
		}

		binding.inputSummary.addTextChangedListener(SimpleTextWatcher { updateValidationState() })
		binding.inputBody.addTextChangedListener(SimpleTextWatcher { updateValidationState() })
		binding.sliderScore.addOnChangeListener { _, _, _ -> updateValidationState() }

		updateValidationState()
	}

	binding.inputSummary.setText(initialSummary)
	binding.inputBody.setText(initialBody)
	binding.sliderScore.value = initialScore.toFloat()
	binding.textScoreValue.text = buildScoreText(initialScore, context)

	dialog.setOnKeyListener { _, keyCode, event ->
		if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
			promptForDraftIfNeeded {
				dialog.dismiss()
			}
			true
		} else {
			false
		}
	}
	dialog.show()
}

private fun applyEditor(
	binding: DialogReviewEditorBinding,
	context: Context,
	onSubmit: (summary: String, body: String, score: Int) -> Unit
): Boolean {
	val summary = binding.inputSummary.text?.toString().orEmpty().trim()
	val body = binding.inputBody.text?.toString().orEmpty().trim()
	val score = binding.sliderScore.value.roundToInt()

	var isValid = true
	if (summary.length < MIN_SUMMARY_LENGTH) {
		binding.layoutSummary.error = context.getString(R.string.review_validation_summary, MIN_SUMMARY_LENGTH)
		isValid = false
	} else if (summary.length > MAX_SUMMARY_LENGTH) {
		binding.layoutSummary.error = context.getString(R.string.review_summary_length_error)
		isValid = false
	} else {
		binding.layoutSummary.error = null
	}
	if (body.length < MIN_BODY_LENGTH) {
		binding.layoutBody.error = context.getString(R.string.review_body_too_short)
		isValid = false
	} else {
		binding.layoutBody.error = null
	}
	if (!isValid) {
		return false
	}
	onSubmit(summary, body, score)
	return true
}

private fun updateScoreLabel(
	binding: DialogReviewEditorBinding,
	slider: Slider,
	score: Int,
	context: Context
) {
	binding.textScoreValue.text = buildScoreText(score, context)
	if (slider.isPressed) {
		binding.cardPreview.isVisible = false
	}
}

private fun buildScoreText(score: Int, context: Context): String =
	context.getString(R.string.review_score_format, score)

private data class EditorSnapshot(
	val summary: String,
	val body: String,
	val score: Int,
)

private class SimpleTextWatcher(
	private val onChanged: () -> Unit,
) : TextWatcher {

	override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

	override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

	override fun afterTextChanged(s: Editable?) {
		onChanged()
	}
}
