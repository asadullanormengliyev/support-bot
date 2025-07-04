package uz.zero_one.supportbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass = BaseRepositoryImpl::class)
class SupportBotApplication

fun main(args: Array<String>) {
    runApplication<SupportBotApplication>(*args)
}
