package uz.zero_one.supportbot

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.UUID

@Component
class TelegramBot(
    private val sessionManager: SessionManager,
    private val startHandler: StartHandler,
    private val registerHandler: RegistrationHandler,
    private val questionHandler: QuestionHandler,
    private val operatorHandler: OperatorHandler,
    private val userService: UserService,
    private val settingsHandler: SettingsHandler,
    private val localeUtils: LocaleUtils,
    private val messageService: MessageService,
    private val questionService: QuestionService
) : TelegramLongPollingBot() {

    override fun onUpdateReceived(update: Update?) {
        if (update == null) return

        if (update.hasMessage()) {
            val message = update.message
            val chatId = message.chatId
            val session = sessionManager.getSession(chatId)
            val locale = localeUtils.getLocale(session.language)

            if (message.hasText() || message.hasPhoto() || message.hasSticker()
                || message.hasVoice() || message.hasVideo() || message.hasAnimation()
                || message.hasAudio() || message.hasDocument() || message.hasLocation()
                || message.hasContact() || message.hasPoll() || message.hasVideoNote()
                || message.hasDice() || message.venue != null || message.game != null || message.invoice != null
            ) {
                val messageText = message.text
                val isCommand = messageText?.startsWith("/") == true
                if (isCommand && (session.state == UserState.ASKING_QUESTION || session.state == UserState.IN_CHAT)) {
                    val isOperator = userService.isOperator(chatId)
                    val warningText = if (isOperator)
                        "❗ Siz hozir foydalanuvchi bilan suhbatdasiz. Avval end_chat buyrug‘i bilan yakunlang."
                    else
                        "❗ Siz hozir operator bilan suhbatdasiz. Avval suhbat tugashini kuting."
                    execute(SendMessage(chatId.toString(), warningText))
                    return
                }

                if (messageText == BotConstants.START) {
                    val sendMessage = startHandler.handleStart(chatId)
                    execute(sendMessage)
                } else if (session.state == UserState.CHOOSE_LANGUAGE) {
                    val response = registerHandler.handleLanguageSelection(message, chatId, session)
                    execute(response)
                } else if (session.state == UserState.ENTER_USERNAME) {
                    val response = registerHandler.handleUsernameEntry(message, chatId, session)
                    execute(response)
                } else if (session.state == UserState.REGISTERED) {
                    val isSettings = messageText?.trim() == messageService.get("settings", locale).trim()
                    if (isSettings) {
                        execute(settingsHandler.handleSettings(chatId, session))
                    } else {
                        registerHandler.handleRegisteredState(message, chatId, session)?.let { execute(it) }
                    }
                } else if (session.state == UserState.SETTINGS) {
                    execute(settingsHandler.handleSettingsSelection(message, chatId, session))
                } else if (session.state == UserState.CHANGE_LANGUAGE) {
                    val backText = messageService.get("back", locale)
                    if (messageText != null && messageText.startsWith(backText)) {
                        execute(settingsHandler.handleSettings(chatId, session))
                        return
                    }
                    execute(settingsHandler.handleLanguageChange(message, chatId, session))
                } else if (session.state == UserState.CHANGE_USERNAME) {
                    execute(settingsHandler.handleUsernameChange(message, chatId, session))
                } else if (session.state == UserState.ASKING_QUESTION) {
                    val backText = messageService.get("back", locale)
                    if (messageText != null && messageText == backText) {
                        execute(registerHandler.homeKeyboard(chatId, session))
                        return
                    }
                    if (session.lastSessionId == null) {
                        session.lastSessionId = UUID.randomUUID()
                    }

                    val result = questionHandler.onUserSendQuestion(session, message)
                    result.forEach { method ->
                        val sentMessage = executeMethod(method)
                        val operatorChatId = extractChatId(method)
                        val sessionId = session.lastSessionId

                        if (operatorChatId != null && sentMessage != null && sessionId != null) {
                            val question = questionService.findLastBySessionId(sessionId)
                            if (question != null) {
                                question.toMessageId = sentMessage.messageId
                                questionService.save(question)
                            }
                        }
                    }

                } else if (session.state == UserState.OPERATOR_HOME || session.state == UserState.IN_CHAT) {
                    if (!userService.isOperator(chatId)) return
                    val text = message.text?.trim()
                    if (text == BotConstants.STATISTIC) {
                        execute(operatorHandler.onStatisticsRequested(chatId))
                    } else if (text == BotConstants.ONLINE) {
                        val onlineResult = operatorHandler.online(chatId)
                        val messages = onlineResult.messages
                        val nextUser = onlineResult.nextUser
                        if (nextUser != null) {
                            val session = sessionManager.getSession(nextUser.chatId!!)
                            val unansweredQuestions = if (session.lastSessionId != null) {
                                questionService.findAllUnansweredByUserAndSession(
                                    nextUser.id!!,
                                    session.lastSessionId!!
                                )
                            } else {
                                questionService.findAllUnansweredByUser(nextUser)
                            }

                            messages.zip(unansweredQuestions).forEach { (method, question) ->
                                val sentMessage = executeMethod(method)
                                val toMessageId = sentMessage?.messageId

                                if (toMessageId != null) {
                                    question.toMessageId = toMessageId
                                    questionService.save(question)
                                }
                            }
                        }
                    } else if (text == BotConstants.OFFLINE) {
                        val offline = operatorHandler.offline(chatId)
                        offline.forEach { executeMethod(it) }
                    } else if (text == BotConstants.END_CHAT) {
                        val responses = operatorHandler.onOperatorEndChat(chatId)
                        responses.forEach { executeMethod(it) }
                    } else if (session.state == UserState.IN_CHAT) {
                        val responses = operatorHandler.onOperatorSendAnswer(chatId, message)
                        responses.forEach { method ->
                            val sentMessage = executeMethod(method)
                            val userId = extractChatId(method)
                            if (sentMessage != null && userId != null) {
                                val session = sessionManager.getSession(userId)
                                if (session.lastSessionId == null) {
                                    val newSessionId = UUID.randomUUID()
                                    sessionManager.updateLastSessionId(userId, newSessionId)
                                }
                                val sessionId = session.lastSessionId!!
                                val lastQuestion = questionService.findLastBySessionId(sessionId)
                                if (lastQuestion != null) {
                                    lastQuestion.fromMessageId = message.messageId
                                    lastQuestion.toMessageId = sentMessage.messageId
                                    questionService.save(lastQuestion)
                                }
                            }
                        }
                    }
                }
            }
            if (message.hasContact()) {
                if (session.state == UserState.ENTER_PHONE) {
                    val handleContactMessage = registerHandler.handleContactMessage(message, chatId, session)
                    execute(handleContactMessage)
                } else if (session.state == UserState.CHANGE_PHONE) {
                    execute(settingsHandler.handlePhoneChange(message, chatId, session))
                }
            }
        } else if (update.hasCallbackQuery()) {
            val callbackQuery = update.callbackQuery
            val data = callbackQuery.data
            val chatId = callbackQuery.message.chatId
            val messageId = callbackQuery.message.messageId
            if (data.startsWith("rating:")) {
                val messages = operatorHandler.handleRating(chatId, data, messageId)
                messages.forEach { send -> execute(send) }
                val replyMarkup = EditMessageReplyMarkup().apply {
                    this.chatId = chatId.toString()
                    this.messageId = messageId
                    this.replyMarkup = null
                }
                execute(replyMarkup)
            }
        }
    }

    fun executeMethod(method: PartialBotApiMethod<*>): Message? {
        return when (method) {
            is SendMessage -> execute(method)
            is SendPhoto -> execute(method)
            is SendSticker -> execute(method)
            is SendVoice -> execute(method)
            is SendVideo -> execute(method)
            is SendAnimation -> execute(method)
            is SendAudio -> execute(method)
            is SendDocument -> execute(method)
            is SendLocation -> execute(method)
            is SendContact -> execute(method)
            is SendVenue -> execute(method)
            is SendDice -> execute(method)
            is SendVideoNote -> execute(method)
            is SendPoll -> execute(method)
            else -> {
                println("❗ Not supported method type: ${method::class.simpleName}")
                null
            }
        }
    }

    fun extractChatId(method: PartialBotApiMethod<*>): Long? {
        return when (method) {
            is SendMessage -> method.chatId.toLongOrNull()
            is SendPhoto -> method.chatId.toLongOrNull()
            is SendSticker -> method.chatId.toLongOrNull()
            is SendVoice -> method.chatId.toLongOrNull()
            is SendVideo -> method.chatId.toLongOrNull()
            is SendAnimation -> method.chatId.toLongOrNull()
            is SendAudio -> method.chatId.toLongOrNull()
            is SendDocument -> method.chatId.toLongOrNull()
            is SendLocation -> method.chatId.toLongOrNull()
            is SendContact -> method.chatId.toLongOrNull()
            is SendVenue -> method.chatId.toLongOrNull()
            is SendDice -> method.chatId.toLongOrNull()
            is SendVideoNote -> method.chatId.toLongOrNull()
            is SendPoll -> method.chatId.toLongOrNull()
            else -> null
        }
    }

    override fun getBotUsername(): String? {
        return getUserName
    }

    @Deprecated("Deprecated in Java")
    override fun getBotToken(): String? {
        return getToken
    }

    @Value("\${bot.name}")
    lateinit var getUserName: String

    @Value("\${bot.token}")
    lateinit var getToken: String

}