package uz.zero_one.supportbot

import jakarta.transaction.Transactional
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import java.io.Serializable
import java.util.UUID
import kotlin.collections.toMutableList

@Service
class StartHandler(
    private val messageService: MessageService,
    private val userService: UserService,
    private val localeUtils: LocaleUtils,
    private val sessionManager: SessionManager
) {

    fun handleStart(chatId: Long): SendMessage {
        val session = sessionManager.getSession(chatId)
        if (userService.isOperator(chatId)) {
            val operator = userService.getByChatId(chatId)
            operator.available = false
            operator.inChat = false
            operator.inChatWithId = null
            userService.save(operator)
            sessionManager.updateState(chatId, UserState.OPERATOR_HOME)
            return SendMessage(chatId.toString(), "🛠 Operator paneliga xush kelibsiz!")
                .apply { replyMarkup = operatorHomeKeyboard() }
        } else if (userService.existsByChatId(chatId)) {
            val user = userService.getByChatId(chatId)
            val locale = localeUtils.getLocale(user.selectedLanguage)
            session.language = user.selectedLanguage
            sessionManager.updateState(chatId, UserState.REGISTERED)
            val askButtonText = messageService.get("ask_question", locale)
            val welcome = messageService.get("welcome", locale)
            val keyboard = ReplyKeyboardMarkup(
                listOf(KeyboardRow().apply {
                    add(KeyboardButton(askButtonText))
                    add(KeyboardButton(messageService.get("settings", locale)))
                })
            ).apply { resizeKeyboard = true }

            return SendMessage(chatId.toString(), welcome).apply {
                replyMarkup = keyboard
            }
        }
        sessionManager.updateState(chatId, UserState.CHOOSE_LANGUAGE)
        return showLanguageSelection(chatId)
    }

    fun operatorHomeKeyboard(): ReplyKeyboardMarkup {
        val row = KeyboardRow().apply {
            add(KeyboardButton(BotConstants.STATISTIC))
            add(KeyboardButton(BotConstants.ONLINE))
            add(KeyboardButton("Settings"))
        }
        return ReplyKeyboardMarkup(listOf(row)).apply {
            resizeKeyboard = true
        }
    }

    private fun showLanguageSelection(chatId: Long): SendMessage {
        val keyboard = ReplyKeyboardMarkup(
            listOf(
                KeyboardRow().apply {
                    add(KeyboardButton("🇺🇿 Uz"))
                    add(KeyboardButton("🇷🇺 Ru"))
                    add(KeyboardButton("🇬🇧 En"))
                }
            )).apply {
            resizeKeyboard = true
            oneTimeKeyboard = true
        }

        return (SendMessage(chatId.toString(), "Iltimos, tilni tanlang:").apply {
            replyMarkup = keyboard
        })
    }

}

@Service
class RegistrationHandler(
    private val messageService: MessageService,
    private val userService: UserService,
    private val localeUtils: LocaleUtils,
    private val sessionManager: SessionManager,
    private val startHandler: StartHandler
) {

    fun handleLanguageSelection(message: Message, chatId: Long, session: UserSession): SendMessage {
        val selectedLanguage = when (message.text) {
            "🇺🇿 Uz" -> Language.UZ
            "🇷🇺 Ru" -> Language.RU
            "🇬🇧 En" -> Language.EN
            else -> null
        }
        val locale = localeUtils.getLocale(selectedLanguage ?: session.language)
        val text = if (selectedLanguage != null) {
            session.language = selectedLanguage
            sessionManager.updateState(chatId, UserState.ENTER_USERNAME)
            messageService.get("enter_name", locale)
        } else {
            messageService.get("invalid_language", locale)
        }
        return SendMessage(chatId.toString(), text)
    }

    fun handleUsernameEntry(message: Message, chatId: Long, session: UserSession): SendMessage {
        session.userName = message.text
        sessionManager.updateState(chatId, UserState.ENTER_PHONE)
        val locale = localeUtils.getLocale(session.language)
        val text = messageService.get("send_phone", locale)
        val contactButton = KeyboardButton(text).apply { requestContact = true }
        val keyboardRow = KeyboardRow().apply { add(contactButton) }
        val replyKeyboard = ReplyKeyboardMarkup(listOf(keyboardRow)).apply {
            resizeKeyboard = true
            oneTimeKeyboard = true
        }
        return SendMessage(chatId.toString(), text).apply { replyMarkup = replyKeyboard }
    }

    fun handleContactMessage(message: Message, chatId: Long, session: UserSession): SendMessage {
        val contact = message.contact

        if (contact.userId != message.from.id) {
            return SendMessage(
                chatId.toString(),
                messageService.get("invalid_contact_message", localeUtils.getLocale(session.language))
            )
        }

        val phone = message.contact.phoneNumber
        session.phoneNumber = phone
        val existingUser = userService.getByPhoneNumber(phone)
        val isOperator = userService.isOperatorByPhone(phone)

        return if (existingUser != null && isOperator) {
            existingUser.chatId = chatId
            existingUser.available = false
            existingUser.selectedLanguage = null
            existingUser.userName = session.userName.toString()
            userService.save(existingUser)
            sessionManager.updateState(chatId, UserState.OPERATOR_HOME)

            SendMessage(chatId.toString(), "👨‍💻 Siz operator sifatida tizimga muvaffaqiyatli kirdingiz!")
                .apply { replyMarkup = startHandler.operatorHomeKeyboard() }

        } else {
            sessionManager.updateState(chatId, UserState.REGISTERED)
            userService.registerUser(session)

            val locale = localeUtils.getLocale(session.language)
            val successText = messageService.get("register_success", locale)
            val askText = messageService.get("ask_question", locale)
            val settingsText = messageService.get("settings", locale)

            val keyboard = ReplyKeyboardMarkup(
                listOf(KeyboardRow().apply {
                    add(KeyboardButton(askText))
                    add(KeyboardButton(settingsText))
                })
            ).apply { resizeKeyboard = true }

            SendMessage(chatId.toString(), successText).apply { replyMarkup = keyboard }
        }
    }

    fun handleRegisteredState(message: Message, chatId: Long, session: UserSession): SendMessage? {
        val locale = localeUtils.getLocale(session.language)
        val askButtonText = messageService.get("ask_question", locale)
        val backText = messageService.get("back", locale)

        return if (message.text?.trim() == askButtonText.trim()) {
            sessionManager.updateState(chatId, UserState.ASKING_QUESTION)
            val text = messageService.get("write_question", locale)
            val keyboard = ReplyKeyboardMarkup(
                listOf(
                    KeyboardRow().apply {
                        add(KeyboardButton(backText))
                    }
                )
            ).apply {
                resizeKeyboard = true
                oneTimeKeyboard = true
            }

            SendMessage(chatId.toString(), text).apply {
                replyMarkup = keyboard
            }
        } else {
            null
        }
    }

    fun homeKeyboard(chatId: Long, session: UserSession): SendMessage {
        val locale = localeUtils.getLocale(session.language)
        sessionManager.updateState(chatId, UserState.REGISTERED)
        val askText = messageService.get("ask_question", locale)
        val settingsText = messageService.get("settings", locale)
        val homeText = messageService.get("home", locale)
        val keyboard = ReplyKeyboardMarkup(
            listOf(
                KeyboardRow().apply {
                    add(KeyboardButton(askText))
                    add(KeyboardButton(settingsText))
                }
            )
        ).apply {
            resizeKeyboard = true
        }

        return SendMessage(chatId.toString(), homeText).apply {
            replyMarkup = keyboard
        }
    }

}

@Service
class SettingsHandler(
    private val messageService: MessageService,
    private val sessionManager: SessionManager,
    private val localeUtils: LocaleUtils,
    private val userService: UserService
) {

    fun handleSettings(chatId: Long, session: UserSession): SendMessage {
        sessionManager.updateState(chatId, UserState.SETTINGS)
        val locale = localeUtils.getLocale(session.language)

        val keyboard = ReplyKeyboardMarkup(
            listOf(
                KeyboardRow(listOf(KeyboardButton(messageService.get("change_language", locale)))),
                KeyboardRow(listOf(KeyboardButton(messageService.get("change_username", locale)))),
                KeyboardRow(listOf(KeyboardButton(messageService.get("change_phone", locale)))),
                KeyboardRow(listOf(KeyboardButton(messageService.get("back", locale))))
            )
        ).apply {
            resizeKeyboard = true
            oneTimeKeyboard = true
        }

        return SendMessage(chatId.toString(), messageService.get("settings", locale)).apply {
            replyMarkup = keyboard
        }
    }

    fun handleSettingsSelection(message: Message, chatId: Long, session: UserSession): SendMessage {
        val text = message.text
        val locale = localeUtils.getLocale(session.language)

        return when (text) {
            messageService.get("change_language", locale) -> {
                sessionManager.updateState(chatId, UserState.CHANGE_LANGUAGE)

                val languageKeyboard = ReplyKeyboardMarkup(
                    listOf(
                        KeyboardRow(
                            listOf(
                                KeyboardButton("🇺🇿 Uz"),
                                KeyboardButton("🇷🇺 Ru"),
                                KeyboardButton("🇬🇧 En")
                            )
                        ),
                        KeyboardRow(listOf(KeyboardButton(messageService.get("back", locale))))
                    )
                ).apply {
                    resizeKeyboard = true
                    oneTimeKeyboard = true
                }

                SendMessage(chatId.toString(), messageService.get("change_language", locale)).apply {
                    replyMarkup = languageKeyboard
                }
            }

            messageService.get("change_username", locale) -> {
                sessionManager.updateState(chatId, UserState.CHANGE_USERNAME)
                SendMessage(chatId.toString(), messageService.get("enter_name", locale))
            }

            messageService.get("change_phone", locale) -> {
                sessionManager.updateState(chatId, UserState.CHANGE_PHONE)

                val phoneButton = KeyboardButton(messageService.get("send_phone", locale)).apply {
                    requestContact = true
                }

                val keyboard = ReplyKeyboardMarkup().apply {
                    keyboard = listOf(
                        KeyboardRow(listOf(phoneButton)),
                        KeyboardRow(listOf(KeyboardButton(messageService.get("back", locale))))
                    )
                    resizeKeyboard = true
                    oneTimeKeyboard = true
                }

                SendMessage(chatId.toString(), messageService.get("send_phone", locale)).apply {
                    replyMarkup = keyboard
                }
            }

            messageService.get("back", locale) -> {
                sessionManager.updateState(chatId, UserState.REGISTERED)

                val keyboard = ReplyKeyboardMarkup(
                    listOf(
                        KeyboardRow(listOf(KeyboardButton(messageService.get("ask_question", locale)))),
                        KeyboardRow(listOf(KeyboardButton(messageService.get("settings", locale))))
                    )
                ).apply { resizeKeyboard = true }

                SendMessage(chatId.toString(), messageService.get("home", locale)).apply {
                    replyMarkup = keyboard
                }
            }

            else -> SendMessage(chatId.toString(), messageService.get("invalid_command", locale))
        }
    }

    fun handleLanguageChange(message: Message, chatId: Long, session: UserSession): SendMessage {
        val selectedLanguage = when (message.text) {
            "🇺🇿 Uz" -> Language.UZ
            "🇷🇺 Ru" -> Language.RU
            "🇬🇧 En" -> Language.EN
            else -> null
        }

        return if (selectedLanguage != null) {
            val user = userService.getByChatId(chatId)
            user.selectedLanguage = selectedLanguage
            userService.save(user)

            session.language = selectedLanguage
            sessionManager.updateState(chatId, UserState.REGISTERED)

            val locale = localeUtils.getLocale(selectedLanguage)

            val keyboard = ReplyKeyboardMarkup(
                listOf(
                    KeyboardRow(listOf(KeyboardButton(messageService.get("ask_question", locale)))),
                    KeyboardRow(listOf(KeyboardButton(messageService.get("settings", locale))))
                )
            ).apply { resizeKeyboard = true }

            SendMessage(chatId.toString(), messageService.get("language_changed", locale)).apply {
                replyMarkup = keyboard
            }
        } else {
            SendMessage(chatId.toString(), "❌ Noto‘g‘ri til tanlandi!")
        }
    }

    fun handleUsernameChange(message: Message, chatId: Long, session: UserSession): SendMessage {
        val user = userService.getByChatId(chatId)
        user.userName = message.text
        userService.save(user)

        session.userName = message.text
        sessionManager.updateState(chatId, UserState.REGISTERED)

        val locale = localeUtils.getLocale(session.language)
        val keyboard = ReplyKeyboardMarkup(
            listOf(
                KeyboardRow(listOf(KeyboardButton(messageService.get("ask_question", locale)))),
                KeyboardRow(listOf(KeyboardButton(messageService.get("settings", locale))))
            )
        ).apply { resizeKeyboard = true }

        return SendMessage(chatId.toString(), messageService.get("username_changed", locale)).apply {
            replyMarkup = keyboard
        }
    }

    fun handlePhoneChange(message: Message, chatId: Long, session: UserSession): SendMessage {
        val contact = message.contact
        if (contact == null || contact.userId != message.from.id) {
            return SendMessage(
                chatId.toString(),
                messageService.get("invalid_contact_message", localeUtils.getLocale(session.language))
            )
        }

        val phone = message.contact.phoneNumber
        val user = userService.getByChatId(chatId)
        user.phoneNumber = phone
        userService.save(user)
        session.phoneNumber = phone
        sessionManager.updateState(chatId, UserState.REGISTERED)

        val locale = localeUtils.getLocale(session.language)
        val keyboard = ReplyKeyboardMarkup(
            listOf(
                KeyboardRow(listOf(KeyboardButton(messageService.get("ask_question", locale)))),
                KeyboardRow(listOf(KeyboardButton(messageService.get("settings", locale))))
            )
        ).apply { resizeKeyboard = true }

        return SendMessage(chatId.toString(), messageService.get("phone_changed", locale)).apply {
            replyMarkup = keyboard
        }
    }
}

@Service
class QuestionHandler(
    private val userService: UserService,
    private val questionService: QuestionService,
    private val sessionManager: SessionManager,
    @Lazy
    private val telegramBot: TelegramBot
) {

    @Transactional
    fun onUserSendQuestion(session: UserSession, message: Message): List<PartialBotApiMethod<*>> {
        val user = userService.getByChatId(session.chatId)
        val responses = mutableListOf<PartialBotApiMethod<*>>()
        val freshUser = userService.getById(user.id!!)
        val existingOperatorId = freshUser.inChatWithId
        val existingOperator = existingOperatorId?.let { id -> userService.getById(id) }
        val sessionId = session.lastSessionId ?: UUID.randomUUID()

        val userReplyToMessageId = message.replyToMessage?.messageId

        val operatorReplyToMessageId: Int? = userReplyToMessageId?.let {
            questionService.findOperatorMessageIdByUserMessageId(it)
        }

        if (existingOperator != null &&
            existingOperator.inChatWithId == freshUser.id &&
            existingOperator.inChat
        ) {
            val question = QuestionEntity(
                user = freshUser,
                operator = existingOperator,
                isAnswered = false,
                mediaType = detectMediaType(message),
                fileId = extractFileId(message),
                caption = message.caption,
                question = message.text ?: message.caption,
                latitude = message.location?.latitude,
                longitude = message.location?.longitude,
                venueTitle = message.venue?.title,
                venueAddress = message.venue?.address,
                contactPhoneNumber = message.contact?.phoneNumber,
                contactFirstName = message.contact?.firstName,
                contactLastName = message.contact?.lastName,
                gameTitle = message.game?.title,
                invoiceTitle = message.invoice?.title,
                pollOptions = message.poll?.options
                    ?.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }
                    ?.toMutableList()
                    ?.takeIf { it.size in 2..10 }
                    ?: mutableListOf(),
                sessionId = sessionId,
                fromMessageId = message.messageId,
                toMessageId = null
            )

            questionService.save(question)

            buildOperatorMessageFromUserMessage(existingOperator.chatId!!, message, operatorReplyToMessageId)?.let {
                responses += it
            }

            return responses
        }

        val availableOperator = userService.findAvailableOperator(user.selectedLanguage)

        if (availableOperator != null) {
            val question = QuestionEntity(
                user = user,
                operator = availableOperator,
                isAnswered = false,
                mediaType = detectMediaType(message),
                fileId = extractFileId(message),
                caption = message.caption,
                question = message.text ?: message.caption,
                latitude = message.location?.latitude,
                longitude = message.location?.longitude,
                venueTitle = message.venue?.title,
                venueAddress = message.venue?.address,
                contactPhoneNumber = message.contact?.phoneNumber,
                contactFirstName = message.contact?.firstName,
                contactLastName = message.contact?.lastName,
                gameTitle = message.game?.title,
                invoiceTitle = message.invoice?.title,
                pollOptions = message.poll?.options
                    ?.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }
                    ?.toMutableList()
                    ?.takeIf { it.size in 2..10 }
                    ?: mutableListOf(),
                sessionId = sessionId,
                fromMessageId = message.messageId,
                toMessageId = null
            )

            questionService.save(question)

            val freshOperator = userService.getById(availableOperator.id!!)
            user.inChat = true
            freshOperator.inChat = true
            user.inChatWithId = freshOperator.id
            freshOperator.inChatWithId = user.id
            freshOperator.available = false
            sessionManager.updateState(freshOperator.chatId!!, UserState.IN_CHAT)
            userService.save(user)
            userService.save(freshOperator)

            buildOperatorMessageFromUserMessage(freshOperator.chatId!!, message,operatorReplyToMessageId)?.let {
                responses += it
            }

        } else {
            val question = QuestionEntity(
                user = user,
                operator = null,
                isAnswered = false,
                mediaType = detectMediaType(message),
                fileId = extractFileId(message),
                caption = message.caption,
                question = message.text ?: message.caption,
                latitude = message.location?.latitude,
                longitude = message.location?.longitude,
                venueTitle = message.venue?.title,
                venueAddress = message.venue?.address,
                contactPhoneNumber = message.contact?.phoneNumber,
                contactFirstName = message.contact?.firstName,
                contactLastName = message.contact?.lastName,
                gameTitle = message.game?.title,
                invoiceTitle = message.invoice?.title,
                pollOptions = message.poll?.options
                    ?.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }
                    ?.toMutableList()
                    ?.takeIf { it.size in 2..10 }
                    ?: mutableListOf(),
                sessionId = sessionId,
                fromMessageId = message.messageId,
                toMessageId = null

            )
            questionService.save(question)

            if (!questionService.isUserInQueue(user)) {
                 questionService.addToQueue(user)
            }

            val sendMessage = SendMessage(
                user.chatId.toString(),
                "❗️ Hozircha barcha operatorlar band. Iltimos, kuting."
            )
            telegramBot.execute(sendMessage)
            return listOf()
        }
        return responses
    }

    fun buildOperatorMessageFromUserMessage(
        chatId: Long,
        message: Message,
        replyToMessageId: Int? = null
    ): PartialBotApiMethod<*>? {
        return when {
            message.hasText() -> SendMessage().apply {
                this.chatId = chatId.toString()
                text = message.text
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasPhoto() -> {
                val photo = message.photo?.lastOrNull() ?: return null
                SendPhoto().apply {
                    this.chatId = chatId.toString()
                    this.photo = InputFile(photo.fileId)
                    caption = message.caption ?: "📸 Rasm yuborildi"
                    replyToMessageId?.let { this.replyToMessageId = it }
                }
            }

            message.hasSticker() -> SendSticker().apply {
                this.chatId = chatId.toString()
                sticker = InputFile(message.sticker.fileId)
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasVoice() -> SendVoice().apply {
                this.chatId = chatId.toString()
                voice = InputFile(message.voice.fileId)
                caption = "🎤 Ovozli xabar"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasVideo() -> SendVideo().apply {
                this.chatId = chatId.toString()
                video = InputFile(message.video.fileId)
                caption = "📹 Video yuborildi"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasAnimation() -> SendAnimation().apply {
                this.chatId = chatId.toString()
                animation = InputFile(message.animation.fileId)
                caption = message.caption ?: "🎞️ GIF animatsiya"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasAudio() -> SendAudio().apply {
                this.chatId = chatId.toString()
                audio = InputFile(message.audio.fileId)
                caption = "🎶 Audio"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasDocument() -> SendDocument().apply {
                this.chatId = chatId.toString()
                document = InputFile(message.document.fileId)
                caption = "📄 Hujjat"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasVideoNote() -> SendVideoNote().apply {
                this.chatId = chatId.toString()
                videoNote = InputFile(message.videoNote.fileId)
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasLocation() -> SendLocation().apply {
                this.chatId = chatId.toString()
                latitude = message.location.latitude
                longitude = message.location.longitude
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.venue != null -> SendMessage().apply {
                this.chatId = chatId.toString()
                text = "\uD83D\uDCCD Joy: ${message.venue.title}\nManzil: ${message.venue.address}"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasContact() -> SendMessage().apply {
                this.chatId = chatId.toString()
                text = "\uD83D\uDCDE Kontakt: ${message.contact.firstName}\n${message.contact.phoneNumber}"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasDice() -> SendMessage().apply {
                this.chatId = chatId.toString()
                text = "\uD83C\uDFB2 Tasodifiy son: ${message.dice.value} (${message.dice.emoji})"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasPoll() -> {
                val poll = message.poll
                if (poll != null && poll.options.size in 2..10) {
                    SendPoll(chatId.toString(), poll.question, poll.options.map { it.text })
                } else {
                    SendMessage().apply {
                        this.chatId = chatId.toString()
                        text = "⚠ So‘rovnoma yuborib bo‘lmadi: variantlar soni noto‘g‘ri."
                    }
                }
            }

            else -> SendMessage().apply {
                this.chatId = chatId.toString()
                text = "⚠ Noma'lum xabar turi"
            }
        }
    }

    fun detectMediaType(message: Message): MediaType {
        return when {
            message.hasText() -> MediaType.TEXT
            message.hasPhoto() -> MediaType.PHOTO
            message.hasVideo() -> MediaType.VIDEO
            message.hasVoice() -> MediaType.VOICE
            message.hasAudio() -> MediaType.AUDIO
            message.hasDocument() -> MediaType.DOCUMENT
            message.hasSticker() -> MediaType.STICKER
            message.hasAnimation() -> MediaType.ANIMATION
            message.hasLocation() -> MediaType.LOCATION
            message.hasContact() -> MediaType.CONTACT
            message.hasPoll() -> MediaType.POLL
            message.hasDice() -> MediaType.DICE
            message.hasVideoNote() -> MediaType.VIDEO_NOTE
            message.game != null -> MediaType.GAME
            else -> MediaType.TEXT
        }
    }

    fun extractFileId(message: Message): String? = when {
        message.hasPhoto() -> message.photo.lastOrNull()?.fileId
        message.hasVideo() -> message.video?.fileId
        message.hasVoice() -> message.voice?.fileId
        message.hasAudio() -> message.audio?.fileId
        message.hasDocument() -> message.document?.fileId
        message.hasSticker() -> message.sticker?.fileId
        message.hasAnimation() -> message.animation?.fileId
        message.hasVideoNote() -> message.videoNote?.fileId
        message.hasPoll() -> message.poll?.id
        else -> null
    }

}

@Service
class OperatorHandler(
    private val userService: UserService,
    private val questionService: QuestionService,
    private val operatorStatisticsService: OperatorStatisticsService,
    private val sessionManager: SessionManager,
    private val operatorRatingService: OperatorRatingService,
    private val localeUtils: LocaleUtils,
    private val messageService: MessageService,
    @Lazy
    private val telegramBot: TelegramBot
) {

    @Transactional
    fun onOperatorSendAnswer(operatorChatId: Long, message: Message): List<PartialBotApiMethod<*>> {
        val messages = mutableListOf<PartialBotApiMethod<*>>()
        val operator = userService.getByChatId(operatorChatId)

        if (!operator.inChat || operator.inChatWithId == null) {
            messages += SendMessage(operatorChatId.toString(), "❌ Siz hech kim bilan chatda emassiz.")
            return messages
        }

        val user = userService.getById(operator.inChatWithId!!)
        if (user == null) {
            messages += SendMessage(operatorChatId.toString(), "❌ Chatda hech qanday foydalanuvchi topilmadi.")
            return messages
        }

        val session = sessionManager.getSession(user.chatId!!)
        if (session.lastSessionId == null) {
            val newSessionId = UUID.randomUUID()
            session.lastSessionId = newSessionId
        }
        val sessionId = session.lastSessionId!!

        val answerText = message.text ?: message.caption ?: "[Media]"
        val answer = QuestionEntity(
            user = user,
            operator = operator,
            question = null,
            answer = answerText,
            isAnswered = true,
            sessionId = sessionId,
            pollOptions = message.poll?.options
                ?.mapNotNull { it.text.takeIf { text -> text.isNotBlank() } }
                ?.toMutableList()
                ?.takeIf { it.size in 2..10 }
                ?: mutableListOf()
        )
        questionService.save(answer)

        val operatorReplyToMessageId = message.replyToMessage?.messageId
        val userReplyToMessageId = operatorReplyToMessageId?.let {
            questionService.findOperatorMessageIdByUserMessageId(it)
        }

        buildUserMessageFromOperatorMessage(user.chatId!!, message, userReplyToMessageId)?.let {
            messages += it
        }

        messages += SendMessage(operatorChatId.toString(), "✅ Javob yuborildi.")

        return messages
    }

    fun buildUserMessageFromOperatorMessage(
        toChatId: Long,
        message: Message,
        replyToMessageId: Int? = null
    ): PartialBotApiMethod<*>? {
        return when {
            message.hasText() -> SendMessage().apply {
                chatId = toChatId.toString()
                text = message.text
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasPhoto() -> {
                val photo = message.photo?.lastOrNull() ?: return null
                SendPhoto().apply {
                    chatId = toChatId.toString()
                    this.photo = InputFile(photo.fileId)
                    caption = message.caption ?: "📸 Rasm yuborildi"
                    replyToMessageId?.let { this.replyToMessageId = it }
                }
            }

            message.hasSticker() -> SendSticker().apply {
                chatId = toChatId.toString()
                sticker = InputFile(message.sticker.fileId)
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasVoice() -> SendVoice().apply {
                chatId = toChatId.toString()
                voice = InputFile(message.voice.fileId)
                caption = "🎤 Ovozli xabar"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasVideo() -> SendVideo().apply {
                chatId = toChatId.toString()
                video = InputFile(message.video.fileId)
                caption = "📹 Video yuborildi"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasAnimation() -> SendAnimation().apply {
                chatId = toChatId.toString()
                animation = InputFile(message.animation.fileId)
                caption = message.caption ?: "🎞️ GIF animatsiya"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasAudio() -> SendAudio().apply {
                chatId = toChatId.toString()
                audio = InputFile(message.audio.fileId)
                caption = "🎶 Audio"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasDocument() -> SendDocument().apply {
                chatId = toChatId.toString()
                document = InputFile(message.document.fileId)
                caption = "📄 Hujjat"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasVideoNote() -> SendVideoNote().apply {
                chatId = toChatId.toString()
                videoNote = InputFile(message.videoNote.fileId)
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasLocation() -> SendLocation().apply {
                chatId = toChatId.toString()
                latitude = message.location.latitude
                longitude = message.location.longitude
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.venue != null -> SendMessage().apply {
                chatId = toChatId.toString()
                text = "\uD83D\uDCCD Joy: ${message.venue.title}\nManzil: ${message.venue.address}"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasContact() -> SendMessage().apply {
                chatId = toChatId.toString()
                text = "\uD83D\uDCDE Kontakt: ${message.contact.firstName}\n${message.contact.phoneNumber}"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasDice() -> SendMessage().apply {
                chatId = toChatId.toString()
                text = "\uD83C\uDFB2 Tasodifiy son: ${message.dice.value} (${message.dice.emoji})"
                replyToMessageId?.let { this.replyToMessageId = it }
            }

            message.hasPoll() -> {
                val poll = message.poll
                if (poll != null && poll.options.size in 2..10) {
                    SendPoll().apply {
                        chatId = toChatId.toString()
                        question = poll.question
                        options = poll.options.map { it.text }
                        replyToMessageId?.let { this.replyToMessageId = it }
                    }
                } else {
                    SendMessage().apply {
                        chatId = toChatId.toString()
                        text = "⚠ So‘rovnoma yuborib bo‘lmadi: variantlar soni noto‘g‘ri."
                        replyToMessageId?.let { this.replyToMessageId = it }
                    }
                }
            }

            else -> SendMessage().apply {
                chatId = toChatId.toString()
                text = "⚠ Noma'lum xabar turi"
                replyToMessageId?.let { this.replyToMessageId = it }
            }
        }
    }

    fun onOperatorEndChat(operatorChatId: Long): List<PartialBotApiMethod<*>> {
        val operator = userService.getByChatId(operatorChatId)
        val messages = mutableListOf<PartialBotApiMethod<*>>()

        val activeUser = operator.inChatWithId?.let { userService.getById(it) }
        if (activeUser != null) {
            activeUser.lastOperatorId = operator.id
            userService.save(activeUser)
            val session = sessionManager.getSession(activeUser.chatId!!)
            session.lastSessionId = UUID.randomUUID()

            activeUser.inChat = false
            activeUser.inChatWithId = null
            userService.save(activeUser)
            messages += SendMessage(
                activeUser.chatId.toString(),
                "Operator bilan suhbat yakunlandi. Iltimos, operator xizmatini baholang:"
            )
                .apply { replyMarkup = ratingButtons }
        } else {
            messages += SendMessage(operatorChatId.toString(), "Siz hech kim bilan chatda emassiz")
        }

        operator.inChat = false
        operator.available = true
        operator.inChatWithId = null
        userService.save(operator)
        messages += SendMessage(operatorChatId.toString(), "✅ Suhbat yakunlandi. Siz endi bo‘shsiz.")

        val nextUser = questionService.getNextUser(operator.languages)
        if (nextUser != null) {
            val session = sessionManager.getSession(nextUser.chatId!!)
            val sessionId = session.lastSessionId

            val unansweredQuestions = if (sessionId != null) {
                questionService.findAllUnansweredByUserAndSession(nextUser.id!!, sessionId)
            } else {
                questionService.findAllUnansweredByUser(nextUser)
            }

            if (unansweredQuestions.isNotEmpty()) {
                nextUser.inChat = true
                nextUser.inChatWithId = operator.id
                operator.inChat = true
                operator.available = false
                operator.inChatWithId = nextUser.id
                questionService.removeFromQueue(nextUser)
                userService.save(nextUser)
                userService.save(operator)

                unansweredQuestions.forEach { q ->
                    q.operator = operator
                    questionService.save(q)

                    val msg = buildMessageFromStoredQuestion(operator.chatId!!, q)
                    messages += msg
                }

                messages += SendMessage(
                    nextUser.chatId.toString(),
                    "✅ Sizga operator biriktirildi. Javobni kuting."
                )
            }
        }
        return messages
    }

    val ratingButtons = InlineKeyboardMarkup().apply {
        keyboard = listOf(
            listOf(
                InlineKeyboardButton("⭐️").apply { callbackData = "rating:1" },
                InlineKeyboardButton("⭐️⭐️").apply { callbackData = "rating:2" }
            ),
            listOf(
                InlineKeyboardButton("⭐️⭐️⭐️").apply { callbackData = "rating:3" },
                InlineKeyboardButton("⭐️⭐️⭐️⭐️").apply { callbackData = "rating:4" }
            ),
            listOf(
                InlineKeyboardButton("⭐️⭐️⭐️⭐️⭐️").apply { callbackData = "rating:5" }
            ),
            listOf(
                InlineKeyboardButton("⏭ O‘tkazib yuborish").apply { callbackData = "rating:skip" }
            )
        )
    }

    fun buildMessageFromStoredQuestion(chatId: Long, q: QuestionEntity): PartialBotApiMethod<*> {
        return when (q.mediaType) {
            MediaType.PHOTO -> SendPhoto().apply {
                this.chatId = chatId.toString()
                this.photo = InputFile(q.fileId)
                this.caption = q.caption ?: "📸 Rasm"
            }

            MediaType.VIDEO -> SendVideo().apply {
                this.chatId = chatId.toString()
                this.video = InputFile(q.fileId)
                this.caption = q.caption ?: "📹 Video"
            }

            MediaType.VOICE -> SendVoice().apply {
                this.chatId = chatId.toString()
                this.voice = InputFile(q.fileId)
                this.caption = q.caption ?: "🎤 Ovozli xabar"
            }

            MediaType.AUDIO -> SendAudio().apply {
                this.chatId = chatId.toString()
                this.audio = InputFile(q.fileId)
                this.caption = q.caption ?: "🎶 Audio"
            }

            MediaType.DOCUMENT -> SendDocument().apply {
                this.chatId = chatId.toString()
                this.document = InputFile(q.fileId)
                this.caption = q.caption ?: "📄 Hujjat"
            }

            MediaType.ANIMATION -> SendAnimation().apply {
                this.chatId = chatId.toString()
                this.animation = InputFile(q.fileId)
                this.caption = q.caption ?: "🎞 GIF"
            }

            MediaType.STICKER -> SendSticker().apply {
                this.chatId = chatId.toString()
                this.sticker = InputFile(q.fileId)
            }

            MediaType.LOCATION -> SendLocation().apply {
                this.chatId = chatId.toString()
                this.latitude = q.latitude ?: 0.0
                this.longitude = q.longitude ?: 0.0
            }

            MediaType.CONTACT -> SendContact().apply {
                this.chatId = chatId.toString()
                this.phoneNumber = q.contactPhoneNumber ?: "Nomaʼlum"
                this.firstName = q.contactFirstName ?: "Kontakt"
                this.lastName = q.contactLastName ?: ""
            }

            MediaType.VENUE -> SendVenue().apply {
                this.chatId = chatId.toString()
                this.latitude = q.latitude ?: 0.0
                this.longitude = q.longitude ?: 0.0
                this.title = q.venueTitle ?: "Joy"
                this.address = q.venueAddress ?: "Manzil"
            }

            MediaType.DICE -> SendDice().apply {
                this.chatId = chatId.toString()
                this.emoji = q.diceEmoji ?: "🎲" // default emoji
            }

            MediaType.VIDEO_NOTE -> SendVideoNote().apply {
                this.chatId = chatId.toString()
                this.videoNote = InputFile(q.fileId)
            }

            MediaType.GAME -> SendMessage().apply {
                this.chatId = chatId.toString()
                this.text = "🎮 O‘yin: ${q.gameTitle ?: "Game"}"
            }

            MediaType.POLL -> {
                if (q.pollOptions != null && q.pollOptions!!.size in 2..10) {
                    SendPoll().apply {
                        this.chatId = chatId.toString()
                        question = q.question ?: "📊 So‘rovnoma"
                        options = q.pollOptions!!
                    }
                } else {
                    SendMessage(
                        chatId.toString(),
                        "⚠ So‘rovnoma yuborib bo‘lmadi: kamida 2 va ko‘pi bilan 10 ta variant bo‘lishi kerak."
                    )
                }
            }

            MediaType.TEXT -> SendMessage().apply {
                this.chatId = chatId.toString()
                this.text = q.question ?: "📩 Matnli xabar"
            }

            else -> SendMessage(chatId.toString(), q.question ?: "⚠ Noma'lum savol")
        }
    }

    fun onStatisticsRequested(operatorChatId: Long): SendMessage {
        val operator = userService.getByChatId(operatorChatId)
        val stats = operatorStatisticsService.getStatistics(operator)

        val msg = """
                🧑‍💼 Operator holati: ${stats.onlineStatus}
                🧑‍💼 Bugun xizmat ko‘rsatdi: ${stats.servedUsers} ta foydalanuvchiga
                📬 Qabul qilingan savollar: ${stats.totalQuestions} ta
                ✅ Javob berilgan: ${stats.answered} ta
                ❌ Javobsiz: ${stats.unanswered} ta
                🕓 O‘rtacha javob vaqti: ${format(stats.avgResponseSeconds)}
                ⏱️ O‘rtacha suhbat davomiyligi: ${format(stats.avgChatDurationSeconds)}
                """.trimIndent()
        return SendMessage(operatorChatId.toString(), msg)
    }

    private fun format(seconds: Long): String {
        val minutes = seconds / 60
        val remaining = seconds % 60
        return "$minutes daqiqa $remaining soniya"
    }

    fun online(chatId: Long): OnlineResult {
        val messages = mutableListOf<PartialBotApiMethod<*>>()
        val operator = userService.getByChatId(chatId)
        operator.available = true
        operator.inChat = false
        operator.inChatWithId = null
        userService.save(operator)

        sessionManager.updateState(chatId, UserState.IN_CHAT)
        sessionManager.getSession(chatId).state = UserState.IN_CHAT

        val message = SendMessage(chatId.toString(), "✅ Siz endi online holatdasiz.")
            .apply { replyMarkup = operatorOnlineKeyboard() }
        telegramBot.execute(message)

        val nextUser = questionService.getNextUser(operator.languages)
        if (nextUser != null) {
            val session = sessionManager.getSession(nextUser.chatId!!)
            val sessionId = session.lastSessionId

            val unansweredQuestions = if (sessionId != null) {
                questionService.findAllUnansweredByUserAndSession(nextUser.id!!, sessionId)
            } else {
                questionService.findAllUnansweredByUser(nextUser)
            }

            if (unansweredQuestions.isNotEmpty()) {
                nextUser.inChat = true
                nextUser.inChatWithId = operator.id
                operator.inChat = true
                operator.available = false
                operator.inChatWithId = nextUser.id
                questionService.removeFromQueue(nextUser)
                userService.save(nextUser)
                userService.save(operator)

                unansweredQuestions.forEach { q ->
                    q.operator = operator
                    questionService.save(q)

                    val msg = buildMessageFromStoredQuestion(operator.chatId!!, q)
                    messages += msg
                }

                val sendMessage = SendMessage(
                    nextUser.chatId.toString(),
                    "✅ Sizga operator biriktirildi. Javobni kuting."
                )
                telegramBot.execute(sendMessage)
            }
        }
        return OnlineResult(messages, nextUser)
    }

    fun offline(chatId: Long): List<PartialBotApiMethod<*>> {
        val messages = mutableListOf<PartialBotApiMethod<*>>()
        val operator = userService.getByChatId(chatId)
        val session = sessionManager.getSession(chatId)
        if (operator.inChat) {
            val endChatMessages = onOperatorEndChat(chatId)
            messages += endChatMessages
        }

        operator.available = false
        operator.inChat = false
        operator.inChatWithId = null
        userService.save(operator)

        sessionManager.updateState(chatId, UserState.OPERATOR_HOME)
        session.state = UserState.OPERATOR_HOME
        val message = SendMessage(chatId.toString(), "❌ Siz offline holatdasiz.")
            .apply { replyMarkup = operatorHomeKeyboard() }
        messages += message
        return messages
    }

    fun operatorHomeKeyboard(): ReplyKeyboardMarkup {
        val row = KeyboardRow().apply {
            add(KeyboardButton(BotConstants.STATISTIC))
            add(KeyboardButton(BotConstants.ONLINE))
            add(KeyboardButton("Settings"))
        }
        return ReplyKeyboardMarkup(listOf(row)).apply {
            resizeKeyboard = true
        }
    }

    fun operatorOnlineKeyboard(): ReplyKeyboardMarkup {
        val row = KeyboardRow().apply {
            add(KeyboardButton(BotConstants.END_CHAT))
            add(KeyboardButton(BotConstants.OFFLINE))
        }
        return ReplyKeyboardMarkup(listOf(row)).apply { resizeKeyboard = true }
    }

    fun handleRating(chatId: Long, data: String, messageId: Int): List<SendMessage> {
        val messages = mutableListOf<SendMessage>()
        val user = userService.getByChatId(chatId)
        val locale = localeUtils.getLocale(user.selectedLanguage)
        val askText = messageService.get("ask_question", locale)
        val settingsText = messageService.get("settings", locale)
        val keyboard = ReplyKeyboardMarkup(
            listOf(
                KeyboardRow(listOf(KeyboardButton(askText))),
                KeyboardRow(listOf(KeyboardButton(settingsText)))
            )
        ).apply { resizeKeyboard = true }

        if (data == "rating:skip") {
        } else if (data.startsWith("rating:")) {
            val stars = data.removePrefix("rating:").toIntOrNull()
            if (stars != null && stars in 1..5) {
                val operatorId = user.lastOperatorId
                val session = sessionManager.getSession(chatId)
                val sessionId = session.lastSessionId
                if (operatorId != null && sessionId != null) {
                    val operator = userService.getById(operatorId)
                    if (!operatorRatingService.hasRated(user, operator, sessionId)) {
                        operatorRatingService.saveRating(user, operator, stars, sessionId)

                        val notifyText = "⭐️ Sizga ${stars} yulduzli baho berildi!"
                        messages += SendMessage(operator.chatId.toString(), notifyText)
                    }
                }
            }
        }

        user.lastOperatorId = null
        user.inChat = false
        user.inChatWithId = null
        userService.save(user)
        sessionManager.updateState(chatId, UserState.REGISTERED)

        val text = if (data == "rating:skip") {
            "✅ Baholash o‘tkazib yuborildi."
        } else {
            "✅ Bahoyingiz uchun rahmat!"
        }
        messages += SendMessage(chatId.toString(), text)
        messages += SendMessage(chatId.toString(), "🏠 Bosh sahifaga qaytdingiz").apply {
            replyMarkup = keyboard
        }

        return messages
    }

}

