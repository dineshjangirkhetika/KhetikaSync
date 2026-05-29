package com.khetika.khetikasync.data.approval

import android.util.Log
import com.khetika.khetikasync.data.model.ApprovalFileDto
import com.khetika.khetikasync.data.model.ApprovalRequestDto
import com.khetika.khetikasync.data.model.ApprovalStepDto
import com.khetika.khetikasync.data.model.NotificationDto
import com.khetika.khetikasync.data.notification.NotificationRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class RequestWithSteps(
    val request: ApprovalRequestDto,
    val steps: List<ApprovalStepDto>,
    val files: List<ApprovalFileDto> = emptyList(),
) {
    val activeStep: ApprovalStepDto?
        get() = steps.sortedBy { it.level }
            .firstOrNull { it.status == STATUS_PENDING && it.allEarlierApproved(steps) }
}

const val STATUS_PENDING = "pending"
const val STATUS_APPROVED = "approved"
const val STATUS_REJECTED = "rejected"
const val STATUS_SENT_BACK = "sent_back"

const val NOTIF_PENDING_FOR_YOU = "pending_for_you"
const val NOTIF_APPROVED = "approved"
const val NOTIF_REJECTED = "rejected"
const val NOTIF_SENT_BACK = "sent_back"
const val NOTIF_RESUBMITTED = "resubmitted"
const val NOTIF_REMINDER = "reminder"

private fun ApprovalStepDto.allEarlierApproved(all: List<ApprovalStepDto>): Boolean =
    all.filter { it.requestId == requestId && it.level < level }
        .all { it.status == STATUS_APPROVED }

@Singleton
class ApprovalRepository @Inject constructor(
    private val postgrest: Postgrest,
    private val notificationRepository: NotificationRepository,
) {

    suspend fun createRequest(
        request: ApprovalRequestDto,
        approverUidsByLevel: List<String>,
        files: List<Pair<String, String>> = emptyList(), // (displayName, publicUrl)
    ): String {
        require(approverUidsByLevel.isNotEmpty()) { "At least one approver level required" }
        require(approverUidsByLevel.size == request.levels) {
            "approver count (${approverUidsByLevel.size}) must match levels (${request.levels})"
        }
        Log.d(TAG, "createRequest title='${request.title}' levels=${request.levels} files=${files.size}")

        return try {
            val inserted = postgrest.from(REQUESTS)
                .insert(request) { select() }
                .decodeSingle<ApprovalRequestDto>()
            val requestId = requireNotNull(inserted.id) { "Insert returned no id" }

            val steps = approverUidsByLevel.mapIndexed { index, approverUid ->
                ApprovalStepDto(
                    requestId = requestId,
                    level = index + 1,
                    approverUid = approverUid,
                )
            }
            postgrest.from(STEPS).insert(steps)

            if (files.isNotEmpty()) {
                val fileRows = files.map { (name, url) ->
                    ApprovalFileDto(requestId = requestId, fileName = name, fileUrl = url)
                }
                postgrest.from(FILES).insert(fileRows)
            }

            // Notify the first-level approver.
            notificationRepository.insert(
                NotificationDto(
                    recipientUid = approverUidsByLevel.first(),
                    type = NOTIF_PENDING_FOR_YOU,
                    title = "Pending your action",
                    body = inserted.title,
                    requestId = requestId,
                )
            )
            requestId
        } catch (e: Throwable) {
            Log.e(TAG, "createRequest FAILED", e)
            throw e
        }
    }

    /**
     * Title search across requests the user is related to:
     * requester themselves OR any-level approver. Case-insensitive, ranked
     * newest first, deduped by id.
     */
    suspend fun searchRelatedRequests(uid: String, titleQuery: String): List<ApprovalRequestDto> {
        val q = titleQuery.trim()
        if (q.isEmpty()) return emptyList()
        Log.d(TAG, "searchRelatedRequests(uid=$uid, q='$q')")
        return try {
            // 1) Requests where I'm the requester.
            val asRequester = postgrest.from(REQUESTS)
                .select(columns = Columns.ALL) {
                    filter {
                        eq("requester_uid", uid)
                        ilike("title", "%$q%")
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ApprovalRequestDto>()

            // 2) Requests where I'm a step approver.
            val mySteps = postgrest.from(STEPS)
                .select(columns = Columns.ALL) {
                    filter { eq("approver_uid", uid) }
                }
                .decodeList<ApprovalStepDto>()
            val approverRequestIds = mySteps.map { it.requestId }.distinct()
            val asApprover = if (approverRequestIds.isEmpty()) emptyList() else
                postgrest.from(REQUESTS)
                    .select(columns = Columns.ALL) {
                        filter {
                            isIn("id", approverRequestIds)
                            ilike("title", "%$q%")
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<ApprovalRequestDto>()

            (asRequester + asApprover).distinctBy { it.id }
        } catch (e: Throwable) {
            Log.e(TAG, "searchRelatedRequests FAILED", e)
            throw e
        }
    }

    suspend fun listMyRequests(
        requesterUid: String,
        department: String? = null,
        dateStartIso: String? = null,
        dateEndIso: String? = null,
    ): List<ApprovalRequestDto> {
        Log.d(TAG, "listMyRequests(uid=$requesterUid, department=$department, start=$dateStartIso, end=$dateEndIso)")
        return try {
            val all = postgrest.from(REQUESTS)
                .select(columns = Columns.ALL) {
                    filter {
                        eq("requester_uid", requesterUid)
                        department?.let { eq("department", it) }
                        dateStartIso?.let { gte("created_at", it) }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ApprovalRequestDto>()
            applyEndDateClientSide(all, dateEndIso)
        } catch (e: Throwable) {
            Log.e(TAG, "listMyRequests FAILED", e)
            throw e
        }
    }

    /**
     * Workaround for supabase-kt's filter DSL overwriting same-column filters:
     * apply the upper-bound date check client-side after the gte query returns.
     */
    private fun applyEndDateClientSide(
        rows: List<ApprovalRequestDto>,
        dateEndIso: String?,
    ): List<ApprovalRequestDto> {
        if (dateEndIso == null) return rows
        val endInstant = parseIsoToInstant(dateEndIso) ?: return rows
        val before = rows.size
        val out = rows.filter { req ->
            val createdAt = req.createdAt?.let(::parseIsoToInstant)
                ?: return@filter true   // Keep if we can't parse — better safe than to silently drop.
            createdAt.isBefore(endInstant)
        }
        Log.d(TAG, "applyEndDateClientSide before=$before after=${out.size} end=$dateEndIso")
        return out
    }

    /** Accept either offset form (`+00:00`, `+05:30`) or `Z` form. */
    private fun parseIsoToInstant(iso: String): java.time.Instant? {
        // Try OffsetDateTime first (handles +00:00, +05:30, also Z).
        runCatching { return java.time.OffsetDateTime.parse(iso).toInstant() }
        // Fallback: pure Instant (handles "…Z" only).
        runCatching { return java.time.Instant.parse(iso) }
        // Fallback: replace space with T (some Postgrest variants).
        runCatching {
            val fixed = iso.replaceFirst(' ', 'T')
            return java.time.OffsetDateTime.parse(fixed).toInstant()
        }
        return null
    }

    /**
     * Requests where [approverUid] has already approved at least one step,
     * regardless of the parent request's final status.
     */
    suspend fun listApprovedByMe(
        approverUid: String,
        department: String? = null,
        dateStartIso: String? = null,
        dateEndIso: String? = null,
    ): List<ApprovalRequestDto> {
        Log.d(TAG, "listApprovedByMe(uid=$approverUid, dept=$department, start=$dateStartIso, end=$dateEndIso)")
        return try {
            val mySteps = postgrest.from(STEPS)
                .select(columns = Columns.ALL) {
                    filter {
                        eq("approver_uid", approverUid)
                        eq("status", STATUS_APPROVED)
                    }
                }
                .decodeList<ApprovalStepDto>()
            if (mySteps.isEmpty()) return emptyList()

            val ids = mySteps.map { it.requestId }.distinct()
            val all = postgrest.from(REQUESTS)
                .select(columns = Columns.ALL) {
                    filter {
                        isIn("id", ids)
                        department?.let { eq("department", it) }
                        dateStartIso?.let { gte("created_at", it) }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ApprovalRequestDto>()
            applyEndDateClientSide(all, dateEndIso)
        } catch (e: Throwable) {
            Log.e(TAG, "listApprovedByMe FAILED", e)
            throw e
        }
    }

    suspend fun listActionableForMe(
        approverUid: String,
        department: String? = null,
        dateStartIso: String? = null,
        dateEndIso: String? = null,
    ): List<RequestWithSteps> {
        Log.d(TAG, "listActionableForMe(uid=$approverUid, dept=$department, start=$dateStartIso, end=$dateEndIso)")
        return try {
            val mySteps = postgrest.from(ACTIONABLE)
                .select(columns = Columns.ALL) {
                    filter { eq("approver_uid", approverUid) }
                }
                .decodeList<ApprovalStepDto>()
            if (mySteps.isEmpty()) return emptyList()

            val requestIds = mySteps.map { it.requestId }.distinct()
            val requestsRaw = postgrest.from(REQUESTS)
                .select(columns = Columns.ALL) {
                    filter {
                        isIn("id", requestIds)
                        department?.let { eq("department", it) }
                        dateStartIso?.let { gte("created_at", it) }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ApprovalRequestDto>()
            val requests = applyEndDateClientSide(requestsRaw, dateEndIso)
            val allSteps = postgrest.from(STEPS)
                .select(columns = Columns.ALL) {
                    filter { isIn("request_id", requestIds) }
                    order("level", Order.ASCENDING)
                }
                .decodeList<ApprovalStepDto>()
            val stepsByRequest = allSteps.groupBy { it.requestId }
            requests.mapNotNull { req ->
                val id = req.id ?: return@mapNotNull null
                RequestWithSteps(req, stepsByRequest[id].orEmpty())
            }
        } catch (e: Throwable) {
            Log.e(TAG, "listActionableForMe FAILED", e)
            throw e
        }
    }

    suspend fun getRequestWithSteps(requestId: String): RequestWithSteps? {
        Log.d(TAG, "getRequestWithSteps(id=$requestId)")
        return try {
            val request = postgrest.from(REQUESTS)
                .select(columns = Columns.ALL) {
                    filter { eq("id", requestId) }
                    limit(1)
                }
                .decodeSingleOrNull<ApprovalRequestDto>() ?: return null
            val steps = postgrest.from(STEPS)
                .select(columns = Columns.ALL) {
                    filter { eq("request_id", requestId) }
                    order("level", Order.ASCENDING)
                }
                .decodeList<ApprovalStepDto>()
            val files = postgrest.from(FILES)
                .select(columns = Columns.ALL) {
                    filter { eq("request_id", requestId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<ApprovalFileDto>()
            RequestWithSteps(request, steps, files)
        } catch (e: Throwable) {
            Log.e(TAG, "getRequestWithSteps FAILED", e)
            throw e
        }
    }

    /**
     * Hard-delete a request. Cascades to approval_steps, approval_request_files,
     * and notifications via existing ON DELETE CASCADE foreign keys.
     */
    suspend fun deleteRequest(requestId: String) {
        Log.d(TAG, "deleteRequest(id=$requestId)")
        try {
            postgrest.from(REQUESTS).delete {
                filter { eq("id", requestId) }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "deleteRequest FAILED", e)
            throw e
        }
    }

    /**
     * Send a reminder notification to the currently active approver of a
     * pending request. No-op if the request is not pending or has no active
     * step (e.g., everything's approved/rejected).
     */
    suspend fun remindApprover(requestId: String): String? {
        Log.d(TAG, "remindApprover(id=$requestId)")
        val data = getRequestWithSteps(requestId) ?: error("Request not found")
        if (data.request.status != STATUS_PENDING) {
            error("Request is not pending (status=${data.request.status})")
        }
        val active = data.activeStep ?: error("No active step found")
        notificationRepository.insert(
            NotificationDto(
                recipientUid = active.approverUid,
                type = NOTIF_REMINDER,
                title = "Reminder: request pending your action",
                body = data.request.title,
                requestId = requestId,
            )
        )
        return active.approverUid
    }

    suspend fun approveStep(stepId: String, requestId: String, note: String?) {
        actOnStep(stepId, STATUS_APPROVED, note)
        finalizeOrAdvance(requestId)
    }

    suspend fun rejectStep(stepId: String, requestId: String, note: String?) {
        actOnStep(stepId, STATUS_REJECTED, note)
        setRequestStatus(requestId, STATUS_REJECTED)
        notifyRequester(requestId, NOTIF_REJECTED, "Your request was rejected", note)
    }

    suspend fun sendBackStep(stepId: String, requestId: String, note: String?) {
        actOnStep(stepId, STATUS_SENT_BACK, note)
        setRequestStatus(requestId, STATUS_SENT_BACK)
        notifyRequester(requestId, NOTIF_SENT_BACK, "Your request was sent back for changes", note)
    }

    /**
     * Requester re-opens a sent_back request. All steps reset to pending and
     * acted_at/note clear. The optional [comment] is appended to description.
     * Any [newFiles] (displayName, publicUrl) are added to approval_request_files
     * — old files are KEPT so the approver can see what changed.
     */
    suspend fun resubmit(
        requestId: String,
        comment: String?,
        newFiles: List<Pair<String, String>> = emptyList(),
    ) {
        Log.d(TAG, "resubmit(id=$requestId, newFiles=${newFiles.size})")
        try {
            postgrest.from(STEPS).update(
                buildJsonObject {
                    put("status", STATUS_PENDING)
                    put("acted_at", JsonNull)
                    put("note", JsonNull)
                }
            ) {
                filter { eq("request_id", requestId) }
            }

            val request = postgrest.from(REQUESTS)
                .select(columns = Columns.ALL) {
                    filter { eq("id", requestId) }
                    limit(1)
                }
                .decodeSingleOrNull<ApprovalRequestDto>() ?: error("Request not found")
            val newDescription = buildString {
                request.description?.let { append(it); append("\n\n") }
                append("[Resubmit ")
                append(Instant.now().toString())
                append("]")
                if (!comment.isNullOrBlank()) { append(": "); append(comment.trim()) }
            }
            postgrest.from(REQUESTS).update(
                buildJsonObject {
                    put("status", STATUS_PENDING)
                    put("description", newDescription)
                }
            ) {
                filter { eq("id", requestId) }
            }

            if (newFiles.isNotEmpty()) {
                val fileRows = newFiles.map { (name, url) ->
                    ApprovalFileDto(requestId = requestId, fileName = name, fileUrl = url)
                }
                postgrest.from(FILES).insert(fileRows)
                Log.d(TAG, "Resubmit added ${newFiles.size} new file rows")
            }

            // Find level-1 approver and notify.
            val firstStep = postgrest.from(STEPS)
                .select(columns = Columns.ALL) {
                    filter {
                        eq("request_id", requestId)
                        eq("level", 1)
                    }
                    limit(1)
                }
                .decodeSingleOrNull<ApprovalStepDto>()
            firstStep?.let {
                notificationRepository.insert(
                    NotificationDto(
                        recipientUid = it.approverUid,
                        type = NOTIF_RESUBMITTED,
                        title = "Request resubmitted",
                        body = request.title,
                        requestId = requestId,
                    )
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "resubmit FAILED", e)
            throw e
        }
    }

    private suspend fun actOnStep(stepId: String, newStatus: String, note: String?) {
        Log.d(TAG, "actOnStep(id=$stepId, status=$newStatus)")
        try {
            postgrest.from(STEPS).update(
                buildJsonObject {
                    put("status", newStatus)
                    put("acted_at", Instant.now().toString())
                    put("note", if (note.isNullOrBlank()) JsonNull else JsonPrimitive(note))
                }
            ) {
                filter { eq("id", stepId) }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "actOnStep FAILED for stepId=$stepId", e)
            throw e
        }
    }

    /**
     * After an approval, either finalize (all done) or notify the next-level
     * approver that the request is now in their court.
     */
    private suspend fun finalizeOrAdvance(requestId: String) {
        val steps = postgrest.from(STEPS)
            .select(columns = Columns.ALL) {
                filter { eq("request_id", requestId) }
                order("level", Order.ASCENDING)
            }
            .decodeList<ApprovalStepDto>()
        if (steps.isNotEmpty() && steps.all { it.status == STATUS_APPROVED }) {
            setRequestStatus(requestId, STATUS_APPROVED)
            notifyRequester(requestId, NOTIF_APPROVED, "Request Approved", null)
            return
        }
        val nextPending = steps.firstOrNull { it.status == STATUS_PENDING } ?: return
        val request = postgrest.from(REQUESTS)
            .select(columns = Columns.ALL) {
                filter { eq("id", requestId) }
                limit(1)
            }
            .decodeSingleOrNull<ApprovalRequestDto>() ?: return
        notificationRepository.insert(
            NotificationDto(
                recipientUid = nextPending.approverUid,
                type = NOTIF_PENDING_FOR_YOU,
                title = "Pending your action",
                body = request.title,
                requestId = requestId,
            )
        )
    }

    private suspend fun setRequestStatus(requestId: String, status: String) {
        Log.d(TAG, "setRequestStatus(id=$requestId, status=$status)")
        postgrest.from(REQUESTS).update(
            buildJsonObject { put("status", status) }
        ) {
            filter { eq("id", requestId) }
        }
    }

    private suspend fun notifyRequester(
        requestId: String,
        type: String,
        title: String,
        body: String?,
    ) {
        val request = postgrest.from(REQUESTS)
            .select(columns = Columns.ALL) {
                filter { eq("id", requestId) }
                limit(1)
            }
            .decodeSingleOrNull<ApprovalRequestDto>() ?: return
        notificationRepository.insert(
            NotificationDto(
                recipientUid = request.requesterUid,
                type = type,
                title = title,
                body = body ?: request.title,
                requestId = requestId,
            )
        )
    }

    private companion object {
        const val TAG = "KhetikaApprovalRepo"
        const val REQUESTS = "approval_requests"
        const val STEPS = "approval_steps"
        const val FILES = "approval_request_files"
        const val ACTIONABLE = "actionable_steps"
    }
}
