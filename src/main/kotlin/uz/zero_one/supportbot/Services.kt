package uz.zero_one.supportbot

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.access.token.secretKey}")
    private val jwtAccessTokenSecretKey: String,

    @Value("\${jwt.access.token.expire.date}")
    private val jwtAccessTokenExpireDate: Long,

    @Value("\${jwt.refresh.token.secretKey}")
    private val jwtRefreshTokenSecretKey: String,

    @Value("\${jwt.refresh.token.expire.date}")
    private val jwtRefreshTokenExpireDate: Long
) {

    fun generateAccessToken(userEntity: UserEntity?): String {
        val date = Date()
        return Jwts.builder()
            .subject(userEntity?.userName)
            .issuedAt(date)
            .expiration(Date(date.time + jwtAccessTokenExpireDate))
            .signWith(getAccessTokenSecretKey())
            .claims(
                mapOf(
                    "roles" to userEntity?.authorities,
                )
            )
            .compact()
    }

    fun generateRefreshToken(userEntity: UserEntity): String {
        val date = Date()
        return Jwts.builder()
            .subject(userEntity.userName)
            .issuedAt(date)
            .expiration(Date(date.time + jwtRefreshTokenExpireDate))
            .signWith(getRefreshTokenSecretKey())
            .compact()
    }

    private fun getAccessTokenSecretKey(): SecretKey {
        return Keys.hmacShaKeyFor(jwtAccessTokenSecretKey.toByteArray())
    }

    private fun getRefreshTokenSecretKey(): SecretKey {
        return Keys.hmacShaKeyFor(jwtRefreshTokenSecretKey.toByteArray())
    }

    fun validateAccessToken(accessToken: String?) {
        try {
            val key = getAccessTokenSecretKey()
            Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken);
        } catch (e: SecurityException) {
            throw AuthenticationCredentialsNotFoundException("JWT was expired or incorrect")
        } catch (e: MalformedJwtException) {
            throw AuthenticationCredentialsNotFoundException("JWT was expired or incorrect")
        } catch (e: ExpiredJwtException) {
            throw AuthenticationCredentialsNotFoundException("Expired JWT token.")
        } catch (e: UnsupportedJwtException) {
            throw AuthenticationCredentialsNotFoundException("Unsupported JWT token.")
        } catch (e: IllegalArgumentException) {
            throw AuthenticationCredentialsNotFoundException("JWT token compact of handler are invalid.")
        }
    }

    fun validateRefreshToken(refreshToken: String?) {
        try {
            val key = getRefreshTokenSecretKey()
            Jwts.parser().verifyWith(key).build().parseSignedClaims(refreshToken)
        } catch (e: SecurityException) {
            throw AuthenticationCredentialsNotFoundException("JWT was expired or incorrect")
        } catch (e: MalformedJwtException) {
            throw AuthenticationCredentialsNotFoundException("JWT was expired or incorrect")
        } catch (e: ExpiredJwtException) {
            throw AuthenticationCredentialsNotFoundException("Expired JWT token.")
        } catch (e: UnsupportedJwtException) {
            throw AuthenticationCredentialsNotFoundException("Unsupported JWT token.")
        } catch (e: IllegalArgumentException) {
            throw AuthenticationCredentialsNotFoundException("JWT token compact of handler are invalid.")
        }
    }

    fun accessTokenClaims(accessToken: String?): Claims {
        return Jwts.parser().verifyWith(getAccessTokenSecretKey()).build().parseSignedClaims(accessToken).payload
    }

    fun refreshTokenClaims(refreshToken: String?): Claims {
        return Jwts.parser().verifyWith(getRefreshTokenSecretKey()).build().parseSignedClaims(refreshToken).payload
    }
}

interface OperatorService {
    fun create(request: CreateOperatorRequest): OperatorResponse
    fun updateOperatorLanguage(id: Long)
    fun getOperator(id: Long): OperatorResponse
    fun delete(id: Long)
}

interface AdminService {
    fun createAdmin(request: CreateAdminRequest): AdminResponse
    fun login(username: String, password: String): JwtResponseDto
}

@Service
class OperatorServiceImpl(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder
) : OperatorService {

    override fun create(request: CreateOperatorRequest): OperatorResponse {
        val toEntity = toEntity(request)
        val save = userRepository.save(toEntity)
        return toResponse(save)
    }

    override fun updateOperatorLanguage(id: Long) {
        TODO("Not yet implemented")
    }

    override fun getOperator(id: Long): OperatorResponse {
        TODO("Not yet implemented")
    }

    override fun delete(id: Long) {
        TODO("Not yet implemented")
    }

    fun toEntity(request: CreateOperatorRequest): UserEntity {
        return UserEntity(
            chatId = null,
            userName = request.userName,
            phoneNumber = request.phoneNumber,
            passWord = passwordEncoder.encode(request.passWord),
            languages = request.languages,
            roles = mutableListOf(operatorRole()),
            available = true,
            state = null
        )
    }

    fun operatorRole(): RoleEntity {
        return roleRepository.findByRole(UserRole.OPERATOR) ?: throw RoleNotFoundException("Role operator not found")
    }

    fun toResponse(user: UserEntity): OperatorResponse {
        return OperatorResponse(
            id = user.id!!,
            chatId = user.chatId,
            userName = user.userName,
            phoneNumber = user.phoneNumber,
            languages = user.languages,
            roles = user.roles.map { it.role }
        )
    }
}

@Service
class AdminServiceImpl(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) : AdminService {

    override fun createAdmin(request: CreateAdminRequest): AdminResponse {
        val adminRole =
            roleRepository.findByRole(UserRole.ADMIN) ?: throw RoleNotFoundException("Admin role not found")
        val admin = UserEntity(
            chatId = request.chatId,
            userName = request.userName,
            phoneNumber = request.phoneNumber,
            passWord = passwordEncoder.encode(request.passWord),
            roles = mutableListOf(adminRole),
            available = true,
            state = null
        )
        val save = userRepository.save(admin)
        return AdminResponse(
            id = save.id!!,
            chatId = save.chatId!!,
            userName = save.userName,
            phoneNumber = save.phoneNumber
        )
    }

    override fun login(username: String, password: String): JwtResponseDto {
        val userEntity = userRepository.findByUserName(username) ?: throw UsernameNotFoundException("User not found")
        val matches = passwordEncoder.matches(password, userEntity.password)
        if (!matches) {
            throw RuntimeException("password not found")
        }
        val generateAccessToken = jwtService.generateAccessToken(userEntity)
        val generateRefreshToken = jwtService.generateRefreshToken(userEntity)

        return JwtResponseDto("Bearer $generateAccessToken", generateRefreshToken)
    }
}

@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val entityManager: EntityManager
) {

    fun registerUser(session: UserSession) {
        val userRole = roleRepository.findByRole(UserRole.USER)
            ?: throw RoleNotFoundException("USER roli topilmadi")

        val user = UserEntity(
            chatId = session.chatId,
            userName = session.userName!!,
            phoneNumber = session.phoneNumber!!,
            roles = mutableListOf(userRole),
            selectedLanguage = session.language,
            state = UserState.REGISTERED
        )
        userRepository.save(user)
    }

    fun existsByChatId(chatId: Long): Boolean {
        return userRepository.existsByChatId(chatId)
    }

    fun isOperator(chatId: Long): Boolean {
        return userRepository.existsByChatIdAndRole(chatId, UserRole.OPERATOR)
    }

    fun getByChatId(chatId: Long): UserEntity {
        return userRepository.findByChatId(chatId)
            ?: throw EntityNotFoundException("User not found with chatId: $chatId")
    }

    @Transactional
    fun save(user: UserEntity): UserEntity {
        val saved = userRepository.save(user)
        entityManager.flush()
        entityManager.clear()
        return saved
    }

    fun findAvailableOperator(language: Language?): UserEntity? {
        val findByRoleId = findByRoleId()
        return userRepository.findTopAvailableOperatorNative(
            findByRoleId.id!!,
            language?.name
        )
    }

    fun findByRoleId(): RoleEntity {
        return roleRepository.findByRole(UserRole.OPERATOR)
    }

    fun getById(id: Long): UserEntity {
        return userRepository.findById(id).orElseThrow {
            RuntimeException("User not found with id: $id")
        }
    }

    @Transactional
    fun getByPhoneNumber(phone: String): UserEntity? {
        return userRepository.findByPhoneNumber(phone)
    }

    @Transactional
    fun isOperatorByPhone(phone: String): Boolean {
        val user = userRepository.findByPhoneNumber(phone) ?: return false
        return user.roles.any { it.role == UserRole.OPERATOR }
    }
}

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
    private val queueRepo: QueueEntryRepository
) {

    fun save(question: QuestionEntity): QuestionEntity {
        return questionRepository.save(question)
    }

    fun addToQueue(user: UserEntity) {
        if (!queueRepo.existsByUser(user)) {
            queueRepo.save(QueueEntryEntity(user = user))
        }
    }

    @Transactional
    fun removeFromQueue(user: UserEntity) {
        val entry = queueRepo.findByUser(user)
        if (entry != null) queueRepo.delete(entry)
    }

    fun isUserInQueue(user: UserEntity): Boolean {
        return queueRepo.existsByUser(user)
    }

    fun getNextUser(operatorLanguages: List<Language>): UserEntity? {
        val all = queueRepo.findAllByOrderByCreatedAtAsc()
        all.forEach {
            val u = it.user
            println("UserID=${u.id} | inChat=${u.inChat} | inChatWithId=${u.inChatWithId} | lang=${u.selectedLanguage}")
        }

        val found = all.map { it.user }
            .firstOrNull { user ->
                (!user.inChat && user.inChatWithId == null) && user.selectedLanguage != null && operatorLanguages.contains(user.selectedLanguage)
            }

        println("Topilgan navbatdagi user: ${found?.id}")
        return found
    }

    fun findAllUnansweredByUser(user: UserEntity): List<QuestionEntity> {
        return questionRepository.findAllByUserAndOperatorIsNullAndIsAnsweredFalseOrderByCreatedAt(user)
    }

    fun findAllUnansweredByUserAndSession(userId: Long, sessionId: UUID): List<QuestionEntity> {
        return questionRepository.findUnansweredByUserAndSession(userId, sessionId)
    }

    fun findLastBySessionId(sessionId: UUID): QuestionEntity? {
        return questionRepository.findTop1BySessionIdOrderByCreatedAtDesc(sessionId)
    }

    fun findOperatorMessageIdByUserMessageId(userMessageId: Int): Int? {
        return questionRepository.findLinkedMessageIdByMessageId(userMessageId)
    }

}

@Service
class OperatorStatisticsService(
    private val questionRepository: QuestionRepository
) {

    fun getStatistics(operator: UserEntity): OperatorStatsDTO {
        val onlineStatus = if (!operator.available && operator.inChat) "🟢 Online" else "🔴 Offline"
        val servedUsers = questionRepository.countDistinctUsersServedToday(operator)
        val total = questionRepository.countByOperator(operator)
        val answered = questionRepository.countByOperatorAndIsAnsweredTrue(operator)
        val unanswered = questionRepository.countByOperatorAndIsAnsweredFalse(operator)
        val avgResponse = questionRepository.averageResponseTimeSeconds(operator) ?: 0.0
        val avgChatDurations = questionRepository.averageChatDurationSeconds(operator.chatId!!)
        val avgChat = avgChatDurations ?: 0.0

        return OperatorStatsDTO(
            onlineStatus = onlineStatus,
            servedUsers = servedUsers,
            totalQuestions = total,
            answered = answered,
            unanswered = unanswered,
            avgResponseSeconds = avgResponse.toLong(),
            avgChatDurationSeconds = avgChat.toLong()
        )
    }
}

@Service
class OperatorRatingService(
    private val operatorRatingRepository: OperatorRatingRepository
) {

    fun saveRating(user: UserEntity, operator: UserEntity, stars: Int, sessionId: UUID) {
        val rating = OperatorRatingEntity(
            user = user,
            operator = operator,
            sessionId = sessionId,
            stars = stars
        )
        operatorRatingRepository.save(rating)
    }

    fun hasRated(user: UserEntity, operator: UserEntity, sessionId: UUID): Boolean {
        return operatorRatingRepository.existsByUserAndOperatorAndSessionId(user, operator, sessionId)
    }

}

@Service
class MessageService(
    private val messageSource: MessageSource
) {
    fun get(code: String, locale: Locale): String {
        return messageSource.getMessage(code, null, locale)
    }
}
