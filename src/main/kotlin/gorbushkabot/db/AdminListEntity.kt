package gorbushkabot.db

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "admin_list")
@EntityListeners(AuditingEntityListener::class)
class AdminListEntity(var userId: Long) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreatedDate
    var created: Instant? = null

}