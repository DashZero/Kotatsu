package org.koitharu.kotatsu.comments.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R // Assuming fragment_comments.xml is in the main res/layout
import org.koitharu.kotatsu.comments.CommentViewModel
import org.koitharu.kotatsu.comments.databinding.FragmentCommentsBinding

@AndroidEntryPoint
class CommentsFragment : Fragment() {

    private var _binding: FragmentCommentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommentViewModel by viewModels()
    private lateinit var commentAdapter: CommentAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter(
            onReportClicked = {
                // TODO: Implement report dialog/confirmation
                viewModel.reportComment(it)
                Toast.makeText(context, "Comment reported (placeholder)", Toast.LENGTH_SHORT).show()
            },
            onDeleteClicked = { 
                // TODO: Implement delete confirmation, check for moderator role
                viewModel.deleteComment(it)
                Toast.makeText(context, "Comment delete requested (placeholder)", Toast.LENGTH_SHORT).show()
            }
        )
        binding.commentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentAdapter
            // Add item decoration if needed (e.g., for spacing)
        }
    }

    private fun setupClickListeners() {
        binding.sendCommentButton.setOnClickListener {
            val commentText = binding.commentEditText.text.toString().trim()
            if (commentText.isNotBlank()) {
                // TODO: Get actual userId, userName, avatarUrl from user session/profile
                val mockUserId = "user123"
                val mockUserName = "TestUser"
                val mockAvatarUrl: String? = null // or some placeholder image

                viewModel.sendComment(commentText, mockUserId, mockUserName, mockAvatarUrl)
                binding.commentEditText.text.clear()
            } else {
                Toast.makeText(context, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.comments.collect {
                    commentAdapter.submitList(it)
                }
            }
        }
        // TODO: Observe other states from ViewModel like errors, rate limiting, etc.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.commentsRecyclerView.adapter = null // Clear adapter to avoid leaks
        _binding = null
    }
}
