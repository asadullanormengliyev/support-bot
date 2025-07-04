package uz.zero_one.supportbot

enum class UserRole {
    ADMIN, USER, OPERATOR
}

enum class Language {
    UZ, RU, EN
}

enum class UserState {
    CHOOSE_LANGUAGE,
    ENTER_USERNAME,
    ENTER_PHONE,
    REGISTERED,
    ASKING_QUESTION,
    IN_CHAT,
    SETTINGS,
    CHANGE_LANGUAGE,
    CHANGE_USERNAME,
    CHANGE_PHONE,
    OPERATOR_HOME
}

enum class MediaType {
    TEXT, PHOTO, VIDEO, VOICE, STICKER, ANIMATION, AUDIO, DOCUMENT,
    LOCATION,CONTACT, POLL, DICE, VENUE, VIDEO_NOTE, GAME
}

