package gorbushkabot.db

import org.springframework.data.jpa.repository.JpaRepository

interface PinnedMessageRepository : JpaRepository<PinnedMessageEntity, Long> {

    fun findAllByChatId(chatId: Long): List<PinnedMessageEntity>

}