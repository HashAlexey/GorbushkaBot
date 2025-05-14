package gorbushkabot.db

import org.springframework.data.jpa.repository.JpaRepository

interface UserApplicationRepository : JpaRepository<UserApplicationEntity, Long> {

    fun findAllByStatus(status: UserApplicationEntity.Status): List<UserApplicationEntity>

    fun findAllByUserId(userId: Long): List<UserApplicationEntity>

}