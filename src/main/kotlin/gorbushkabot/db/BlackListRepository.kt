package gorbushkabot.db

import org.springframework.data.jpa.repository.JpaRepository

interface BlackListRepository : JpaRepository<BlackListEntity, Long> {

    fun existsByUserId(userId: Long): Boolean

}