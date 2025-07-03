package uz.zero_one.supportbot

import jakarta.validation.constraints.Pattern
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod
import java.util.UUID

data class CreateOperatorRequest(
    val userName: String,
    @field:Pattern(
        regexp = "^\\+998\\d{9}$",
        message = "Telefon raqam +998 bilan boshlanib 13 ta belgidan iborat bo'lishi kerak"
    )
    val phoneNumber: String,
    val passWord: String,
    val languages: List<Language>
)

data class OperatorResponse(
    val id: Long,
    val chatId: Long?,
    val userName: String,
    val phoneNumber: String,
    val languages: List<Language>,
    val roles: List<UserRole>
)

data class JwtResponseDto(
    val accessToken: String,
    val refreshToken: String?
) {
    constructor(access: String?) : this("", access)
}

data class CreateAdminRequest(
    val chatId: Long,
    val userName: String,
    val passWord: String,
    val phoneNumber: String
)

data class AdminResponse(
    val id: Long,
    val chatId: Long,
    val userName: String,
    val phoneNumber: String,
)

data class UserSession(
    val chatId: Long,
    var state: UserState? = null,
    var userName: String? = null,
    var phoneNumber: String? = null,
    var language: Language? = null,
    var lastSessionId: UUID? = null,
    var lastOperatorId: Long? = null,
)

data class OperatorStatsDTO(
    val onlineStatus: String,
    val servedUsers: Long,
    val totalQuestions: Long,
    val answered: Long,
    val unanswered: Long,
    val avgResponseSeconds: Long,
    val avgChatDurationSeconds: Long
)

data class OnlineResult(
    val messages: List<PartialBotApiMethod<*>>,
    val nextUser: UserEntity?
)

