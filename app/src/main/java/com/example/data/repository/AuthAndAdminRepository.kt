package com.example.data.repository

import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.local.PreferencesManager
import com.example.data.model.Invitation
import com.example.data.model.MemberProfile
import com.example.data.model.MemberRole
import com.example.data.model.MemberSecret
import com.example.data.model.MemberStatus
import com.example.security.E2eeCryptoManager
import com.example.security.PasswordHasher
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class AuthAndAdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val prefs: PreferencesManager,
    private val cryptoManager: E2eeCryptoManager
) {
    companion object {
        const val SUPER_ADMIN_EMAIL = "jonathan.mathews777@gmail.com"
        private val USERNAME_REGEX = Regex("^[a-z0-9_-]{3,30}$")
    }

    fun isSuperAdminEmail(email: String?, isVerified: Boolean): Boolean {
        return email.equals(SUPER_ADMIN_EMAIL, ignoreCase = false) && isVerified
    }

    fun observeMyProfile(uid: String): Flow<MemberProfile?> = flow {
        require(uid.isNotBlank())
        val path = "members/$uid"
        emitAll(
            db.collection("members").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(MemberProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeActiveMembers(uid: String): Flow<List<MemberProfile>> = flow {
        val path = "members"
        emitAll(
            db.collection("members")
                .whereEqualTo("status", MemberStatus.ACTIVE.name)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MemberProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.username }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllMembersForAdmin(uid: String): Flow<List<MemberProfile>> = flow {
        val path = "members"
        emitAll(
            db.collection("members")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MemberProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<MemberProfile> { it.status != MemberStatus.PENDING.name }.thenBy { it.username })
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeInvitationsForAdmin(uid: String): Flow<List<Invitation>> = flow {
        val path = "invitations"
        emitAll(
            db.collection("invitations")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun bootstrapSuperAdmin(
        usernameRaw: String,
        displayNameRaw: String,
        password: String
    ): Result<MemberProfile> {
        val currentUser = auth.currentUser ?: return Result.failure(IllegalStateException("Transport session not connected."))
        val uid = currentUser.uid
        val path = "members/$uid"
        return try {
            if (!isSuperAdminEmail(currentUser.email, currentUser.isEmailVerified)) {
                return Result.failure(SecurityException("Only the verified Super Administrator can initialize the admin account."))
            }
            val username = usernameRaw.trim().lowercase()
            val displayName = displayNameRaw.trim()
            require(USERNAME_REGEX.matches(username)) { "Username must be 3-30 characters (a-z, 0-9, _, -)." }
            require(displayName.length in 1..60) { "Display name must be 1-60 characters." }
            require(password.length >= 8) { "Password must be at least 8 characters." }

            val existingUser = db.collection("username_directory").document(username).get().await()
            if (existingUser.exists() && existingUser.getString("uid") != uid) {
                return Result.failure(IllegalArgumentException("Username @$username is already taken."))
            }

            val keys = cryptoManager.generateIdentityKeyPair()
            val hashed = PasswordHasher.hashPassword(password.toCharArray())

            val memberMap = mapOf(
                "uid" to uid,
                "username" to username,
                "displayName" to displayName,
                "role" to MemberRole.SUPER_ADMIN.name,
                "status" to MemberStatus.ACTIVE.name,
                "publicKeyBase64" to keys.publicKeyBase64,
                "keyFingerprint" to keys.fingerprint,
                "online" to true,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val secretMap = mapOf(
                "uid" to uid,
                "username" to username,
                "passwordHash" to hashed.hashBase64,
                "passwordSalt" to hashed.saltBase64,
                "email" to (currentUser.email ?: ""),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val directoryMap = mapOf(
                "uid" to uid,
                "username" to username,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(db.collection("members").document(uid), memberMap)
            batch.set(db.collection("member_secrets").document(uid), secretMap)
            batch.set(db.collection("username_directory").document(username), directoryMap)
            batch.commit().await()

            prefs.saveWrappedPrivateKey(uid, keys.wrappedPrivateKeyBase64)
            prefs.setSessionUnlocked(uid, username)

            Result.success(
                MemberProfile(
                    id = uid,
                    uid = uid,
                    username = username,
                    displayName = displayName,
                    role = MemberRole.SUPER_ADMIN.name,
                    status = MemberStatus.ACTIVE.name,
                    publicKeyBase64 = keys.publicKeyBase64,
                    keyFingerprint = keys.fingerprint,
                    online = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun redeemInviteAndRegister(
        inviteCodeRaw: String,
        usernameRaw: String,
        displayNameRaw: String,
        password: String
    ): Result<MemberProfile> {
        val currentUser = auth.currentUser ?: return Result.failure(IllegalStateException("Transport session not connected."))
        val uid = currentUser.uid
        val codeId = inviteCodeRaw.trim().uppercase()
        return try {
            require(codeId.isNotBlank() && codeId.matches(Regex("^[a-zA-Z0-9_-]{4,64}$"))) {
                "Invalid invitation code format."
            }
            val username = usernameRaw.trim().lowercase()
            val displayName = displayNameRaw.trim()
            require(USERNAME_REGEX.matches(username)) { "Username must be 3-30 characters (a-z, 0-9, _, -)." }
            require(displayName.length in 1..60) { "Display name must be 1-60 characters." }
            require(password.length >= 8) { "Password must be at least 8 characters." }

            val inviteSnap = db.collection("invitations").document(codeId).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("Invitation code not found."))
            }
            val invite = inviteSnap.toObject(Invitation::class.java)
                ?: return Result.failure(IllegalArgumentException("Invalid invitation record."))
            if (invite.used) {
                return Result.failure(IllegalStateException("This single-use invitation code has already been redeemed."))
            }
            if (invite.revoked) {
                return Result.failure(IllegalStateException("This invitation code has been revoked by the Super Administrator."))
            }
            val expiresAt = invite.expiresAt?.toDate()
            if (expiresAt != null && expiresAt.before(Date())) {
                return Result.failure(IllegalStateException("This invitation code has expired."))
            }

            val usernameSnap = db.collection("username_directory").document(username).get().await()
            if (usernameSnap.exists()) {
                return Result.failure(IllegalArgumentException("Username @$username is already taken."))
            }

            val keys = cryptoManager.generateIdentityKeyPair()
            val hashed = PasswordHasher.hashPassword(password.toCharArray())
            val initialStatus = if (invite.autoApprove) MemberStatus.ACTIVE.name else MemberStatus.PENDING.name

            val memberMap = mapOf(
                "uid" to uid,
                "username" to username,
                "displayName" to displayName,
                "role" to MemberRole.MEMBER.name,
                "status" to initialStatus,
                "publicKeyBase64" to keys.publicKeyBase64,
                "keyFingerprint" to keys.fingerprint,
                "online" to true,
                "inviteCodeId" to codeId,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val secretMap = mapOf(
                "uid" to uid,
                "username" to username,
                "passwordHash" to hashed.hashBase64,
                "passwordSalt" to hashed.saltBase64,
                "email" to (currentUser.email ?: ""),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val directoryMap = mapOf(
                "uid" to uid,
                "username" to username,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.update(
                db.collection("invitations").document(codeId),
                mapOf(
                    "used" to true,
                    "usedByUid" to uid,
                    "usedByUsername" to username,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(db.collection("members").document(uid), memberMap)
            batch.set(db.collection("member_secrets").document(uid), secretMap)
            batch.set(db.collection("username_directory").document(username), directoryMap)
            batch.commit().await()

            prefs.saveWrappedPrivateKey(uid, keys.wrappedPrivateKeyBase64)
            if (initialStatus == MemberStatus.ACTIVE.name) {
                prefs.setSessionUnlocked(uid, username)
            }

            Result.success(
                MemberProfile(
                    id = uid,
                    uid = uid,
                    username = username,
                    displayName = displayName,
                    role = MemberRole.MEMBER.name,
                    status = initialStatus,
                    publicKeyBase64 = keys.publicKeyBase64,
                    keyFingerprint = keys.fingerprint,
                    online = true,
                    inviteCodeId = codeId
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "invitations/$codeId")
            Result.failure(e)
        }
    }

    suspend fun loginWithUsernamePassword(usernameRaw: String, password: String): Result<MemberProfile> {
        val currentUser = auth.currentUser ?: return Result.failure(IllegalStateException("Transport session not connected."))
        val uid = currentUser.uid
        val path = "member_secrets/$uid"
        return try {
            val username = usernameRaw.trim().lowercase()
            require(USERNAME_REGEX.matches(username)) { "Invalid username format." }
            require(password.isNotEmpty()) { "Password is required." }

            val secretSnap = db.collection("member_secrets").document(uid).get().await()
            if (!secretSnap.exists()) {
                return Result.failure(IllegalStateException("No ChatX account bound to this device session. Please redeem an invite code."))
            }
            val secret = secretSnap.toObject(MemberSecret::class.java)
                ?: return Result.failure(IllegalStateException("Corrupted credential record."))

            if (!secret.username.equals(username, ignoreCase = true)) {
                return Result.failure(SecurityException("Invalid username or password."))
            }
            val valid = PasswordHasher.verifyPassword(password.toCharArray(), secret.passwordHash, secret.passwordSalt)
            if (!valid) {
                return Result.failure(SecurityException("Invalid username or password."))
            }

            val profileSnap = db.collection("members").document(uid).get().await()
            val profile = profileSnap.toObject(MemberProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Member profile missing or removed by Super Admin."))

            when (profile.parsedStatus) {
                MemberStatus.SUSPENDED -> return Result.failure(SecurityException("Account suspended by the Super Administrator."))
                MemberStatus.PENDING -> return Result.failure(IllegalStateException("Membership is awaiting Super Administrator approval."))
                MemberStatus.ACTIVE -> Unit
            }

            // Ensure local E2EE keypair exists on this device (re-generate and publish if device storage was cleared)
            if (prefs.getWrappedPrivateKey(uid).isNullOrBlank()) {
                val keys = cryptoManager.generateIdentityKeyPair()
                prefs.saveWrappedPrivateKey(uid, keys.wrappedPrivateKeyBase64)
                db.collection("members").document(uid).update(
                    mapOf(
                        "publicKeyBase64" to keys.publicKeyBase64,
                        "keyFingerprint" to keys.fingerprint,
                        "online" to true,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                db.collection("members").document(uid).update(
                    mapOf(
                        "online" to true,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            prefs.setSessionUnlocked(uid, username)
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun lockSession() {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("members").document(uid).update(
                mapOf(
                    "online" to false,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Best-effort presence offline
        }
        prefs.setSessionUnlocked(uid, null)
    }

    suspend fun createInvitation(label: String, autoApprove: Boolean, validHours: Long): Result<String> {
        val currentUser = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated."))
        val codeId = PasswordHasher.generateSingleUseInviteCode()
        val path = "invitations/$codeId"
        return try {
            val cleanLabel = label.trim().take(80)
            val expiresDate = Date(System.currentTimeMillis() + validHours.coerceIn(1L, 720L) * 3_600_000L)
            val payload = mapOf(
                "createdByUid" to currentUser.uid,
                "label" to cleanLabel,
                "autoApprove" to autoApprove,
                "used" to false,
                "revoked" to false,
                "expiresAt" to Timestamp(expiresDate),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("invitations").document(codeId).set(payload).await()
            Result.success(codeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeInvitation(codeId: String): Result<Unit> {
        val path = "invitations/$codeId"
        return try {
            db.collection("invitations").document(codeId).update(
                mapOf(
                    "revoked" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemberStatus(targetUid: String, newStatus: MemberStatus): Result<Unit> {
        val path = "members/$targetUid"
        return try {
            db.collection("members").document(targetUid).update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeMember(member: MemberProfile): Result<Unit> {
        val path = "members/${member.uid}"
        return try {
            val batch = db.batch()
            batch.delete(db.collection("members").document(member.uid))
            batch.delete(db.collection("member_secrets").document(member.uid))
            if (member.username.isNotBlank()) {
                batch.delete(db.collection("username_directory").document(member.username))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMyPhoneE164(phoneE164: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated."))
        val path = "members/$uid"
        return try {
            val sanitized = phoneE164.trim().take(24)
            db.collection("members").document(uid).update(
                mapOf(
                    "phoneE164" to sanitized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
