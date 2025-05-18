package gorbushkabot

import gorbushkabot.db.*
import gorbushkabot.spreadsheets.GoogleSheetService
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember
import org.telegram.telegrambots.meta.api.methods.groupadministration.CreateChatInviteLink
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember
import org.telegram.telegrambots.meta.api.methods.groupadministration.UnbanChatMember
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

@Suppress("DuplicatedCode")
@Component
class AdminUpdateHandler(
    private val userApplicationRepository: UserApplicationRepository,
    private val pinnedMessageRepository: PinnedMessageRepository,
    private val adminListRepository: AdminListRepository,
    private val blackListRepository: BlackListRepository,
    private val applicationFormatter: ApplicationFormatter,
    private val googleSheetService: GoogleSheetService,
    @Value("\${custom.chats.main-chat-id}") private val mainChatId: Long,
    @Value("\${custom.chats.price-chat-id}") private val priceChatId: Long,
    @Value("\${custom.chats.communication-chat-id}") private val communicationChatId: Long
) {

    companion object {
        private const val MENU_APPLICATIONS_TEXT = "\uD83D\uDCCB Заявки"
        private const val MENU_UPDATE_CATEGORIES_TEXT = "\uD83D\uDD04 Обновить категории"
        private const val MENU_ADMIN_LIST_TEXT = "\uD83D\uDD10 Администраторы"
        private const val MENU_BLACK_LIST_TEXT = "\uD83D\uDEAB Чёрный список"
    }

    private val chatStates = ConcurrentHashMap<Long, State>()
    private val chatEditMessageIds = ConcurrentHashMap<Long, Int>()
    private val chatMessagesToDeleteMap = ConcurrentHashMap<Long, List<Int>>()

    // Handle

    fun handleMessage(telegramClient: TelegramClient, chatId: Long, message: Message) {
        val chatState = chatStates[chatId]
        val chatEditMessageId = chatEditMessageIds[chatId]

        val deleteMessages: Boolean

        when {
            message.text == "/start" -> {
                showMenu(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = chatEditMessageId
                )

                deleteMessages = true
            }
            message.text == "/menu" -> {
                showMenu(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = chatEditMessageId
                )

                deleteMessages = true
            }
            message.text == "/applications" -> {
                showApplicationList(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = chatEditMessageId,
                    page = 1,
                    filter = null
                )

                deleteMessages = true
            }
            message.text == "/update_categories" -> {
                updateCategories(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = chatEditMessageId
                )

                deleteMessages = true
            }
            message.text == "/admin_list" -> {
                showAdminList(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = chatEditMessageId
                )

                deleteMessages = true
            }
            message.text == "/black_list" -> {
                showBlackList(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = chatEditMessageId,
                    page = 1,
                    filter = null
                )

                deleteMessages = true
            }
            chatState == State.APPLICATION_LIST_FILTER -> {
                if (message.text != null) {
                    showApplicationList(
                        telegramClient = telegramClient,
                        chatId = chatId,
                        editMessageId = chatEditMessageId,
                        page = 1,
                        filter = message.text
                    )

                    deleteMessages = true
                } else {
                    val messageId = telegramClient.sendMessage(chatId, "Пожалуйста, пришлите текст")
                    addMessageToDelete(chatId, messageId)
                    deleteMessages = false
                }
            }
            chatState == State.ADMIN_ADD -> {
                if (message.contact != null || message.forwardFrom != null) {
                    addAdminProcess(
                        telegramClient = telegramClient,
                        chatId = chatId,
                        editMessageId = chatEditMessageId,
                        userId = message.contact?.userId ?: message.forwardFrom.id
                    )

                    deleteMessages = true
                } else {
                    val messageId = telegramClient.sendMessage(chatId, "Пожалуйста, пришлите контакт или перешлите сообщение")
                    addMessageToDelete(chatId, messageId)
                    deleteMessages = false
                }
            }
            chatState == State.BLACK_LIST_FILTER -> {
                if (message.text != null) {
                    showBlackList(
                        telegramClient = telegramClient,
                        chatId = chatId,
                        editMessageId = chatEditMessageId,
                        page = 1,
                        filter = message.text
                    )

                    deleteMessages = true
                } else {
                    val messageId = telegramClient.sendMessage(chatId, "Пожалуйста, пришлите текст")
                    addMessageToDelete(chatId, messageId)
                    deleteMessages = false
                }
            }
            chatState == State.BLACK_LIST_ADD -> {
                if (message.contact != null || message.forwardFrom != null) {
                    addToBlackListProcess(
                        telegramClient = telegramClient,
                        chatId = chatId,
                        editMessageId = chatEditMessageId,
                        userId = message.contact?.userId ?: message.forwardFrom.id
                    )

                    deleteMessages = true
                } else {
                    val messageId = telegramClient.sendMessage(chatId, "Пожалуйста, пришлите контакт")
                    addMessageToDelete(chatId, messageId)
                    deleteMessages = false
                }
            }
            else -> {
                val messageId = telegramClient.sendMessage(chatId, "Некорректные данные")
                addMessageToDelete(chatId, messageId)
                deleteMessages = false
            }
        }

        if (deleteMessages) {
            val chatMessagesToDelete = chatMessagesToDeleteMap[chatId] ?: emptyList()
            deleteMessages(telegramClient, chatId, listOf(message.messageId).plus(chatMessagesToDelete))
            chatMessagesToDeleteMap.remove(chatId)
        } else {
            addMessageToDelete(chatId, message.messageId)
        }
    }

    fun handleCallbackQuery(telegramClient: TelegramClient, chatId: Long, callbackQuery: CallbackQuery) {
        val callbackData = callbackQuery.data
        val from = callbackQuery.from
        val sourceMessageId = callbackQuery.message.messageId

        when {
            callbackData == "menu" -> {
                showMenu(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId
                )
            }
            callbackData == "applications_filter" -> {
                handleApplicationListFilter(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId
                )
            }
            callbackData.startsWith("applications_") -> {
                val splitResult = callbackQuery.data.split('_', limit = 3)
                val page = splitResult[1].toInt()
                val filter = splitResult.getOrNull(2)

                showApplicationList(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId,
                    page = page,
                    filter = filter
                )
            }
            callbackData.startsWith("application_") -> {
                val applicationId = callbackQuery.data.substringAfter("application_").toLong()

                showApplication(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId,
                    applicationId = applicationId
                )
            }
            callbackData.startsWith("approve_application_") -> {
                val applicationId = callbackQuery.data.substringAfter("approve_application_").toLong()

                approveApplication(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    from = from,
                    editMessageId = sourceMessageId,
                    applicationId = applicationId
                )
            }
            callbackData.startsWith("reject_application_") -> {
                val applicationId = callbackQuery.data.substringAfter("reject_application_").toLong()

                rejectApplication(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    from = from,
                    editMessageId = sourceMessageId,
                    applicationId = applicationId
                )
            }
            callbackData == "update_categories" -> {
                updateCategories(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId
                )
            }
            callbackData == "admin_list" -> {
                showAdminList(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId
                )
            }
            callbackData.startsWith("admin_") -> {
                val adminId = callbackQuery.data.substringAfter("admin_").toLong()

                showAdminCard(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    from = from,
                    editMessageId = sourceMessageId,
                    adminId = adminId
                )
            }
            callbackData == "add_admin" -> {
                addAdmin(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId
                )
            }
            callbackData.startsWith("demote_admin_") -> {
                val adminId = callbackQuery.data.substringAfter("demote_admin_").toLong()

                demoteAdmin(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId,
                    adminId = adminId
                )
            }
            callbackData.startsWith("black_list_card_") -> {
                val blackListId = callbackQuery.data.substringAfter("black_list_card_").toLong()

                showBlackListCard(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId,
                    blackListId = blackListId
                )
            }
            callbackData == "black_list_filter" -> {
                handleBlackListFilter(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId
                )
            }
            callbackData.startsWith("black_list_") -> {
                val splitResult = callbackQuery.data.split('_', limit = 4)
                val page = splitResult[2].toInt()
                val filter = splitResult.getOrNull(3)

                showBlackList(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId,
                    page = page,
                    filter = filter
                )
            }
            callbackData == "add_to_black_list" -> {
                addToBlackList(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId
                )
            }
            callbackData.startsWith("remove_from_black_list_") -> {
                val blackListId = callbackQuery.data.substringAfter("remove_from_black_list_").toLong()

                removeFromBlackList(
                    telegramClient = telegramClient,
                    chatId = chatId,
                    editMessageId = sourceMessageId,
                    blackListId = blackListId
                )
            }
        }

        val chatMessagesToDelete = chatMessagesToDeleteMap[chatId] ?: emptyList()

        if (chatMessagesToDelete.isNotEmpty()) {
            deleteMessages(telegramClient, chatId, chatMessagesToDelete)
            chatMessagesToDeleteMap.remove(chatId)
        }
    }

    // Actions

    private fun showMenu(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?) {
        telegramClient.execute(
            SetMyCommands.builder()
                .command(
                    BotCommand.builder()
                        .command("/applications")
                        .description(MENU_APPLICATIONS_TEXT)
                        .build()
                )
                .command(
                    BotCommand.builder()
                        .command("/update_categories")
                        .description(MENU_UPDATE_CATEGORIES_TEXT)
                        .build()
                )
                .command(
                    BotCommand.builder()
                        .command("/admin_list")
                        .description(MENU_ADMIN_LIST_TEXT)
                        .build()
                )
                .command(
                    BotCommand.builder()
                        .command("/black_list")
                        .description(MENU_BLACK_LIST_TEXT)
                        .build()
                )
                .build()
        )

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Меню администратора",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("applications_1")
                            .text(MENU_APPLICATIONS_TEXT)
                            .build()
                    )
                )
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("update_categories")
                            .text(MENU_UPDATE_CATEGORIES_TEXT)
                            .build()
                    )
                )
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("admin_list")
                            .text(MENU_ADMIN_LIST_TEXT)
                            .build()
                    )
                )
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("black_list_1")
                            .text(MENU_BLACK_LIST_TEXT)
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.MENU
    }

    private fun showApplicationList(
        telegramClient: TelegramClient,
        chatId: Long,
        editMessageId: Int?,
        page: Int,
        filter: String?
    ) {
        val applications = userApplicationRepository.findAllByStatus(UserApplicationEntity.Status.NEW)

        val backToMenuButton = InlineKeyboardButton.builder()
            .callbackData("menu")
            .text("↩\uFE0F Вернуться в меню администратора")
            .build()

        if (applications.isEmpty()) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Заявки не найдены",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToMenuButton))
                    .build()
            )

            return
        }

        val pageSize = 10

        val filteredApplications = applications
            .map { it.id to "👤 ${it.fio} | 📞 ${formatPhone(it.phoneNumber)} | 🛠 ${it.role}" }
            .filter { filter.isNullOrBlank() || it.second.lowercase().contains(filter.lowercase()) }

        val filteredAndPagedApplications = filteredApplications
            .sortedBy { it.first }
            .drop((page - 1) * pageSize)
            .take(pageSize)
            .toList()

        val totalPages = ceil(filteredApplications.size.toDouble() / pageSize).toInt()
        val showPrevious = page > 1
        val showNext = page < totalPages

        val textParts = listOfNotNull(
            "Список заявок",
            "Страница: $page из $totalPages",
            filter?.takeIf { it.isNotBlank() }?.let { "Фильтр: $it" }
        )

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = textParts.joinToString("\n"),
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboard(
                    filteredAndPagedApplications.map {
                        InlineKeyboardRow(
                            InlineKeyboardButton.builder()
                                .callbackData("application_${it.first}")
                                .text(it.second)
                                .build()
                        )
                    }
                )
                .let { replyMarkupBuilder ->
                    val buttons = mutableListOf<InlineKeyboardButton>()

                    if (showPrevious) {
                        buttons.add(
                            InlineKeyboardButton.builder()
                                .callbackData("applications_${page - 1}_${filter ?: ""}")
                                .text("⬅\uFE0F Назад")
                                .build()
                        )
                    }

                    if (showNext) {
                        buttons.add(
                            InlineKeyboardButton.builder()
                                .callbackData("applications_${page + 1}_${filter ?: ""}")
                                .text("➡\uFE0F Вперед")
                                .build()
                        )
                    }

                    if (buttons.size == 0) {
                        return@let replyMarkupBuilder
                    }

                    return@let replyMarkupBuilder.keyboardRow(InlineKeyboardRow(buttons))
                }
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("applications_filter")
                            .text("\uD83D\uDD0E Поиск")
                            .build()
                    )
                )
                .keyboardRow(InlineKeyboardRow(backToMenuButton))
                .build()
        )

        chatStates[chatId] = State.APPLICATION_LIST
    }

    private fun handleApplicationListFilter(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?) {
        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Введите фильтр для поиска",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("applications_1")
                            .text("↩\uFE0F Вернуться в список заявок")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.APPLICATION_LIST_FILTER
    }

    private fun showApplication(
        telegramClient: TelegramClient,
        chatId: Long,
        editMessageId: Int?,
        applicationId: Long
    ) {
        val application = userApplicationRepository.findByIdOrNull(applicationId)

        val backToApplicationsButton = InlineKeyboardButton.builder()
            .callbackData("applications_1")
            .text("↩\uFE0F Вернуться в список заявок")
            .build()

        if (application == null) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Заявка не найдена",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToApplicationsButton))
                    .build()
            )

            chatStates[chatId] = State.APPLICATION_CARD_ERROR

            return
        }

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = applicationFormatter.format(application),
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("approve_application_${application.id}")
                            .text("✅ Одобрить")
                            .build(),
                        InlineKeyboardButton.builder()
                            .callbackData("reject_application_${application.id}")
                            .text("❌ Отклонить")
                            .build()
                    )
                )
                .keyboardRow(InlineKeyboardRow(backToApplicationsButton))
                .build()
        )

        chatStates[chatId] = State.APPLICATION_CARD
    }

    private fun approveApplication(
        telegramClient: TelegramClient,
        chatId: Long,
        from: User,
        editMessageId: Int?,
        applicationId: Long
    ) {
        val application = userApplicationRepository.findByIdOrNull(applicationId)

        val backToApplicationsButton = InlineKeyboardButton.builder()
            .callbackData("applications_1")
            .text("↩\uFE0F Вернуться в список заявок")
            .build()

        if (application == null) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Заявка не найдена",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToApplicationsButton))
                    .build()
            )

            chatStates[chatId] = State.APPLICATION_APPROVE_ERROR

            return
        }

        if (application.status != UserApplicationEntity.Status.NEW) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Заявка уже обработана",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToApplicationsButton))
                    .build()
            )

            chatStates[chatId] = State.APPLICATION_APPROVE_ERROR

            return
        }

        application.status = UserApplicationEntity.Status.APPROVED
        application.decisionUserId = from.id
        application.decisionTimestamp = Instant.now()

        userApplicationRepository.saveAndFlush(application)

        // TODO: Переносить отдельным процессом
        googleSheetService.addApprovedApplication(application)

        telegramClient.sendMessage(
            chatId = application.userId,
            text = """
                ✅ Ваша заявка одобрена!
                Вот ваши уникальные ссылки для вступления в группы (одноразовые):
                
                Союз основной:
                ${createInviteLink(telegramClient, mainChatId)}
                
                Союз цены:
                ${createInviteLink(telegramClient, priceChatId)}
                
                Союз общение:
                ${createInviteLink(telegramClient, communicationChatId)}
            """.trimIndent()
        )

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Заявка была успешно одобрена",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(InlineKeyboardRow(backToApplicationsButton))
                .build()
        )

        chatStates[chatId] = State.APPLICATION_APPROVED
    }

    private fun rejectApplication(
        telegramClient: TelegramClient,
        chatId: Long,
        from: User,
        editMessageId: Int?,
        applicationId: Long
    ) {
        val application = userApplicationRepository.findByIdOrNull(applicationId)

        val backToApplicationsButton = InlineKeyboardButton.builder()
            .callbackData("applications_1")
            .text("↩\uFE0F Вернуться в список заявок")
            .build()

        if (application == null) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Заявка не найдена",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToApplicationsButton))
                    .build()
            )

            chatStates[chatId] = State.APPLICATION_REJECT_ERROR

            return
        }

        if (application.status != UserApplicationEntity.Status.NEW) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Заявка уже обработана",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToApplicationsButton))
                    .build()
            )

            chatStates[chatId] = State.APPLICATION_REJECT_ERROR

            return
        }

        application.status = UserApplicationEntity.Status.REJECTED
        application.decisionUserId = from.id
        application.decisionTimestamp = Instant.now()

        userApplicationRepository.saveAndFlush(application)

        telegramClient.sendMessage(
            chatId = application.userId,
            text = "К сожалению, ваша заявка была отклонена. ❌\nВы можете подать заявку заново, нажав кнопку ниже через 5 минут",
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("fill")
                            .text("Заполнить заново \uD83D\uDD04")
                            .build()
                    )
                )
                .build()
        )

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Заявка была успешно отклонена",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(InlineKeyboardRow(backToApplicationsButton))
                .build()
        )

        chatStates[chatId] = State.APPLICATION_REJECTED
    }

    private fun updateCategories(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?) {
        val categories = googleSheetService.getCategories()

        val backToMenuButton = InlineKeyboardButton.builder()
            .callbackData("menu")
            .text("↩\uFE0F Вернуться в меню администратора")
            .build()

        if (categories.isEmpty()) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Категории не найдены",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToMenuButton))
                    .build()
            )

            chatStates[chatId] = State.UPDATE_CATEGORIES_ERROR

            return
        }

        pinnedMessageRepository.findAllByChatId(mainChatId).forEach { pinnedMessage ->
            deleteMessages(telegramClient, mainChatId, listOf(pinnedMessage.messageId))
            pinnedMessageRepository.delete(pinnedMessage)
        }

        val newMessageId = telegramClient.sendMessage(
            chatId = mainChatId,
            text = "Выберите категорию (лист) из таблицы:",
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboard(
                    categories
                        .chunked(2)
                        .map { chunk ->
                            InlineKeyboardRow(
                                chunk.map {
                                    InlineKeyboardButton.builder()
                                        .text(it.name)
                                        .url(it.href)
                                        .build()
                                }
                            )
                        }
                )
                .build()
        )

        telegramClient.execute(
            PinChatMessage.builder()
                .chatId(mainChatId)
                .messageId(newMessageId)
                .disableNotification(true)
                .build()
        )

        pinnedMessageRepository.saveAndFlush(
            PinnedMessageEntity(
                chatId = mainChatId,
                messageId = newMessageId
            )
        )

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Категории обновлены!",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(InlineKeyboardRow(backToMenuButton))
                .build()
        )

        chatStates[chatId] = State.UPDATE_CATEGORIES_SUCCESS
    }

    private fun showAdminList(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?) {
        val adminList = adminListRepository.findAll()

        val addButton = InlineKeyboardButton.builder()
            .callbackData("add_admin")
            .text("➕ Добавить")
            .build()

        val backToMenuButton = InlineKeyboardButton.builder()
            .callbackData("menu")
            .text("↩\uFE0F Вернуться в меню администратора")
            .build()

        if (adminList.isEmpty()) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Администраторы не найдены",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(addButton))
                    .keyboardRow(InlineKeyboardRow(backToMenuButton))
                    .build()
            )

            chatStates[chatId] = State.ADMIN_LIST

            return
        }

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Список администраторов:\n",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboard(
                    adminList.map { admin ->
                        val getResult = telegramClient.execute(
                            GetChatMember.builder()
                                .chatId(admin.userId)
                                .userId(admin.userId)
                                .build()
                        )

                        val textParts = listOfNotNull(
                            "\uD83D\uDC64",
                            getResult.user.userName?.let { "@$it" },
                            getResult.user.lastName,
                            getResult.user.firstName
                        )

                        return@map InlineKeyboardRow(
                            InlineKeyboardButton.builder()
                                .callbackData("admin_${admin.id}")
                                .text(textParts.joinToString(" "))
                                .build()
                        )
                    }
                )
                .keyboardRow(InlineKeyboardRow(addButton))
                .keyboardRow(InlineKeyboardRow(backToMenuButton))
                .build()
        )

        chatStates[chatId] = State.ADMIN_LIST
    }

    private fun showAdminCard(
        telegramClient: TelegramClient,
        chatId: Long,
        from: User,
        editMessageId: Int?,
        adminId: Long
    ) {
        val admin = adminListRepository.findByIdOrNull(adminId)

        val backToAdminListButton = InlineKeyboardButton.builder()
            .callbackData("admin_list")
            .text("↩\uFE0F Вернуться в список администраторов")
            .build()

        if (admin == null) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Администратор не найден",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToAdminListButton))
                    .build()
            )

            chatStates[chatId] = State.ADMIN_CARD_ERROR

            return
        }

        val getResult = telegramClient.execute(
            GetChatMember.builder()
                .chatId(admin.userId)
                .userId(admin.userId)
                .build()
        )

        val ts = admin.created!!
            .atZone(ZoneId.of("Europe/Moscow"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = """
                ФИО: ${listOfNotNull(getResult.user.lastName, getResult.user.firstName).joinToString(" ")}
                Telegram: ${getResult.user.userName?.let { "@$it" } ?: "Не указан"}
                Дата выдачи прав: $ts
            """.trimIndent(),
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .let { replyMarkupBuilder ->
                    if (from.id == admin.userId) {
                        return@let replyMarkupBuilder
                    }

                    return@let replyMarkupBuilder.keyboardRow(
                        InlineKeyboardRow(
                            InlineKeyboardButton.builder()
                                .callbackData("demote_admin_${adminId}")
                                .text("❌ Удалить из администраторов")
                                .build()
                        )
                    )
                }
                .keyboardRow(InlineKeyboardRow(backToAdminListButton))
                .build()
        )

        chatStates[chatId] = State.ADMIN_CARD
    }

    private fun addAdmin(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?) {
        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Пожалуйста, пришлите пользователя как контакт или перешлите сообщения от него",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("admin_list")
                            .text("❌ Отмена")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.ADMIN_ADD
    }

    private fun addAdminProcess(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?, userId: Long) {
        if (!adminListRepository.existsByUserId(userId)) {
            adminListRepository.saveAndFlush(AdminListEntity(userId))
        }

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Администратор успешно добавлен",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("admin_list")
                            .text("↩\uFE0F Вернуться в список администраторов")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.ADMIN_ADD_SUCCESS
    }

    private fun demoteAdmin(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?, adminId: Long) {
        adminListRepository.deleteById(adminId)

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Администратор успешно удален",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("admin_list")
                            .text("↩\uFE0F Вернуться в список администраторов")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.ADMIN_DEMOTE_SUCCESS
    }

    private fun showBlackList(
        telegramClient: TelegramClient,
        chatId: Long,
        editMessageId: Int?,
        page: Int,
        filter: String?
    ) {
        val blackList = blackListRepository.findAll()

        val addButton = InlineKeyboardButton.builder()
            .callbackData("add_to_black_list")
            .text("➕ Добавить")
            .build()

        val backToMenuButton = InlineKeyboardButton.builder()
            .callbackData("menu")
            .text("↩\uFE0F Вернуться в меню администратора")
            .build()

        if (blackList.isEmpty()) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Чёрный список пуст",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(addButton))
                    .keyboardRow(InlineKeyboardRow(backToMenuButton))
                    .build()
            )

            chatStates[chatId] = State.BLACK_LIST

            return
        }

        val pageSize = 10

        val filteredBlackList = blackList
            .map { blackListElement ->
                val getResult = telegramClient.execute(
                    GetChatMember.builder()
                        .chatId(blackListElement.userId)
                        .userId(blackListElement.userId)
                        .build()
                )

                val textParts = listOfNotNull(
                    "\uD83D\uDC64",
                    getResult.user.userName?.let { "@$it" },
                    getResult.user.lastName,
                    getResult.user.firstName
                )

                return@map blackListElement.id to textParts.joinToString(" ")
            }

            .filter { filter.isNullOrBlank() || it.second.lowercase().contains(filter.lowercase()) }

        val filteredAndPagedBlackList = filteredBlackList
            .sortedBy { it.first }
            .drop((page - 1) * pageSize)
            .take(pageSize)
            .toList()

        val totalPages = ceil(filteredBlackList.size.toDouble() / pageSize).toInt()
        val showPrevious = page > 1
        val showNext = page < totalPages

        val textParts = listOfNotNull(
            "Список заявок",
            "Страница: $page из $totalPages",
            filter?.takeIf { it.isNotBlank() }?.let { "Чёрный список: $it" }
        )

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = textParts.joinToString("\n"),
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboard(
                    filteredAndPagedBlackList.map {
                        InlineKeyboardRow(
                            InlineKeyboardButton.builder()
                                .callbackData("black_list_card_${it.first}")
                                .text(it.second)
                                .build()
                        )
                    }
                )
                .let { replyMarkupBuilder ->
                    val buttons = mutableListOf<InlineKeyboardButton>()

                    if (showPrevious) {
                        buttons.add(
                            InlineKeyboardButton.builder()
                                .callbackData("black_list_${page - 1}_${filter ?: ""}")
                                .text("⬅\uFE0F Назад")
                                .build()
                        )
                    }

                    if (showNext) {
                        buttons.add(
                            InlineKeyboardButton.builder()
                                .callbackData("black_list_${page + 1}_${filter ?: ""}")
                                .text("➡\uFE0F Вперед")
                                .build()
                        )
                    }

                    if (buttons.size == 0) {
                        return@let replyMarkupBuilder
                    }

                    return@let replyMarkupBuilder.keyboardRow(InlineKeyboardRow(buttons))
                }
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("black_list_filter")
                            .text("\uD83D\uDD0E Поиск")
                            .build()
                    )
                )
                .keyboardRow(InlineKeyboardRow(addButton))
                .keyboardRow(InlineKeyboardRow(backToMenuButton))
                .build()
        )

        chatStates[chatId] = State.BLACK_LIST
    }

    private fun handleBlackListFilter(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?) {
        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Введите фильтр для поиска",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("black_list_1")
                            .text("↩\uFE0F Вернуться в чёрный список")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.BLACK_LIST_FILTER
    }

    private fun showBlackListCard(
        telegramClient: TelegramClient,
        chatId: Long,
        editMessageId: Int?,
        blackListId: Long
    ) {
        val blackListElement = blackListRepository.findByIdOrNull(blackListId)

        val backToBlackListButton = InlineKeyboardButton.builder()
            .callbackData("black_list_1")
            .text("↩\uFE0F Вернуться в чёрный список")
            .build()

        if (blackListElement == null) {
            telegramClient.sendReplyInternal(
                chatId = chatId,
                text = "Запись в чёрном списке не найдена",
                editMessageId = editMessageId,
                replyMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(InlineKeyboardRow(backToBlackListButton))
                    .build()
            )

            chatStates[chatId] = State.BLACK_LIST_CARD_ERROR

            return
        }

        val getResult = telegramClient.execute(
            GetChatMember.builder()
                .chatId(blackListElement.userId)
                .userId(blackListElement.userId)
                .build()
        )

        val ts = blackListElement.created!!
            .atZone(ZoneId.of("Europe/Moscow"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = """
                ФИО: ${listOfNotNull(getResult.user.lastName, getResult.user.firstName).joinToString(" ")}
                Telegram: ${getResult.user.userName?.let { "@$it" } ?: "Не указан"}
                Дата блокировки: $ts
            """.trimIndent(),
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("remove_from_black_list_${blackListId}")
                            .text("❌ Разблокировать")
                            .build()
                    )
                )
                .keyboardRow(InlineKeyboardRow(backToBlackListButton))
                .build()
        )

        chatStates[chatId] = State.BLACK_LIST_CARD
    }

    private fun addToBlackList(telegramClient: TelegramClient, chatId: Long, editMessageId: Int?) {
        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Пожалуйста, пришлите пользователя как контакт или перешлите сообщения от него",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("black_list_1")
                            .text("❌ Отмена")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.BLACK_LIST_ADD
    }

    private fun addToBlackListProcess(
        telegramClient: TelegramClient,
        chatId: Long,
        editMessageId: Int?,
        userId: Long
    ) {
        if (!blackListRepository.existsByUserId(userId)) {
            banChatMember(telegramClient = telegramClient, chatId = mainChatId, userId = userId)
            banChatMember(telegramClient = telegramClient, chatId = communicationChatId, userId = userId)
            banChatMember(telegramClient = telegramClient, chatId = priceChatId, userId = userId)

            blackListRepository.saveAndFlush(BlackListEntity(userId))
        }

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Пользователь успешно добавлен в чёрный список",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("black_list_1")
                            .text("↩\uFE0F Вернуться в чёрный список")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.BLACK_LIST_ADD_SUCCESS
    }

    private fun removeFromBlackList(
        telegramClient: TelegramClient,
        chatId: Long,
        editMessageId: Int?,
        blackListId: Long
    ) {
        val blackListEntity = blackListRepository.findByIdOrNull(blackListId)

        if (blackListEntity != null) {
            val userId = blackListEntity.userId

            unbanChatMember(telegramClient = telegramClient, chatId = mainChatId, userId = userId)
            unbanChatMember(telegramClient = telegramClient, chatId = communicationChatId, userId = userId)
            unbanChatMember(telegramClient = telegramClient, chatId = priceChatId, userId = userId)

            blackListRepository.delete(blackListEntity)
        }

        telegramClient.sendReplyInternal(
            chatId = chatId,
            text = "Пользователь успешно убран из чёрного списка",
            editMessageId = editMessageId,
            replyMarkup = InlineKeyboardMarkup.builder()
                .keyboardRow(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                            .callbackData("black_list_1")
                            .text("↩\uFE0F Вернуться в чёрный список")
                            .build()
                    )
                )
                .build()
        )

        chatStates[chatId] = State.BLACK_LIST_REMOVE_SUCCESS
    }

    // Util

    private fun TelegramClient.sendReplyInternal(
        chatId: Long,
        text: String,
        editMessageId: Int? = null,
        replyMarkup: InlineKeyboardMarkup? = null
    ) {
        if (editMessageId == null) {
            val newMessageId = this.sendMessage(chatId = chatId, text = text, replyMarkup = replyMarkup)
            chatEditMessageIds[chatId] = newMessageId
            return
        }

        this.editMessage(chatId = chatId, text = text, editMessageId = editMessageId, replyMarkup = replyMarkup)
        chatEditMessageIds[chatId] = editMessageId
        return
    }

    private fun createInviteLink(telegramClient: TelegramClient, chatId: Long): String {
        val result = telegramClient.execute(
            CreateChatInviteLink.builder()
                .chatId(chatId)
                .memberLimit(1)
                .build()
        )

        return result.inviteLink
    }

    private fun deleteMessages(telegramClient: TelegramClient, chatId: Long, messageIds: Collection<Int>) {
        if (messageIds.isEmpty()) {
            return
        }

        telegramClient.execute(
            DeleteMessages.builder()
                .chatId(chatId)
                .messageIds(messageIds)
                .build()
        )
    }

    private fun banChatMember(telegramClient: TelegramClient, chatId: Long, userId: Long) {
        telegramClient.execute(
            BanChatMember.builder()
                .chatId(chatId)
                .userId(userId)
                .build()
        )
    }

    private fun unbanChatMember(telegramClient: TelegramClient, chatId: Long, userId: Long) {
        telegramClient.execute(
            UnbanChatMember.builder()
                .chatId(chatId)
                .userId(userId)
                .build()
        )
    }

    private fun addMessageToDelete(chatId: Long, messageId: Int) {
        chatMessagesToDeleteMap[chatId] = (chatMessagesToDeleteMap[chatId] ?: emptyList()).plus(messageId)
    }

    private enum class State {
        MENU,
        APPLICATION_LIST,
        APPLICATION_LIST_FILTER,
        APPLICATION_CARD,
        APPLICATION_CARD_ERROR,
        APPLICATION_APPROVED,
        APPLICATION_APPROVE_ERROR,
        APPLICATION_REJECTED,
        APPLICATION_REJECT_ERROR,
        UPDATE_CATEGORIES_SUCCESS,
        UPDATE_CATEGORIES_ERROR,
        ADMIN_LIST,
        ADMIN_CARD,
        ADMIN_CARD_ERROR,
        ADMIN_ADD,
        ADMIN_ADD_SUCCESS,
        ADMIN_DEMOTE_SUCCESS,
        BLACK_LIST,
        BLACK_LIST_FILTER,
        BLACK_LIST_CARD,
        BLACK_LIST_CARD_ERROR,
        BLACK_LIST_ADD,
        BLACK_LIST_ADD_SUCCESS,
        BLACK_LIST_REMOVE_SUCCESS
    }

}