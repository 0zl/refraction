package shiro.refraction.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import shiro.refraction.R
import shiro.refraction.data.model.Profile
import shiro.refraction.domain.ProfileManager

class DashboardBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: MaterialButton
    private lateinit var adapter: ProfileAdapter

    var onProfileSelected: ((Profile) -> Unit)? = null
    var onAddProfile: (() -> Unit)? = null
    var onEditProfile: ((Profile) -> Unit)? = null
    var onDeleteProfile: ((Profile) -> Unit)? = null

    private val profileManager by lazy { ProfileManager(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerProfiles)
        addButton = view.findViewById(R.id.btnAddProfile)

        val activeId = arguments?.getString(ARG_ACTIVE_ID)
        adapter = ProfileAdapter(
            activeProfileId = activeId,
            onProfileClick = { profile ->
                onProfileSelected?.invoke(profile)
                dismiss()
            },
            onProfileLongClick = { profile, anchor ->
                showContextMenu(profile, anchor)
                true
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            onAddProfile?.invoke()
            dismiss()
        }

        observeProfiles()
    }

    private fun observeProfiles() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileManager.observeProfiles().collect { entities ->
                    val profiles = entities.map { entity ->
                        Profile(
                            id = entity.id,
                            name = entity.name,
                            colorHex = entity.colorHex,
                            isDefault = entity.isDefault,
                            createdAt = entity.createdAt,
                            lastUsedAt = entity.lastUsedAt
                        )
                    }
                    adapter.submitList(profiles)
                }
            }
        }
    }

    private fun showContextMenu(profile: Profile, anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.profile_context_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    onEditProfile?.invoke(profile)
                    dismiss()
                    true
                }
                R.id.action_delete -> {
                    onDeleteProfile?.invoke(profile)
                    dismiss()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    companion object {
        private const val ARG_ACTIVE_ID = "active_id"

        fun newInstance(activeProfileId: String?): DashboardBottomSheet {
            return DashboardBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ACTIVE_ID, activeProfileId)
                }
            }
        }
    }
}
