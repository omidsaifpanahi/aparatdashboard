// path: src/main/com/example/aparatdashboard/CookieInputActivity.kt

package com.example.aparatdashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CookieInputActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cookie_input)

        val cookieInput = findViewById<EditText>(R.id.etCookie)
        val saveButton = findViewById<Button>(R.id.btnSaveCookie)

        saveButton.setOnClickListener {
            val cookie = cookieInput.text.toString().trim()

            if (cookie.isBlank()) {
                Toast.makeText(this, "کوکی را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CookieStore.saveCookie(this, cookie)

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
