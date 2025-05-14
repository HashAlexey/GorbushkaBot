package gorbushkabot.db

import jakarta.persistence.*

@Entity
@Table(name = "pinned_message")
class PinnedMessageEntity(
    var chatId: Long,
    var messageId: Int
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

}