package gorbushkabot

import gorbushkabot.db.UserApplicationEntity
import org.springframework.stereotype.Component

@Component
class ApplicationFormatter {

    fun format(application: UserApplicationEntity): String {
        val textParts = listOfNotNull(
            "\uD83D\uDC64 ФИО: ${application.fio}",
            "\uD83D\uDCDB Telegram: ${application.username?.let { "@$it" } ?: "Не указано"}",
            "\uD83D\uDCDE Телефон (контактный): ${formatPhone(application.phoneNumber)}",
            "\uD83D\uDEE0 Роль: ${application.role}",
            application.officeNumber?.let { "\uD83C\uDFE2 Номер офиса: $it" }
        )

        return textParts.joinToString("\n")
    }

}