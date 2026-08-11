package com.cuentasclaras.app.data.group

import com.cuentasclaras.app.BuildConfig
import com.cuentasclaras.app.data.local.LocalCache
import com.cuentasclaras.app.data.mapper.toDomain
import com.cuentasclaras.app.data.offline.ConnectivityMonitor
import com.cuentasclaras.app.data.offline.OfflineRead
import com.cuentasclaras.app.data.offline.OfflineReadResult
import com.cuentasclaras.app.data.remote.GroupDto
import com.cuentasclaras.app.data.remote.GroupMemberDto
import com.cuentasclaras.app.data.remote.JoinGroupRequest
import com.cuentasclaras.app.data.remote.JoinGroupResponse
import com.cuentasclaras.domain.model.Group
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.GroupThemeId
import com.cuentasclaras.domain.model.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val client: SupabaseClient,
    private val localCache: LocalCache,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    suspend fun listMyGroups(): OfflineReadResult<List<Group>> {
        return OfflineRead.networkFirst(
            isOnline = connectivityMonitor.currentlyOnline(),
            remote = {
                client.from("groups")
                    .select()
                    .decodeList<GroupDto>()
                    .map { it.toDomain() }
                    .sortedBy { it.name.lowercase() }
            },
            readCache = {
                if (localCache.hasGroupsListSnapshot()) localCache.listGroups() else null
            },
            writeCache = { localCache.replaceGroups(it) },
        )
    }

    suspend fun getGroup(groupId: GroupId): OfflineReadResult<Group> {
        return OfflineRead.networkFirst(
            isOnline = connectivityMonitor.currentlyOnline(),
            remote = {
                client.from("groups")
                    .select {
                        filter { eq("id", groupId.value) }
                    }
                    .decodeSingle<GroupDto>()
                    .toDomain()
            },
            readCache = { localCache.getGroup(groupId) },
            writeCache = { localCache.upsertGroup(it) },
        )
    }

    suspend fun listMembers(groupId: GroupId): OfflineReadResult<List<GroupMember>> {
        return OfflineRead.networkFirst(
            isOnline = connectivityMonitor.currentlyOnline(),
            remote = {
                client.from("group_members")
                    .select(Columns.raw("*, profiles(id, display_name, email, avatar_url, created_at, updated_at)")) {
                        filter { eq("group_id", groupId.value) }
                    }
                    .decodeList<GroupMemberDto>()
                    .map { it.toDomain() }
                    .sortedBy { it.displayName.lowercase() }
            },
            readCache = {
                if (localCache.hasMembersSnapshot(groupId)) localCache.listMembers(groupId) else null
            },
            writeCache = { localCache.replaceMembers(groupId, it) },
        )
    }

    suspend fun createGroup(name: String): Group {
        val response = client.postgrest.rpc(
            function = "create_group",
            parameters = buildJsonObject { put("p_name", name.trim()) },
        )
        val group = response.decodeAs<GroupDto>().toDomain()
        localCache.upsertGroup(group)
        return group
    }

    suspend fun joinGroup(inviteCode: String): GroupId {
        val response = client.postgrest.rpc(
            function = "join_group_by_code",
            parameters = JoinGroupRequest(inviteCode = inviteCode.trim().uppercase()),
        )
        return GroupId(response.decodeAs<JoinGroupResponse>().groupId)
    }

    suspend fun rotateInviteCode(groupId: GroupId): String {
        val response = client.postgrest.rpc(
            function = "rotate_invite_code",
            parameters = buildJsonObject { put("p_group_id", groupId.value) },
        )
        return response.decodeAs<String>().trim('"')
    }

    suspend fun removeMember(groupId: GroupId, userId: UserId) {
        client.postgrest.rpc(
            function = "remove_group_member",
            parameters = buildJsonObject {
                put("p_group_id", groupId.value)
                put("p_user_id", userId.value)
            },
        )
    }

    suspend fun uploadGroupAvatar(groupId: GroupId, jpegBytes: ByteArray): String {
        val path = "${groupId.value}/avatar.jpg"
        client.storage.from(BUCKET).upload(
            path = path,
            data = jpegBytes,
        ) {
            upsert = true
            contentType = io.ktor.http.ContentType.Image.JPEG
        }
        val publicUrl = publicAvatarUrl(path)
        client.postgrest.rpc(
            function = "set_group_avatar",
            parameters = buildJsonObject {
                put("p_group_id", groupId.value)
                put("p_avatar_url", publicUrl)
            },
        )
        localCache.getGroup(groupId)?.let { cached ->
            localCache.upsertGroup(cached.copy(avatarUrl = publicUrl))
        }
        return publicUrl
    }

    suspend fun clearGroupAvatar(groupId: GroupId) {
        val path = "${groupId.value}/avatar.jpg"
        runCatching {
            client.storage.from(BUCKET).delete(listOf(path))
        }
        client.postgrest.rpc(
            function = "clear_group_avatar",
            parameters = buildJsonObject { put("p_group_id", groupId.value) },
        )
        localCache.getGroup(groupId)?.let { cached ->
            localCache.upsertGroup(cached.copy(avatarUrl = null))
        }
    }

    suspend fun setTheme(groupId: GroupId, themeId: GroupThemeId) {
        client.postgrest.rpc(
            function = "set_group_theme",
            parameters = buildJsonObject {
                put("p_group_id", groupId.value)
                put("p_theme_id", themeId.value)
            },
        )
        localCache.getGroup(groupId)?.let { cached ->
            localCache.upsertGroup(cached.copy(themeId = themeId))
        }
    }

    private fun publicAvatarUrl(path: String): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val cacheBust = System.currentTimeMillis()
        return "$base/storage/v1/object/public/$BUCKET/$path?t=$cacheBust"
    }

    private companion object {
        const val BUCKET = "group-avatars"
    }
}
