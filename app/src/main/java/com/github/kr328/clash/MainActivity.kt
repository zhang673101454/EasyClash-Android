package com.github.kr328.clash

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * EasyClash 主界面：订阅列表 + 点卡开关代理 + 节点入口。
 * 多订阅互刷：先开能用的订阅，再对其它订阅点「更新」（经当前 VPN 隧道拉取）。
 */
class MainActivity : BaseActivity<MainDesign>() {
    override suspend fun main() {
        val design = MainDesign(this)
        setContentDesign(design)
        design.fetchStatus()
        design.fetchProfiles()

        val trafficTicker = ticker(TimeUnit.SECONDS.toMillis(1))
        val elapsedTicker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            design.setHomeTab(true)
                            design.fetchStatus()
                            design.fetchProfiles()
                        }
                        Event.ServiceRecreated,
                        Event.ClashStop,
                        Event.ClashStart,
                        Event.ProfileLoaded -> design.fetchStatus()
                        Event.ProfileChanged -> {
                            design.fetchStatus()
                            design.fetchProfiles()
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive { req ->
                    when (req) {
                        MainDesign.Request.ShowHome -> design.setHomeTab(true)
                        MainDesign.Request.OpenProxy -> {
                            design.setHomeTab(false)
                            startActivity(ProxyActivity::class.intent)
                        }
                        MainDesign.Request.Create ->
                            startActivity(NewProfileActivity::class.intent)
                        MainDesign.Request.UpdateAll -> Unit
                        is MainDesign.Request.Active -> design.toggleSubscription(req.profile)
                        is MainDesign.Request.Update -> design.updateSubscription(req.profile)
                        is MainDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(req.profile.uuid))
                        is MainDesign.Request.Delete ->
                            withProfile { delete(req.profile.uuid) }
                    }
                }
                if (clashRunning) {
                    trafficTicker.onReceive {
                        design.fetchTraffic()
                    }
                }
                if (activityStarted) {
                    elapsedTicker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private suspend fun MainDesign.fetchStatus() {
        setClashRunning(clashRunning)
        val state = withClash { queryTunnelState() }
        setMode(state.mode)
    }

    private suspend fun MainDesign.fetchProfiles() {
        withProfile { patchProfiles(queryAll()) }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash { setForwarded(queryTrafficTotal()) }
    }

    /**
     * 点订阅：未启用则启用并开代理；已启用且代理开着则关闭；已启用但代理关着则再开。
     */
    private suspend fun MainDesign.toggleSubscription(profile: Profile) {
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
            startVpn()
        }
    }

    /**
     * 更新订阅节点/流量。VPN 已开时走当前隧道（多订阅互刷）；结果经 onProfileUpdate* 回调提示。
     */
    private suspend fun MainDesign.updateSubscription(profile: Profile) {
        if (!profile.imported || profile.type == Profile.Type.File) {
            return
        }
        if (!clashRunning) {
            showToast(DesignR.string.subscription_refresh_need_proxy, ToastDuration.Long)
        }
        withProfile { update(profile.uuid) }
    }

    private suspend fun startVpn() {
        val vpnRequest = startClashService()
        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )
                if (result.resultCode == RESULT_OK) {
                    startClashService()
                }
            }
        } catch (_: Exception) {
            design?.showToast(DesignR.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if (uuid == null) return
        launch {
            design?.fetchProfiles()
            var name: String? = null
            withProfile { name = queryByUUID(uuid)?.name }
            design?.showToast(
                getString(DesignR.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }

    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if (uuid == null) return
        launch {
            val tip = if (!clashRunning) {
                getString(DesignR.string.subscription_refresh_need_proxy)
            } else {
                var name: String? = null
                withProfile { name = queryByUUID(uuid)?.name }
                getString(DesignR.string.toast_profile_updated_failed, name, reason)
            }
            design?.showToast(tip, ToastDuration.Long)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = registerForActivityResult(RequestPermission()) { }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
