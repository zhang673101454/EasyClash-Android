package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class ProfilesActivity : BaseActivity<ProfilesDesign>() {
    override suspend fun main() {
        val design = ProfilesDesign(this)

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileChanged -> {
                            design.fetch()
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProfilesDesign.Request.Create ->
                            startActivity(NewProfileActivity::class.intent)
                        ProfilesDesign.Request.UpdateAll ->
                            withProfile {
                                try {
                                    queryAll().forEach { p ->
                                        if (p.imported && p.type != Profile.Type.File)
                                            update(p.uuid)
                                    }
                                }
                                finally {
                                    withContext(Dispatchers.Main) {
                                        design.finishUpdateAll();
                                    }
                                }
                            }
                        is ProfilesDesign.Request.Update -> {
                            if (!clashRunning) {
                                design.showToast(
                                    R.string.subscription_refresh_need_proxy,
                                    ToastDuration.Long
                                )
                            }
                            withProfile { update(it.profile.uuid) }
                        }
                        is ProfilesDesign.Request.Delete ->
                            withProfile { delete(it.profile.uuid) }
                        is ProfilesDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(it.profile.uuid))
                        is ProfilesDesign.Request.Active ->
                            design.toggleSubscription(it.profile)
                        is ProfilesDesign.Request.Duplicate -> Unit
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private suspend fun ProfilesDesign.fetch() {
        withProfile {
            patchProfiles(queryAll())
        }
    }

    private suspend fun ProfilesDesign.toggleSubscription(profile: Profile) {
        if (!profile.imported) {
            requestSave(profile)
            return
        }
        if (profile.active && clashRunning) {
            stopClashService()
            return
        }
        withProfile { setActive(profile) }
        if (!clashRunning) {
            val vpnRequest = startClashService()
            try {
                if (vpnRequest != null) {
                    val result = startActivityForResult(
                        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                        vpnRequest
                    )
                    if (result.resultCode == RESULT_OK) {
                        startClashService()
                    }
                }
            } catch (_: Exception) {
                showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
            }
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if (uuid == null) return
        launch {
            design?.fetch()
            var name: String? = null
            withProfile { name = queryByUUID(uuid)?.name }
            design?.showToast(
                getString(R.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if (uuid == null) return
        launch {
            val tip = if (!clashRunning) {
                getString(R.string.subscription_refresh_need_proxy)
            } else {
                var name: String? = null
                withProfile { name = queryByUUID(uuid)?.name }
                getString(R.string.toast_profile_updated_failed, name, reason)
            }
            design?.showToast(tip, ToastDuration.Long) {
                setAction(R.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }
}