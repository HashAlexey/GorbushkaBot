package gorbushkabot

import gorbushkabot.db.BlackListRepository
import gorbushkabot.spreadsheets.GoogleSheetService
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.generics.TelegramClient

@Component
@EnableScheduling
class Tmp(
    private val blackListRepository: BlackListRepository,
    private val googleSheetService: GoogleSheetService,
    @Value("\${custom.bot.token}") private val botToken: String
) {

    private val telegramClient: TelegramClient = OkHttpTelegramClient(botToken)

    @Scheduled(fixedDelay = 5_000)
    fun tmp() {
        googleSheetService.syncBlackList(
            blackListRepository.findAll().map {
                val getResult = telegramClient.execute(
                    GetChatMember.builder()
                        .chatId(it.userId)
                        .userId(it.userId)
                        .build()
                )

                return@map GoogleSheetService.BlackListEntry(
                    id = it.id!!,
                    timestamp = it.created!!,
                    userId = it.userId,
                    username = getResult.user.userName ?: "Не задано"
                )
            }
        )
    }

}