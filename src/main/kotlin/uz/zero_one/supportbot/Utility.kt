package uz.zero_one.supportbot

import org.springframework.stereotype.Component
import java.util.Locale
import java.util.UUID

@Component
class SessionManager(private val userRepository: UserRepository) {
    private val sessions = mutableMapOf<Long, UserSession>()

    fun getSession(chatId: Long): UserSession {
        return sessions.getOrPut(chatId) {
            val user = userRepository.findByChatId(chatId)
            if (user != null) {
                UserSession(
                    chatId = user.chatId!!,
                    state = user.state,
                    userName = user.userName,
                    phoneNumber = user.phoneNumber,
                    language = user.selectedLanguage
                )
            } else {
                UserSession(chatId = chatId)
            }
        }
    }

    fun updateState(chatId: Long, newState: UserState) {
        val session = getSession(chatId)
        session.state = newState

        val user = userRepository.findByChatId(chatId)
        if (user != null) {
            user.state = newState
            userRepository.save(user)
        } else {
            println("User not found in DB when updating state for chatId: $chatId")
        }
    }

    fun updateLastSessionId(chatId: Long, newSessionId: UUID) {
        val session = getSession(chatId)
        session.lastSessionId = newSessionId
    }

    fun clearSession(chatId: Long) {
        sessions.remove(chatId)
    }


}

@Component
class LocaleUtils {

    fun getLocale(language: Language?): Locale {
        return when (language) {
            Language.RU -> Locale("ru")
            Language.EN -> Locale("en")
            else -> Locale("uz")
        }
    }
}
