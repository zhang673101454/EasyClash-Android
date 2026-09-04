package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.adapter.ProxyPageAdapter
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 精简节点页：单列、按延迟排序、隐藏分组 Tab / FAB；测速由主界面右上角触发。
 */
class ProxyDesign(
    context: Context,
    @Suppress("UNUSED_PARAMETER") overrideMode: TunnelState.Mode?,
    groupNames: List<String>,
    uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {
    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private val binding = DesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    private var config = ProxyViewConfig(context, 1)

    private val adapter: ProxyPageAdapter
        get() = binding.pagesView.adapter!! as ProxyPageAdapter

    private var urlTesting: Boolean = false

    override val root: View = binding.root

    var onUrlTestingChanged: ((Boolean) -> Unit)? = null

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>
    ) {
        adapter.updateAdapter(position, proxies, selectable, parent, links)
        setUrlTesting(false)
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            adapter.requestRedrawVisible()
        }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    init {
        // 强制精简默认，覆盖旧版本地偏好
        uiStore.proxyLine = 1
        uiStore.proxySort = ProxySort.Delay
        uiStore.proxyExcludeNotSelectable = true

        binding.self = this

        binding.urlTestFloatView.visibility = View.GONE
        binding.activityBarLayout.visibility = View.GONE
        binding.tabLayoutView.visibility = View.GONE
        binding.elevationView.visibility = View.GONE

        if (groupNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.pagesView.visibility = View.GONE
        } else {
            binding.pagesView.apply {
                isUserInputEnabled = false
                adapter = ProxyPageAdapter(
                    surface,
                    config,
                    List(groupNames.size) { index ->
                        ProxyAdapter(config) { name ->
                            requests.trySend(Request.Select(index, name))
                        }
                    }
                ) { }
            }
        }
    }

    fun requestUrlTesting() {
        if (binding.pagesView.adapter == null) return
        setUrlTesting(true)
        requests.trySend(Request.ReloadAll)
        requests.trySend(Request.UrlTest(binding.pagesView.currentItem))
    }

    private fun setUrlTesting(testing: Boolean) {
        urlTesting = testing
        onUrlTestingChanged?.invoke(testing)
    }

    companion object {
        /** 只保留一组可选节点，去掉 GLOBAL / 自动 / 随机等。 */
        fun liteGroupNames(names: List<String>): List<String> {
            val filtered = names.filterNot { name ->
                val n = name.lowercase()
                n == "global" ||
                    n.contains("自动") ||
                    n.contains("随机") ||
                    n.contains("故障") ||
                    n.contains("负载") ||
                    n.contains("url-test") ||
                    n.contains("fallback") ||
                    n.contains("load-balance") ||
                    n.contains("loadbalance")
            }
            val preferred = filtered.firstOrNull { name ->
                val n = name.lowercase()
                n.contains("节点") || n.contains("proxy") || n.contains("选择") || n == "proxies"
            }
            val picked = preferred ?: filtered.firstOrNull() ?: names.firstOrNull()
            return listOfNotNull(picked)
        }
    }
}
