package gorbushkabot

import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.generics.TelegramClient

fun TelegramClient.editMessage(
    chatId: Long,
    text: String,
    editMessageId: Int,
    parseMode: String? = null,
    replyMarkup: InlineKeyboardMarkup? = null
) {
    // TODO: Обработать ситуацию, когда сообщение удалено
    val editMessageText = EditMessageText.builder()
        .chatId(chatId)
        .messageId(editMessageId)
        .text(text)
        .let { if (parseMode != null) it.parseMode(parseMode) else it }
        .let { if (replyMarkup != null) it.replyMarkup(replyMarkup) else it }
        .build()

    this.execute(editMessageText)
}

fun TelegramClient.sendMessage(
    chatId: Long,
    text: String,
    parseMode: String? = null,
    replyMarkup: ReplyKeyboard? = null
) : Int {
    val sendMessage = SendMessage.builder()
        .chatId(chatId)
        .text(text)
        .let { if (parseMode != null) it.parseMode(parseMode) else it }
        .let { if (replyMarkup != null) it.replyMarkup(replyMarkup) else it }
        .build()

    val sendResult = this.execute(sendMessage)

    return sendResult.messageId
}