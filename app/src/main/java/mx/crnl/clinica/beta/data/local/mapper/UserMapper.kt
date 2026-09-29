package mx.crnl.clinica.beta.data.local.mapper

import mx.crnl.clinica.beta.data.local.entity.DemoUserEntity
import mx.crnl.clinica.beta.domain.model.AccountStatus
import mx.crnl.clinica.beta.domain.model.ClinicalArea
import mx.crnl.clinica.beta.domain.model.UserAccount
import mx.crnl.clinica.beta.domain.model.UserRole

fun DemoUserEntity.toDomain(): UserAccount = UserAccount(
    userId = userId,
    firstName = firstName,
    paternalSurname = paternalSurname,
    maternalSurname = maternalSurname,
    email = email,
    role = UserRole.valueOf(roleCode),
    area = areaCode?.let(ClinicalArea::valueOf),
    professionalLicense = professionalLicense,
    status = AccountStatus.valueOf(status),
)
