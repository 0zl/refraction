package shiro.refraction.ui.dashboard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import shiro.refraction.R
import shiro.refraction.data.model.Profile
import shiro.refraction.util.toRelativeTime

class ProfileAdapter(
    private val activeProfileId: String?,
    private val onProfileClick: (Profile) -> Unit,
    private val onProfileLongClick: (Profile, View) -> Boolean
) : ListAdapter<Profile, ProfileAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: TextView = itemView.findViewById(R.id.tvAvatar)
        private val name: TextView = itemView.findViewById(R.id.tvName)
        private val lastUsed: TextView = itemView.findViewById(R.id.tvLastUsed)
        private val checkmark: ImageView = itemView.findViewById(R.id.ivCheckmark)

        fun bind(profile: Profile) {
            val initials = profile.name.take(2).uppercase()
            avatar.text = initials
            avatar.background.setTint(Color.parseColor(profile.colorHex))

            name.text = profile.name
            lastUsed.text = itemView.context.getString(
                R.string.last_used_format,
                profile.lastUsedAt.toRelativeTime(itemView.context)
            )

            val isActive = profile.id == activeProfileId
            checkmark.visibility = if (isActive) View.VISIBLE else View.GONE
            itemView.isSelected = isActive

            itemView.setOnClickListener { onProfileClick(profile) }
            itemView.setOnLongClickListener { onProfileLongClick(profile, itemView) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Profile>() {
        override fun areItemsTheSame(old: Profile, new: Profile) = old.id == new.id
        override fun areContentsTheSame(old: Profile, new: Profile) = old == new
    }
}
