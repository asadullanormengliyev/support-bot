package uz.zero_one.supportbot

import jakarta.validation.Valid
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ExceptionHandlers(
    private val errorMessageSource: ResourceBundleMessageSource
){
    @ExceptionHandler(DemoExceptions::class)
    fun handleException(exceptions: DemoExceptions): ResponseEntity<*>{
        return  when (exceptions) {
            is AgeMaxException -> ResponseEntity.badRequest().body(exceptions.getErrorMessage(errorMessageSource,exceptions.age))
            is UniversityNotFoundException -> ResponseEntity.badRequest().body(exceptions.getErrorMessage(errorMessageSource,exceptions.id))
            is UserNameExistsException ->  ResponseEntity.badRequest().body(exceptions.getErrorMessage(errorMessageSource,exceptions.username))
            is UserNotFoundException -> ResponseEntity.badRequest().body(exceptions.getErrorMessage(errorMessageSource,exceptions.id))
            is RoleNotFoundException -> ResponseEntity.badRequest().body(exceptions.getErrorMessage(errorMessageSource,exceptions.messages))
        }
    }
}

@RestController
@RequestMapping("/api/v1/admin")
class AdminOperatorController(
    private val operatorService: OperatorServiceImpl,
    private val adminService: AdminServiceImpl
) {

    @PostMapping("/admin")
    fun createAdmin(@RequestBody request: CreateAdminRequest): AdminResponse {
        return adminService.createAdmin(request)
    }

    @PostMapping("/login")
    fun login(@RequestParam username: String, @RequestParam password: String): JwtResponseDto {
        return adminService.login(username, password)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/operator")
    fun createOperator(@RequestBody @Valid request: CreateOperatorRequest): OperatorResponse {
        return operatorService.create(request)
    }

}
