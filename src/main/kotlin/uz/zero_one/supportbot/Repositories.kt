package uz.zero_one.supportbot

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.awt.print.Pageable
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<UserEntity, Long> {
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
interface RoleRepository : JpaRepository<RoleEntity, Long> {
    fun findByRole(role: UserRole): RoleEntity
}

@Repository
interface QuestionRepository : JpaRepository<QuestionEntity, Long> {

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

    fun findTopByUserAndOperatorAndIsAnsweredFalseOrderByCreatedAtDesc(
        user: UserEntity,
        operator: UserEntity
    ): QuestionEntity?

    @Query("SELECT q FROM QuestionEntity q WHERE q.user.id = :userId AND q.isAnswered = false AND q.sessionId = :sessionId")
    fun findUnansweredByUserAndSession(@Param("userId") userId: Long, @Param("sessionId") sessionId: UUID): List<QuestionEntity>

    fun findTop1BySessionIdOrderByCreatedAtDesc(sessionId: UUID): QuestionEntity?

    fun findByFromMessageId(fromMessageId: Int): QuestionEntity?

    fun findTopBySessionIdAndOperatorIdAndAnswerIsNotNullOrderByIdDesc(
        sessionId: UUID,
        operatorId: Long
    ): QuestionEntity?

    @Query("SELECT q FROM QuestionEntity q WHERE q.toMessageId = :toMessageId")
    fun findByToMessageId(toMessageId: Int): QuestionEntity?

    @Query("SELECT q FROM QuestionEntity q WHERE q.user.chatId = :chatId AND q.isAnswered = false AND q.sessionId = :sessionId")
    fun findUnansweredByUserChatIdAndSession(
        @Param("chatId") chatId: Long,
        @Param("sessionId") sessionId: UUID
    ): List<QuestionEntity>


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
interface QueueEntryRepository : JpaRepository<QueueEntryEntity, Long> {
    fun existsByUser(user: UserEntity): Boolean
    fun deleteByUser(user: UserEntity)
    fun findAllByOrderByCreatedAtAsc(): List<QueueEntryEntity>
    fun findByUser(user: UserEntity): QueueEntryEntity?

}

@Repository
interface OperatorRatingRepository : JpaRepository<OperatorRatingEntity, Long> {
    fun findByUserAndOperator(user: UserEntity, operator: UserEntity): OperatorRatingEntity?
    fun existsByUserAndOperatorAndSessionId(user: UserEntity, operator: UserEntity, sessionId: UUID): Boolean
}

