package gorbushkabot.spreadsheets

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ServiceAccountCredentials
import gorbushkabot.db.UserApplicationEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class GoogleSheetService(
    @Value("\${custom.google.service-account-json-resource}") private val serviceAccountJsonResource: Resource,
    @Value("\${custom.google.categories.spreadsheet-id}") private val categoriesSpreadsheetId: String,
    @Value("\${custom.google.categories.sheet-name}") private val categoriesSheetName: String,
    @Value("\${custom.google.applications.spreadsheet-id}") private val applicationsSpreadsheetId: String,
    @Value("\${custom.google.applications.sheet-name}") private val applicationsSheetName: String,
    @Value("\${custom.google.approved-applications.spreadsheet-id}") private val approvedApplicationsSpreadsheetId: String,
    @Value("\${custom.google.approved-applications.sheet-name}") private val approvedApplicationsSheetName: String,
    @Value("\${custom.google.black-list.spreadsheet-id}") private val blackListSpreadsheetId: String,
    @Value("\${custom.google.black-list.sheet-name}") private val blackListSheetName: String
) {

    fun getCategories(): List<Category> {
        return getSheets()
            .spreadsheets()
            .values()
            .get(categoriesSpreadsheetId, categoriesSheetName)
            .execute()
            .getValues()
            .drop(1)
            .filterNot { it[0]?.toString().isNullOrBlank() }
            .filterNot { it[1]?.toString().isNullOrBlank() }
            .map { Category(name = it[0].toString(), href = it[1].toString()) }
    }

    fun addApplication(application: UserApplicationEntity) {
        addApplicationInternal(
            application = application,
            spreadsheetId = applicationsSpreadsheetId,
            sheetName = applicationsSheetName
        )
    }

    fun addApprovedApplication(application: UserApplicationEntity) {
        addApplicationInternal(
            application = application,
            spreadsheetId = approvedApplicationsSpreadsheetId,
            sheetName = approvedApplicationsSheetName
        )
    }

    private fun addApplicationInternal(application: UserApplicationEntity, spreadsheetId: String, sheetName: String) {
        val nowFormatted = Instant.now()
            .atZone(ZoneId.of("Europe/Moscow"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val valuesRange = ValueRange()
            .setValues(
                listOf(
                    listOf(
                        nowFormatted,
                        application.fio,
                        application.phoneNumber,
                        application.role,
                        application.officeNumber ?: "",
                        application.userId,
                        application.username ?: "Не указано"
                    )
                )
            )

        getSheets()
            .spreadsheets()
            .values()
            .append(spreadsheetId, "$sheetName!A2", valuesRange)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    private fun getSheets(): Sheets {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val credentials = serviceAccountJsonResource.inputStream
            .use { ServiceAccountCredentials.fromStream(it) }
            .createScoped(SheetsScopes.SPREADSHEETS)
        val httpRequestInitializer = HttpCredentialsAdapter(credentials)

        return Sheets.Builder(transport, jsonFactory, httpRequestInitializer)
            .setApplicationName("GorbushkaBot")
            .build()
    }

    @Synchronized
    fun syncBlackList(blackList: List<BlackListEntry>) {
        val current = getSheets()
            .spreadsheets()
            .values()
            .get(blackListSpreadsheetId, "$blackListSheetName!A2")
            .execute()
            .getValues()
            ?: emptyList()

        val new = blackList.map {
            val timestampFormatted = it.timestamp
                .atZone(ZoneId.of("Europe/Moscow"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            return@map listOf(
                timestampFormatted,
                it.userId.toString(),
                it.username
            )
        }

        getSheets()
            .spreadsheets()
            .values()
            .update(blackListSpreadsheetId, "$blackListSheetName!A2", ValueRange().setValues(new))
            .setValueInputOption("USER_ENTERED")
            .execute()

        if (current.size > new.size) {
            getSheets()
                .spreadsheets()
                .values()
                .clear(
                    blackListSpreadsheetId,
                    "$blackListSheetName!A${1 + (current.size - new.size)}:Z",
                    ClearValuesRequest()
                )
                .execute()
        }
    }

    class BlackListEntry(
        val id: Long,
        val timestamp: Instant,
        val userId: Long,
        val username: String
    )

}