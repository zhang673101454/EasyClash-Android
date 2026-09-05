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
import com.github.kr328.clash.design.databinding.DialogSubscriptionBinding
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.ValidatorHttpUrl
import com.github.kr328.clash.design.util.ValidatorNotBlank
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.patchDataSet
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    data class SubscriptionForm(val name: String, val url: String)

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
            if (!running) {
                refreshSubtitle(null)
            }
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(@Suppress("UNUSED_PARAMETER") mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            // 产品固定「智能模式」；节点名由 setCurrentNode 追加
            if (binding.clashRunning != true) {
                refreshSubtitle(null)
            }
        }
    }

    /** 标题副栏：智能模式|当前节点 */
    suspend fun setCurrentNode(nodeName: String?) {
        withContext(Dispatchers.Main) {
            refreshSubtitle(nodeName)
        }
    }

    private fun refreshSubtitle(nodeName: String?) {
        val clean = nodeName?.trim().orEmpty()
        val running = binding.clashRunning
        binding.mode = when {
            !running -> context.getString(R.string.smart_mode)
            clean.isEmpty() || clean == "?" ->
                context.getString(R.string.smart_mode_with_node, context.getString(R.string.waiting_node))
            else -> context.getString(R.string.smart_mode_with_node, clean)
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
        withContext(Dispatchers.Main) {
            adapter.clashRunning = binding.clashRunning
            binding.profilesEmpty = profiles.isEmpty()
        }
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

    suspend fun requestDeleteConfirm(profile: Profile): Boolean {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.delete_subscription)
                    .setMessage(context.getString(R.string.delete_subscription_warn, profile.name))
                    .setPositiveButton(R.string.delete) { _, _ -> cont.resume(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .setOnDismissListener { if (!cont.isCompleted) cont.resume(false) }
                    .show()
                    .also { dialog -> cont.invokeOnCancellation { dialog.dismiss() } }
            }
        }
    }

    /** 新增/编辑订阅：仅备注名称 + 订阅地址 */
    suspend fun requestSubscriptionForm(
        title: CharSequence,
        initialName: String,
        initialUrl: String,
    ): SubscriptionForm? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val form = DialogSubscriptionBinding.inflate(context.layoutInflater)
                form.nameField.setText(initialName)
                form.urlField.setText(initialUrl)
                if (initialName.isNotEmpty()) {
                    form.nameField.setSelection(initialName.length)
                }

                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(title)
                    .setView(form.root)
                    .setPositiveButton(R.string.ok, null)
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .setOnDismissListener { if (!cont.isCompleted) cont.resume(null) }
                    .create()

                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = form.nameField.text?.toString()?.trim().orEmpty()
                        val url = form.urlField.text?.toString()?.trim().orEmpty()
                        form.nameLayout.error = null
                        form.urlLayout.error = null

                        var valid = true
                        if (!ValidatorNotBlank(name)) {
                            form.nameLayout.error = context.getString(R.string.empty_name)
                            valid = false
                        }
                        if (!ValidatorHttpUrl(url)) {
                            form.urlLayout.error = context.getString(R.string.invalid_url)
                            valid = false
                        }
                        if (!valid) return@setOnClickListener

                        cont.resume(SubscriptionForm(name, url))
                        dialog.dismiss()
                    }
                }

                cont.invokeOnCancellation { dialog.dismiss() }
                dialog.show()
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
        binding.profilesEmpty = true
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
