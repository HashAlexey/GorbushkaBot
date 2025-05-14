package gorbushkabot

import gorbushkabot.db.AdminListRepository
import gorbushkabot.db.BlackListRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramClient

private val log = KotlinLogging.logger {}

@Component
class GorbushkaBot(
    @Value("\${custom.bot.token}") private val botToken: String,
    private val adminListRepository: AdminListRepository,
    private val blackListRepository: BlackListRepository,
    private val adminUpdateHandler: AdminUpdateHandler,
    private val userUpdateHandler: UserUpdateHandler
) : SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private val telegramClient: TelegramClient = OkHttpTelegramClient(botToken)

    override fun getBotToken(): String {
        return botToken
    }

    override fun getUpdatesConsumer(): LongPollingUpdateConsumer {
        return this
    }

    override fun consume(update: Update) {
        log.debug { "Handle $update" }

        val (chatId, from) = when {
            update.hasMessage() -> update.message.chatId to update.message.from
            update.hasCallbackQuery() -> update.callbackQuery.message.chatId to update.callbackQuery.from
            else -> return
        }

        if (checkInBlackList(from.id)) {
            log.debug { "User $from is blacklisted" }
            telegramClient.sendMessage(chatId, "Вы находитесь в черном списке")
            return
        }

        if (checkIsAdmin(from.id)) {
            if (update.hasMessage()) {
                adminUpdateHandler.handleMessage(telegramClient, chatId, update.message)
            } else {
                adminUpdateHandler.handleCallbackQuery(telegramClient, chatId, update.callbackQuery)
            }
        } else {
            if (update.hasMessage()) {
                userUpdateHandler.handleMessage(telegramClient, chatId, update.message)
            } else {
                userUpdateHandler.handleCallbackQuery(telegramClient, chatId, update.callbackQuery)
            }
        }
    }

    private fun checkInBlackList(userId: Long): Boolean {
        return blackListRepository.existsByUserId(userId)
    }

    private fun checkIsAdmin(userId: Long): Boolean {
        return adminListRepository.existsByUserId(userId)
    }

}