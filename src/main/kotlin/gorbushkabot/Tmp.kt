package gorbushkabot

import gorbushkabot.db.BlackListRepository
import gorbushkabot.spreadsheets.GoogleSheetService
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class Tmp(
    private val blackListRepository: BlackListRepository,
    private val googleSheetService: GoogleSheetService
) {

    @Scheduled(fixedDelay = 15_000)
    fun tmp() {
        googleSheetService.syncBlackList(
            blackListRepository.findAll()
                .sortedBy { it.created!! }
                .map { it.userId to it.created!! }
        )
    }

}