package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterProfileBinding
import com.github.kr328.clash.design.model.ProfilePageState
import com.github.kr328.clash.design.ui.ObservableCurrentTime
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.service.model.Profile

class ProfileAdapter(
    private val context: Context,
    private val onClicked: (Profile) -> Unit,
    private val onUpdate: (Profile) -> Unit,
    private val onEdit: (Profile) -> Unit,
    private val onDelete: (Profile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder(val binding: AdapterProfileBinding) : RecyclerView.ViewHolder(binding.root)

    private val currentTime = ObservableCurrentTime()

    var profiles: List<Profile> = emptyList()
    var clashRunning: Boolean = false
    val states = ProfilePageState()

    fun updateElapsed() {
        currentTime.update()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterProfileBinding
                .inflate(context.layoutInflater, parent, false)
                .also { it.currentTime = currentTime }
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = profiles[position]
        val binding = holder.binding

        binding.profile = current
        binding.proxyOn = current.active && clashRunning
        binding.setClicked {
            onClicked(current)
        }
        binding.setUpdate {
            onUpdate(current)
        }
        binding.setEdit {
            onEdit(current)
        }
        binding.setDelete {
            onDelete(current)
        }
    }

    override fun getItemCount(): Int {
        return profiles.size
    }
}
