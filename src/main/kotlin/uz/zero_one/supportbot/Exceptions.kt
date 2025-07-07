package uz.zero_one.supportbot

import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale

sealed class DemoExceptions(message: String? = null): RuntimeException(message){
    abstract fun errorType(): ErrorCode

    fun getErrorMessage(errorMessageSource: ResourceBundleMessageSource,vararg array: Any?): BaseMessage {
        return BaseMessage(
            errorType().code,errorMessageSource.getMessage(
                errorType().toString(),array, Locale(
                    LocaleContextHolder.getLocale().language
                )
            )
        )
    }

}

class UserNameExistsException(val username: String): DemoExceptions(){
    override fun errorType(): ErrorCode {
        return ErrorCode.USER_NAME_EXISTS
    }
}

class AgeMaxException(val age: Int): DemoExceptions(){
    override fun errorType(): ErrorCode {
        return ErrorCode.AGE_MAX
    }
}

class UniversityNotFoundException(val id: Long): DemoExceptions(){
    override fun errorType(): ErrorCode {
        return ErrorCode.UNIVERSITY_NOT_FOUND
    }
}

class UserNotFoundException(val id: Long): DemoExceptions(){
    override fun errorType(): ErrorCode {
        return ErrorCode.USER_NOT_FOUND
    }
}

class RoleNotFoundException(val messages: String): DemoExceptions(){
    override fun errorType(): ErrorCode {
        return ErrorCode.ROLE_NOT_FOUND
    }
}

