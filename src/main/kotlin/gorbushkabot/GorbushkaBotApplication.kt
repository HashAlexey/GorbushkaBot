package gorbushkabot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@EnableJpaAuditing
@SpringBootApplication
class GorbushkaBotApplication

fun main(args: Array<String>) {
    runApplication<GorbushkaBotApplication>(*args)
}
