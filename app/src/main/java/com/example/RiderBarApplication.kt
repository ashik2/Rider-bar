package com.example

import android.app.Application
import com.example.database.RiderBarDatabase
import com.example.database.RiderBarRepository

class RiderBarApplication : Application() {
    val database by lazy { RiderBarDatabase.getDatabase(this) }
    val repository by lazy { RiderBarRepository(database) }
}
