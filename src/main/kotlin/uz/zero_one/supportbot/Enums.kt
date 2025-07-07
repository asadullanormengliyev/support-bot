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

enum class ErrorCode(val code: Int){
    USER_NAME_EXISTS(100),
    AGE_MAX(101),
    UNIVERSITY_NOT_FOUND(102),
    USER_NOT_FOUND(103),
    ROLE_NOT_FOUND(104)
}


