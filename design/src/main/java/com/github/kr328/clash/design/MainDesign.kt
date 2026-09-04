package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.patchDataSet
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    sealed class Request {
        object OpenProxy : Request()
        object ShowHome : Request()
        object Create : Request()
        object UrlTest : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = ProfileAdapter(
        context,
        onClicked = { requests.trySend(Request.Active(it)) },
        onUpdate = { requests.trySend(Request.Update(it)) },
        onEdit = { requests.trySend(Request.Edit(it)) },
        onDelete = { requests.trySend(Request.Delete(it)) },
    )

    override val root: View
        get() = binding.root

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
            adapter.clashRunning = running
            adapter.notifyDataSetChanged()
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = context.getString(R.string.smart_mode)
        }
    }

    suspend fun setHomeTab(home: Boolean) {
        withContext(Dispatchers.Main) {
            binding.homeTab = home
        }
    }

    suspend fun setUrlTesting(testing: Boolean) {
        withContext(Dispatchers.Main) {
            binding.urlTesting = testing
        }
    }

    suspend fun attachProxy(view: View) {
        withContext(Dispatchers.Main) {
            val host = binding.proxyHost
            host.removeAllViews()
            (view.parent as? ViewGroup)?.removeView(view)
            host.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    suspend fun clearProxy() {
        withContext(Dispatchers.Main) {
            binding.proxyHost.removeAllViews()
        }
    }

    suspend fun patchProfiles(profiles: List<Profile>) {
        adapter.clashRunning = binding.clashRunning
        adapter.patchDataSet(adapter::profiles, profiles, id = { it.uuid })
    }

    fun updateElapsed() {
        adapter.updateElapsed()
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                requests.trySend(Request.Edit(profile))
            }
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val about = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }
            AlertDialog.Builder(context).setView(about.root).show()
        }
    }

    init {
        binding.self = this
        binding.homeTab = true
        binding.urlTesting = false
        binding.mode = context.getString(R.string.smart_mode)
        binding.appAuthor = context.getString(R.string.app_author)
        binding.appVersion = context.getString(R.string.app_version)
        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)
        binding.profileList.applyLinearAdapter(context, adapter)
    }

    fun requestShowHome() {
        requests.trySend(Request.ShowHome)
    }

    fun requestOpenProxy() {
        requests.trySend(Request.OpenProxy)
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
    }

    fun requestUrlTest() {
        requests.trySend(Request.UrlTest)
    }
}
