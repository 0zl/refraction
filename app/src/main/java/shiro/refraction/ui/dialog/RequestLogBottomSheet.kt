package shiro.refraction.ui.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import org.json.JSONArray
import shiro.refraction.R
import shiro.refraction.data.model.NetworkRequest
import shiro.refraction.data.model.RequestSource
import shiro.refraction.ui.main.MainViewModel

class RequestLogBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: RequestAdapter
    private var selectedRequest: NetworkRequest? = null
    private var currentFilter: String? = null
    private var currentSourceFilter: RequestSource? = null
    private var apiOnly = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_request_log, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFullScreen()

        adapter = RequestAdapter { request -> showDetail(view, request) }

        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerRequests)
        recycler.adapter = adapter

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnClose)
        val btnExport = view.findViewById<MaterialButton>(R.id.btnExport)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroup)
        val switchApiOnly = view.findViewById<MaterialSwitch>(R.id.switchApiOnly)
        val btnBack = view.findViewById<MaterialButton>(R.id.btnBack)
        val btnCopyCurl = view.findViewById<MaterialButton>(R.id.btnCopyCurl)

        btnClose.setOnClickListener { dismiss() }

        btnExport.setOnClickListener { exportJson() }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentSourceFilter = null
            currentFilter = when {
                checkedIds.contains(R.id.chipGet) -> "GET"
                checkedIds.contains(R.id.chipPost) -> "POST"
                checkedIds.contains(R.id.chipPut) -> "PUT"
                checkedIds.contains(R.id.chipDelete) -> "DELETE"
                checkedIds.contains(R.id.chipWs) -> {
                    currentSourceFilter = RequestSource.WEBSOCKET
                    null
                }
                else -> null
            }
            updateList()
        }

        switchApiOnly.setOnCheckedChangeListener { _, isChecked ->
            apiOnly = isChecked
            updateList()
        }

        btnBack.setOnClickListener {
            selectedRequest = null
            view.findViewById<View>(R.id.detailContainer).visibility = View.GONE
            view.findViewById<View>(R.id.recyclerRequests).visibility = View.VISIBLE
            view.findViewById<View>(R.id.tvEmpty).visibility =
                if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        }

        btnCopyCurl.setOnClickListener {
            selectedRequest?.let { req ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("cURL", req.toCurl()))
                Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recordedRequests.collect { requests ->
                    tvTitle.text = getString(R.string.request_log_count, requests.size)
                    updateList()
                }
            }
        }
    }

    private fun updateList() {
        val requests = viewModel.recordedRequests.value
        val filtered = requests.filter { req ->
            (currentFilter == null || req.method.equals(currentFilter, ignoreCase = true)) &&
            (currentSourceFilter == null || req.source == currentSourceFilter) &&
            (!apiOnly || req.source == RequestSource.API || req.source == RequestSource.WEBSOCKET)
        }
        adapter.submitList(filtered)
        val recycler = view?.findViewById<View>(R.id.recyclerRequests) ?: return
        val tvEmpty = view?.findViewById<View>(R.id.tvEmpty) ?: return
        val detailContainer = view?.findViewById<View>(R.id.detailContainer) ?: return
        if (detailContainer.visibility == View.GONE) {
            tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showDetail(root: View, request: NetworkRequest) {
        selectedRequest = request

        root.findViewById<View>(R.id.recyclerRequests).visibility = View.GONE
        root.findViewById<View>(R.id.tvEmpty).visibility = View.GONE
        root.findViewById<View>(R.id.detailContainer).visibility = View.VISIBLE

        root.findViewById<TextView>(R.id.tvDetailUrl).text = request.url
        root.findViewById<TextView>(R.id.tvDetailStatus).text = buildString {
            append(request.method)
            append("  ")
            append(if (request.responseCode > 0) request.responseCode.toString() else "---")
            append("  ")
            append(request.source.name)
        }

        root.findViewById<TextView>(R.id.tvDetailReqHeaders).text = formatHeaders(request.headers)

        val reqBodyLabel = root.findViewById<TextView>(R.id.tvLabelReqBody)
        val reqBodyText = root.findViewById<TextView>(R.id.tvDetailReqBody)
        if (request.requestBody != null) {
            reqBodyLabel.visibility = View.VISIBLE
            reqBodyText.visibility = View.VISIBLE
            reqBodyText.text = request.requestBody
        } else {
            reqBodyLabel.visibility = View.GONE
            reqBodyText.visibility = View.GONE
        }

        root.findViewById<TextView>(R.id.tvDetailRespHeaders).text = formatHeaders(request.responseHeaders)

        val respBodyLabel = root.findViewById<TextView>(R.id.tvLabelRespBody)
        val respBodyText = root.findViewById<TextView>(R.id.tvDetailRespBody)
        if (request.responseBody != null) {
            respBodyLabel.visibility = View.VISIBLE
            respBodyText.visibility = View.VISIBLE
            respBodyText.text = request.responseBody
        } else {
            respBodyLabel.visibility = View.GONE
            respBodyText.visibility = View.GONE
        }
    }

    private fun formatHeaders(headers: Map<String, String>): String =
        headers.entries.joinToString("\n") { (k, v) -> "$k: $v" }

    private fun exportJson() {
        val requests = viewModel.recordedRequests.value
        if (requests.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_requests, Toast.LENGTH_SHORT).show()
            return
        }
        val jsonArray = JSONArray()
        requests.forEach { jsonArray.put(it.toJson()) }
        val json = jsonArray.toString(2)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.export_requests)))
    }

    private fun setupFullScreen() {
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }
}
