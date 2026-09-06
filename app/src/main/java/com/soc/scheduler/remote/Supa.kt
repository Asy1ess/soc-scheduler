package com.soc.scheduler.remote

import com.soc.scheduler.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase 클라이언트.
 *
 * 키는 local.properties 에서 BuildConfig 로 주입된다. 값이 비어 있으면
 * [isConfigured] 가 false 가 되고 친구 기능이 통째로 숨겨진다.
 * 즉 키가 없어도 앱은 기존처럼 완전한 로컬 전용으로 동작한다.
 */
object Supa {

    const val DEEPLINK_SCHEME = "socscheduler"
    const val DEEPLINK_HOST = "login-callback"

    val isConfigured: Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient by lazy {
        require(isConfigured) { "Supabase 키가 설정되지 않았습니다." }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                scheme = DEEPLINK_SCHEME
                host = DEEPLINK_HOST
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
            install(Postgrest)
        }
    }
}
