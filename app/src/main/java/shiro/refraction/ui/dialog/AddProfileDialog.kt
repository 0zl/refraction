package shiro.refraction.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.setMargins
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import shiro.refraction.R
import shiro.refraction.data.model.Profile
import shiro.refraction.ui.main.MainViewModel
import shiro.refraction.util.Constants
import shiro.refraction.util.dpToPx

class AddProfileDialog : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private var editingProfileId: String? = null
    private var editingProfile: Profile? = null
    private var selectedColor: String = Constants.MATERIAL_COLORS.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingProfileId = arguments?.getString(ARG_PROFILE_ID)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_profile_form, null)

        val nameInput = view.findViewById<EditText>(R.id.etName)
        val colorContainer = view.findViewById<LinearLayout>(R.id.colorContainer)

        editingProfileId?.let { id ->
            editingProfile = viewModel.profiles.value.find { it.id == id }
        }

        editingProfile?.let { profile ->
            nameInput.setText(profile.name)
            selectedColor = profile.colorHex
        }

        setupColorPicker(colorContainer)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (editingProfile != null) R.string.edit_profile else R.string.add_profile)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (editingProfile != null) {
                        viewModel.updateProfile(
                            editingProfile!!.copy(name = name, colorHex = selectedColor)
                        )
                    } else {
                        viewModel.createProfile(name, selectedColor)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun setupColorPicker(container: LinearLayout) {
        container.removeAllViews()
        val size = 40.dpToPx(requireContext())
        val margin = 8.dpToPx(requireContext())

        Constants.MATERIAL_COLORS.forEach { colorHex ->
            val view = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin)
                }
                background = requireContext().getDrawable(R.drawable.circle_color)?.apply {
                    setTint(Color.parseColor(colorHex))
                }
                tag = colorHex
                setOnClickListener {
                    selectedColor = colorHex
                    highlightSelection(container, colorHex)
                }
            }
            container.addView(view)
        }

        highlightSelection(container, selectedColor)
    }

    private fun highlightSelection(container: LinearLayout, selectedHex: String) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val hex = child.tag as? String
            child.foreground = if (hex.equals(selectedHex, ignoreCase = true)) {
                requireContext().getDrawable(R.drawable.circle_selected)
            } else {
                null
            }
        }
    }

    companion object {
        private const val ARG_PROFILE_ID = "profile_id"

        fun newInstance(profile: Profile): AddProfileDialog {
            return AddProfileDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROFILE_ID, profile.id)
                }
            }
        }
    }
}
