package com.cuentasclaras.app.data.group

import com.cuentasclaras.app.data.mapper.toDomain
import com.cuentasclaras.app.data.remote.GroupDto
import com.cuentasclaras.app.data.remote.GroupMemberDto
import com.cuentasclaras.app.data.remote.JoinGroupRequest
import com.cuentasclaras.app.data.remote.JoinGroupResponse
import com.cuentasclaras.domain.model.Group
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.UserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    suspend fun listMyGroups(): List<Group> {
        val rows = client.from("groups")
            .select()
            .decodeList<GroupDto>()
        return rows.map { it.toDomain() }.sortedBy { it.name.lowercase() }
    }

    suspend fun getGroup(groupId: GroupId): Group {
        return client.from("groups")
            .select {
                filter { eq("id", groupId.value) }
            }
            .decodeSingle<GroupDto>()
            .toDomain()
    }

    suspend fun listMembers(groupId: GroupId): List<GroupMember> {
        return client.from("group_members")
            .select(Columns.raw("*, profiles(id, display_name, email, avatar_url, created_at, updated_at)")) {
                filter { eq("group_id", groupId.value) }
            }
            .decodeList<GroupMemberDto>()
            .map { it.toDomain() }
            .sortedBy { it.displayName.lowercase() }
    }

    suspend fun createGroup(name: String): Group {
        val response = client.postgrest.rpc(
            function = "create_group",
            parameters = buildJsonObject { put("p_name", name.trim()) },
        )
        return response.decodeAs<GroupDto>().toDomain()
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
}
