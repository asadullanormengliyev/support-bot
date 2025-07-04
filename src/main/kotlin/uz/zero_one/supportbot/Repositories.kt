package uz.zero_one.supportbot

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.awt.print.Pageable
import java.util.UUID

@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
}

class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>,
    entityManager: EntityManager
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager),  BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb ->
        cb.equal(root.get<Boolean>("deleted"), false)
    }

    override fun findByIdAndDeletedFalse(id: Long): T? =
        findByIdOrNull(id)?.run { if (deleted) null else this }

    @Transactional
    override fun trash(id: Long): T? = findByIdOrNull(id)?.run {
        deleted = true
        save(this)
    }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)

    @Transactional
    override fun trashList(ids: List<Long>): List<T> = ids.map { trash(it)!! }
}

@Repository
interface UserRepository : BaseRepository<UserEntity> {
    fun findByChatId(chatId: Long): UserEntity?
    fun existsByChatId(id: Long): Boolean
    fun findByUserName(username: String): UserEntity?
    fun findByPhoneNumber(phoneNumber: String): UserEntity?

    @Query(
        """
        SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
        FROM UserEntity u 
        JOIN u.roles r 
        WHERE u.chatId = :chatId AND r.role = :role
        """
    )
    fun existsByChatIdAndRole(chatId: Long, role: UserRole): Boolean

    @Query(
        value = """
    SELECT u.*
    FROM user_entity u
    JOIN user_entity_roles ur ON u.id = ur.user_entity_id
    WHERE ur.roles_id = :roleId
      AND u.available = true
      AND u.in_chat = false
      AND u.in_chat_with_id IS NULL
      AND (:selectedLanguage IS NULL OR EXISTS (
          SELECT 1 FROM user_languages l WHERE l.user_id = u.id AND l.language = :selectedLanguage
      ))
    ORDER BY u.id ASC
    LIMIT 1
""",
        nativeQuery = true
    )
    fun findTopAvailableOperatorNative(
        @Param("roleId") roleId: Long,
        @Param("selectedLanguage") selectedLanguage: String?
    ): UserEntity?

}

@Repository
interface RoleRepository : BaseRepository<RoleEntity> {
    fun findByRole(role: UserRole): RoleEntity
}

@Repository
interface QuestionRepository : BaseRepository<QuestionEntity> {

    @Query(
        """
    SELECT COUNT(DISTINCT q.user.id)
    FROM QuestionEntity q
    WHERE q.operator = :operator AND q.createdAt >= CURRENT_DATE
"""
    )
    fun countDistinctUsersServedToday(@Param("operator") operator: UserEntity): Long

    fun countByOperator(operator: UserEntity): Long

    fun countByOperatorAndIsAnsweredTrue(operator: UserEntity): Long

    fun countByOperatorAndIsAnsweredFalse(operator: UserEntity): Long

    @Query(
        """
        SELECT AVG(TIMESTAMPDIFF(SECOND, q.createdAt, FUNCTION('NOW')))
        FROM QuestionEntity q
        WHERE q.operator = :operator AND q.isAnswered = true
        """
    )
    fun averageResponseTimeSeconds(@Param("operator") operator: UserEntity): Double?

    @Query(
        """
    select avg(duration_seconds) from (
        select extract(epoch from max(q.created_at) - min(q.created_at)) as duration_seconds
        from question_entity q
        where q.operator_chat_id = :operatorChatId
        group by q.user_chat_id
    ) as per_user_durations
""", nativeQuery = true
    )
    fun averageChatDurationSeconds(@Param("operatorChatId") operatorChatId: Long): Double?

    fun findAllByUserAndOperatorIsNullAndIsAnsweredFalseOrderByCreatedAt(user: UserEntity): List<QuestionEntity>

    @Query("SELECT q FROM QuestionEntity q WHERE q.user.id = :userId AND q.isAnswered = false AND q.sessionId = :sessionId")
    fun findUnansweredByUserAndSession(@Param("userId") userId: Long, @Param("sessionId") sessionId: UUID): List<QuestionEntity>

    fun findTop1BySessionIdOrderByCreatedAtDesc(sessionId: UUID): QuestionEntity?

    @Query(
        """
        SELECT CASE
            WHEN q.fromMessageId = :replyMessageId THEN q.toMessageId
            WHEN q.toMessageId = :replyMessageId THEN q.fromMessageId
        END
        FROM QuestionEntity q
        WHERE q.fromMessageId = :replyMessageId OR q.toMessageId = :replyMessageId
        """
    )
    fun findLinkedMessageIdByMessageId(@Param("replyMessageId") replyMessageId: Int): Int?

}

@Repository
interface QueueEntryRepository : BaseRepository<QueueEntryEntity> {
    fun existsByUser(user: UserEntity): Boolean
    fun findAllByOrderByCreatedAtAsc(): List<QueueEntryEntity>
    fun findByUser(user: UserEntity): QueueEntryEntity?
}

@Repository
interface OperatorRatingRepository : BaseRepository<OperatorRatingEntity> {
    fun existsByUserAndOperatorAndSessionId(user: UserEntity, operator: UserEntity, sessionId: UUID): Boolean
}

