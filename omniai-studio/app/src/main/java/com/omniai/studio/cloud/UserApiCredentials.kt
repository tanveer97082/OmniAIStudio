package com.omniai.studio.cloud

import com.omniai.studio.data.remote.KaggleAccountCredential
import com.omniai.studio.data.remote.CompatibleAiProviderConfig
import com.omniai.studio.data.remote.PlatformApiCredential
import kotlinx.serialization.Serializable

@Serializable
data class UserApiCredentials(
    val primaryTunnelUrl: String = "",
    val primaryTunnelApiKey: String = "",
    val primaryModelName: String = "dolphin-mistral",
    val huggingFaceToken: String = "",
    val huggingFaceModel: String = "Qwen/Qwen2.5-72B-Instruct",
    val geminiApiKey: String = "",
    val autoFallbackEnabled: Boolean = true,
    val compatibleAiProviders: List<CompatibleAiProviderConfig> = emptyList(),
    val platformApiCredentials: List<PlatformApiCredential> = emptyList(),
    val braveSearchApiKey: String = "",
    val kaggleUsername: String = "",
    val kaggleApiKey: String = "",
    val kaggleDatasetSlug: String = "video-tasks-input",
    val kaggleKernelSlug: String = "omniai-wan21-dual-gpu-worker",
    val kaggleAccountPool: List<KaggleAccountCredential> = emptyList(),
    val activeKaggleAccountId: String = "",
    val githubRepo: String = "",
    val githubToken: String = "",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val telegramApiId: String = "",
    val telegramApiHash: String = "",
    val customWebhookUrl: String = "",
    val ytClientId: String = "",
    val ytClientSecret: String = "",
    val ytRefreshToken: String = "",
    val ytChannelLabel: String = "Primary Channel",
    val ytProfilesJson: String = "[]",
    val activeYtProfileId: String = "",
    val metaPageId: String = "",
    val metaIgUserId: String = "",
    val metaPageAccessToken: String = ""
)

object UserApiCredentialsSchema {
    const val SQL = """
create table if not exists public.user_api_credentials (
    user_id uuid primary key references auth.users(id) on delete cascade,
    credentials jsonb not null default '{}'::jsonb
        check (jsonb_typeof(credentials) = 'object'),
    updated_at bigint not null default ((extract(epoch from now()) * 1000)::bigint)
);

alter table public.user_api_credentials enable row level security;
alter table public.user_api_credentials force row level security;

revoke all on public.user_api_credentials from anon, public;
grant select, insert, update, delete on public.user_api_credentials to authenticated;

drop policy if exists "user_api_credentials_select_self" on public.user_api_credentials;
drop policy if exists "user_api_credentials_insert_self" on public.user_api_credentials;
drop policy if exists "user_api_credentials_update_self" on public.user_api_credentials;
drop policy if exists "user_api_credentials_delete_self" on public.user_api_credentials;

create policy "user_api_credentials_select_self"
    on public.user_api_credentials for select to authenticated
    using (auth.uid() = user_id);
create policy "user_api_credentials_insert_self"
    on public.user_api_credentials for insert to authenticated
    with check (auth.uid() = user_id);
create policy "user_api_credentials_update_self"
    on public.user_api_credentials for update to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);
create policy "user_api_credentials_delete_self"
    on public.user_api_credentials for delete to authenticated
    using (auth.uid() = user_id);
"""
}
