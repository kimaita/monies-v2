package com.kimaita.monies.data.dao

import androidx.room.Dao
import com.kimaita.monies.data.database.models.UserAccount

@Dao
interface AccountDao : BaseDao<UserAccount> {}