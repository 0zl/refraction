package shiro.refraction.ui.dialog

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import shiro.refraction.R
import shiro.refraction.data.model.NetworkRequest
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class RequestAdapter(
    private val onRequestClick: (NetworkRequest) -> Unit
) : ListAdapter<NetworkRequest, RequestAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMethod: TextView = itemView.findViewById(R.id.tvMethod)
        private val tvUrl: TextView = itemView.findViewById(R.id.tvUrl)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvSource: TextView = itemView.findViewById(R.id.tvSource)

        fun bind(request: NetworkRequest) {
            tvMethod.text = request.method
            tvMethod.setBackgroundColor(methodColor(request.method))

            tvUrl.text = try {
                val uri = URI(request.url)
                val path = uri.path ?: "/"
                val query = uri.query
                if (query != null) "$path?$query" else path
            } catch (e: Exception) {
                request.url
            }

            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            tvTimestamp.text = sdf.format(Date(request.timestamp))

            tvStatus.text = if (request.responseCode > 0) request.responseCode.toString() else "---"
            tvStatus.setTextColor(statusColor(request.responseCode))

            tvSource.text = request.source.name

            itemView.setOnClickListener { onRequestClick(request) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_network_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun methodColor(method: String): Int = when (method.uppercase()) {
        "GET" -> Color.parseColor("#4CAF50")
        "POST" -> Color.parseColor("#2196F3")
        "PUT" -> Color.parseColor("#FF9800")
        "DELETE" -> Color.parseColor("#F44336")
        "PATCH" -> Color.parseColor("#9C27B0")
        "HEAD" -> Color.parseColor("#9E9E9E")
        "OPTIONS" -> Color.parseColor("#009688")
        "WS_UP" -> Color.parseColor("#00897B")
        "WS_IN" -> Color.parseColor("#26A69A")
        "WS_OUT" -> Color.parseColor("#00796B")
        "WS_CLOSE" -> Color.parseColor("#4DB6AC")
        else -> Color.parseColor("#607D8B")
    }

    private fun statusColor(code: Int): Int = when {
        code <= 0 -> Color.parseColor("#9E9E9E")
        code < 300 -> Color.parseColor("#4CAF50")
        code < 400 -> Color.parseColor("#FF9800")
        else -> Color.parseColor("#F44336")
    }

    class DiffCallback : DiffUtil.ItemCallback<NetworkRequest>() {
        override fun areItemsTheSame(old: NetworkRequest, new: NetworkRequest) =
            old.timestamp == new.timestamp && old.url == new.url

        override fun areContentsTheSame(old: NetworkRequest, new: NetworkRequest) = old == new
    }
}
