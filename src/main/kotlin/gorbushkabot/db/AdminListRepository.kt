package gorbushkabot.db

import org.springframework.data.jpa.repository.JpaRepository

interface AdminListRepository : JpaRepository<AdminListEntity, Long> {

    fun existsByUserId(userId: Long): Boolean

}