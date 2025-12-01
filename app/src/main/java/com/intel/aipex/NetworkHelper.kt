package com.intel.aipex

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.OkHttpClient

class NetworkHelper(private val context: Context) {
    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    /** WIFI가 실제 인터넷 가능한지 확인 */
    fun isWifiHasInternet(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return hasInternet && validated
    }
    /** 인터넷 되는 MOBILE 네트워크 찾기 */
    fun getMobileNetwork(): Network? {
        return cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.let { caps ->
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: false
        }
    }
    /**
     * ⚠️ 프로세스 전체에 네트워크를 바인딩/해제하는 함수
     * 이 함수를 gRPC 호출 전후에 사용합니다.
     */
    private fun bindNetwork(network: Network?) {
        try {
            // network가 null이면 시스템 기본 네트워크 바인딩을 해제하고 원래대로 복구
            cm.bindProcessToNetwork(network)
        } catch (e: Exception) {
            // 바인딩 실패 시 로깅하거나 무시 (대부분의 Android 버전에서는 crash 발생하지 않음)
            e.printStackTrace()
        }
    }
    /**
     * ✅ gRPC (또는 HTTP) 호출을 특정 네트워크에 강제하고, 완료 후 원래대로 복구하는 함수
     * @param network 사용할 네트워크 (Mobile 또는 Wi-Fi)
     * @param block 네트워크가 바인딩된 상태에서 실행할 함수 (gRPC 호출)
     */
    suspend fun <T> withBoundNetwork(network: Network?, block: suspend () -> T): T {
        // 1. 현재 바인딩된 네트워크를 임시 저장합니다.
        // NOTE: Android API Level 23+ 에서만 사용할 수 있으며, 그 미만은 항상 null로 간주하고 진행해야 함.
        // 하지만 일반적으로 bindProcessToNetwork(null)이 해제 역할을 하므로, 안전하게 진행합니다.

        // 2. 새로운 네트워크 바인딩 설정
        bindNetwork(network)

        return try {
            // 3. gRPC 호출 실행
            block()
        } finally {
            // 4. 호출 완료 후, 반드시 바인딩을 해제하고 시스템 기본 설정으로 복구
            bindNetwork(null)
        }
    }
    /** OkHttpClient Builder */
    fun buildClientForNetwork(network: Network?): OkHttpClient {
        val builder = OkHttpClient.Builder()
        // socketFactory 사용 ❌ → crash 위험
        // 대신 process 를 특정 network에 바인딩
        if (network != null) {
            try {
                cm.bindProcessToNetwork(network)
            } catch (_: Exception) {
                // bind 실패하면 그대로 시스템 기본 네트워크 사용
            }
        } else {
            // null = 기본 네트워크 사용 (데이터 or WiFi)
            cm.bindProcessToNetwork(null)
        }
        return builder.build()
    }
}