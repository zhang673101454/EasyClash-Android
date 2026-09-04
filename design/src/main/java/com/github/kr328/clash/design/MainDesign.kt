package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.databinding.DialogProfilesMenuBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.patchDataSet
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context), ProfileMenuHandler {
    sealed class Request {
        object OpenProxy : Request()
        object ShowHome : Request()
        object Create : Request()
        object UpdateAll : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    private val adapter = ProfileAdapter(context, this::requestActive, this::showMenu)

    override val root: View
        get() = binding.root

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
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

    suspend fun patchProfiles(profiles: List<Profile>) {
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
        binding.mode = context.getString(R.string.smart_mode)
        binding.appAuthor = context.getString(R.string.app_author)
        binding.appVersion = context.getString(R.string.app_version)
        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)
        binding.profileList.applyLinearAdapter(context, adapter)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    /** 布局点击：避免 DataBinding 直接引用 sealed object（需 .INSTANCE，kapt 易挂）。 */
    fun requestShowHome() {
        requests.trySend(Request.ShowHome)
    }

    fun requestOpenProxy() {
        requests.trySend(Request.OpenProxy)
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
    }

    private fun requestActive(profile: Profile) {
        requests.trySend(Request.Active(profile))
    }

    private fun showMenu(profile: Profile) {
        val dialog = AppBottomSheetDialog(context)
        val menuBinding = DialogProfilesMenuBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)
        menuBinding.master = this
        menuBinding.self = dialog
        menuBinding.profile = profile
        dialog.setContentView(menuBinding.root)
        dialog.show()
    }

    override fun requestUpdate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Update(profile))
        dialog.dismiss()
    }

    override fun requestEdit(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Edit(profile))
        dialog.dismiss()
    }

    override fun requestDuplicate(dialog: Dialog, profile: Profile) {
        dialog.dismiss()
    }

    override fun requestDelete(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Delete(profile))
        dialog.dismiss()
    }
}
