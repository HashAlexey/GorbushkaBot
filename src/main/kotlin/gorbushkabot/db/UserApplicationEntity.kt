package gorbushkabot.db

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "user_application")
@EntityListeners(AuditingEntityListener::class)
class UserApplicationEntity(
    var userId: Long,
    var username: String?,
    var fio: String,
    var phoneNumber: String,
    var role: String,
    var officeNumber: String?,
    @Enumerated(EnumType.STRING)
    var status: Status,
    var decisionTimestamp: Instant?,
    var decisionUserId: Long?
) {

    enum class Status {
        NEW,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreatedDate
    var created: Instant? = null

}