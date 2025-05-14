package gorbushkabot

import gorbushkabot.db.UserApplicationEntity
import gorbushkabot.db.UserApplicationRepository
import gorbushkabot.spreadsheets.GoogleSheetService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.commands.DeleteMyCommands
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

@Component
class UserUpdateHandler(
    private val userApplicationRepository: UserApplicationRepository,
    private val googleSheetService: GoogleSheetService
) {

    private val chatStates = ConcurrentHashMap<Long, State>()
    private val chatDataMap = ConcurrentHashMap<Long, Map<String, String?>>()

    fun handleMessage(telegramClient: TelegramClient, chatId: Long, message: Message) {
        if (!checkAbleToFill(telegramClient, chatId, message.from.id)) {
            return
        }

        val chatState = chatStates[chatId]

        when {
            message.text == "/start" -> {
                telegramClient.execute(DeleteMyCommands.builder().build())

                telegramClient.sendMessage(
                    chatId = chatId,
                    text = "Добро пожаловать в систему!",
                    replyMarkup = InlineKeyboardMarkup.builder()
                        .keyboardRow(
                            InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                    .callbackData("fill")
                                    .text("Перейти к верификации")
                                    .build()
                            )
                        )
                        .build()
                )
            }
            chatState == State.FIO -> {
                when {
                    message.text == null -> {
                        telegramClient.sendMessage(chatId, "Ошибка: Некорректный формат сообщения")
                    }
                    message.text.matches(Regex("^[А-Яа-яA-Za-z ]+$")) -> {
                        putToData(chatId, mapOf("fio" to message.text))
                        handlePhone(telegramClient, chatId)
                    }
                    else -> {
                        telegramClient.sendMessage(chatId, "Ошибка: ФИО должно содержать только буквы и пробелы")
                    }
                }
            }
            chatState == State.PHONE -> {
                when {
                    message.text == null -> {
                        telegramClient.sendMessage(chatId, "Ошибка: Некорректный формат сообщения")
                    }
                    message.text.replace(Regex("\\D+"), "").length == 11 -> {
                        putToData(chatId, mapOf("phone" to message.text.replace(Regex("\\D+"), "")))
                        handleRole(telegramClient, chatId)
                    }
                    else -> {
                        telegramClient.sendMessage(chatId, "Ошибка: Введите корректный номер телефона (формат: +71234567891)")
                    }
                }
            }
            chatState == State.OFFICE_NUMBER -> {
                when {
                    message.text == null -> {
                        telegramClient.sendMessage(chatId, "Ошибка: Некорректный формат сообщения")
                    }
                    else -> {
                        putToData(chatId, mapOf("office_number" to message.text))
                        handleVerification(telegramClient, chatId)
                    }
                }
            }
            else -> {
                telegramClient.sendMessage(chatId, "Ошибка: Отправка сообщений недопустима")
            }
        }
    }

    fun handleCallbackQuery(telegramClient: TelegramClient, chatId: Long, callbackQuery: CallbackQuery) {
        if (!checkAbleToFill(telegramClient, chatId, callbackQuery.from.id)) {
            return
        }

        when (callbackQuery.data) {
            "fill" -> {
                handleFio(telegramClient, chatId)
            }
            "role_seller" -> {
                putToData(chatId, mapOf("role" to "Оптовик", "office_number" to null))
                handleOfficeNumber(telegramClient, chatId)
            }
            "role_buyer" -> {
                putToData(chatId, mapOf("role" to "Покупатель", "office_number" to null))
                handleVerification(telegramClient, chatId)
            }
            "back_from_phone" -> {
                handleFio(telegramClient, chatId)
            }
            "back_from_role" -> {
                handlePhone(telegramClient, chatId)
            }
            "back_from_office_number" -> {
                handleRole(telegramClient, chatId)
            }
            "submit" -> {
                handleSubmit(telegramClient, chatId, callbackQuery.from)
            }
        }
    }

    private fun checkAbleToFill(telegramClient: TelegramClient, chatId: Long, userId: Long): Boolean {
        val applications = userApplicationRepository.findAllByUserId(userId)

        val hasApproved = applications.any { it.status == UserApplicationEntity.Status.APPROVED }

        if (hasApproved) {
            telegramClient.sendMessage(chatId, "Ваша заявка уже одобрена, вы зарегистрированы в системе")
            return false
        }

        val rejectPassed = applications
            .filter { it.status == UserApplicationEntity.Status.REJECTED }
            .minOfOrNull { Duration.between(it.decisionTimestamp!!, Instant.now()).toMinutes() }

        if (rejectPassed != null && rejectPassed < 5) {
            telegramClient.sendMessage(chatId, "Подождите ${5 - rejectPassed} мин. перед повторной подачей заявки. ⏳")
            return false
        }

        return true
    }

    private fun handleFio(telegramClient: TelegramClient, chatId: Long) {
        telegramClient.sendMessage(chatId, "Введите ваше ФИО:")

        chatStates[chatId] = State.FIO
    }

    private fun handlePhone(telegramClient: TelegramClient, chatId: Long) {
        telegramClient.sendMessage(
            chatId = chatId,
            text = "Введите номер вашего телефона:",
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("back_from_phone")
                            .text("⬅️ Назад")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.PHONE
    }

    private fun handleRole(telegramClient: TelegramClient, chatId: Long) {
        telegramClient.sendMessage(
            chatId = chatId,
            text = "Выберите свою роль:",
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("role_seller")
                            .text("Оптовик")
                            .build(),
                        InlineKeyboardButton.builder()
                            .callbackData("role_buyer")
                            .text("Покупатель")
                            .build()
                    )
                )
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("back_from_role")
                            .text("⬅️ Назад")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.ROLE
    }

    private fun handleOfficeNumber(telegramClient: TelegramClient, chatId: Long) {
        telegramClient.sendMessage(
            chatId = chatId,
            text = "Введите номер вашего офиса:",
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("back_from_office_number")
                            .text("⬅️ Назад")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.OFFICE_NUMBER
    }

    @Suppress("DuplicatedCode")
    private fun handleVerification(telegramClient: TelegramClient, chatId: Long) {
        val fillButton = InlineKeyboardButton.builder()
            .callbackData("fill")
            .text("Заполнить заново")
            .build()

        try {
            val chatData = chatDataMap[chatId] ?: emptyMap()

            val fio = chatData["fio"] ?: error("Не удалось найти данные о ФИО")
            val phoneNumber = chatData["phone"] ?: error("Не удалось найти данные о номере телефона")
            val role = chatData["role"] ?: error("Не удалось найти данные о роли")
            val officeNumber = chatData["office_number"]

            val textParts = listOfNotNull(
                "✅ <b>Заявка заполнена!</b>",
                "",
                "👤 <b>ФИО:</b> $fio",
                "📞 <b>Телефон (контактный):</b> ${formatPhone(phoneNumber)}",
                "💼 <b>Роль:</b> $role",
                officeNumber?.let { "🏢 <b>Номер офиса:</b> $it" }
            )

            telegramClient.sendMessage(
                chatId = chatId,
                text = textParts.joinToString("\n"),
                parseMode = "HTML",
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(fillButton))
                    .keyboardRow(
                        InlineKeyboardRow(
                            InlineKeyboardButton.builder()
                                .callbackData("submit")
                                .text("Отправить")
                                .build()
                        )
                    )
                    .build()
            )

            chatStates[chatId] = State.VERIFICATION
        } catch (exception: Exception) {
            log.error(exception) { "Verification error" }

            telegramClient.sendMessage(
                chatId = chatId,
                text = "⚠️ Ошибка при верификации. Заполните анкету снова",
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(fillButton))
                    .build()
            )
        }
    }

    @Suppress("DuplicatedCode")
    private fun handleSubmit(telegramClient: TelegramClient, chatId: Long, from: User) {
        val replyMessageId = telegramClient.sendMessage(chatId, "⏳ Сохраняем данные...")

        try {
            val chatData = chatDataMap[chatId] ?: emptyMap()

            val fio = chatData["fio"] ?: error("Не удалось найти данные о ФИО")
            val phoneNumber = chatData["phone"] ?: error("Не удалось найти данные о номере телефона")
            val role = chatData["role"] ?: error("Не удалось найти данные о роли")
            val officeNumber = chatData["office_number"]

            val application = userApplicationRepository.saveAndFlush(
                UserApplicationEntity(
                    userId = from.id,
                    username = from.userName,
                    fio = fio,
                    phoneNumber = phoneNumber,
                    role = role,
                    officeNumber = officeNumber,
                    status = UserApplicationEntity.Status.NEW,
                    decisionTimestamp = null,
                    decisionUserId = null
                )
            )

            // TODO: Переносить отдельным процессом
            googleSheetService.addApplication(application)

            telegramClient.editMessage(
                chatId = chatId,
                text = "✅ Заявка успешно отправлена! Ожидайте подтверждения",
                editMessageId = replyMessageId
            )

            chatStates[chatId] = State.SUBMITTED
        } catch (exception: Exception) {
            log.error(exception) { "Submit error" }

            telegramClient.editMessage(
                chatId = chatId,
                text = "⚠️ Ошибка при отправке. Попробуйте позже",
                editMessageId = replyMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(
                        InlineKeyboardRow(
                            InlineKeyboardButton.builder()
                                .callbackData("submit")
                                .text("Повторить отправку")
                                .build()
                        )
                    )
                    .build()
            )
        }
    }

    @Synchronized
    private fun putToData(chatId: Long, patch: Map<String, String?>) {
        chatDataMap[chatId] = (chatDataMap[chatId] ?: emptyMap()) + patch
    }

    private enum class State {
        FIO,
        PHONE,
        ROLE,
        OFFICE_NUMBER,
        VERIFICATION,
        SUBMITTED
    }

}