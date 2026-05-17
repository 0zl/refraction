package shiro.refraction.ui.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import shiro.refraction.R

class CookieBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_cookie, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cookieText = arguments?.getString(ARG_COOKIES) ?: ""
        val tvCookies = view.findViewById<MaterialTextView>(R.id.tvCookies)
        val btnCopy = view.findViewById<MaterialButton>(R.id.btnCopy)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnClose)

        tvCookies.text = if (cookieText.isNotEmpty()) cookieText else getString(R.string.no_cookies)

        btnCopy.setOnClickListener {
            if (cookieText.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(getString(R.string.cookies_label), cookieText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }

        btnClose.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        private const val ARG_COOKIES = "cookies"

        fun newInstance(cookies: String): CookieBottomSheet {
            return CookieBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_COOKIES, cookies)
                }
            }
        }
    }
}
