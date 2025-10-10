package org.koitharu.kotatsu.reviews

import android.content.Context
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

internal const val MIN_SUMMARY_LENGTH = 6
internal const val MIN_BODY_LENGTH = 32

fun showReviewEditorDialog(
	context: Context,
	scope: LifecycleCoroutineScope,
	layoutInflater: LayoutInflater,
	existing: AniListReview?,
	renderMarkdown: suspend (String) -> Result<String>,
	onSubmit: (summary: String, body: String, score: Int) -> Unit
) {
	val binding = DialogReviewEditorBinding.inflate(layoutInflater)
	val dialog = MaterialAlertDialogBuilder(context)
		.setTitle(if (existing == null) R.string.review_write else R.string.review_edit)
		.setView(binding.root)
		.setNegativeButton(android.R.string.cancel, null)
		.setPositiveButton(if (existing == null) R.string.review_write else R.string.review_edit, null)
		.create()

	dialog.setOnShowListener {
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
		binding.sliderScore.addOnChangeListener { slider: Slider, value: Float, _ ->
			updateScoreLabel(binding, slider, value.roundToInt(), context)
		}
		dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
			if (applyEditor(binding, context, onSubmit)) {
				dialog.dismiss()
			}
		}
	}

	val initialScore = existing?.score ?: 70
	binding.sliderScore.value = initialScore.toFloat()
	binding.textScoreValue.text = buildScoreText(initialScore, context)
	if (existing != null) {
		binding.inputSummary.setText(existing.summary)
		binding.inputBody.setText(existing.body)
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
	} else {
		binding.layoutSummary.error = null
	}
	if (body.length < MIN_BODY_LENGTH) {
		binding.layoutBody.error = context.getString(R.string.review_validation_body, MIN_BODY_LENGTH)
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
