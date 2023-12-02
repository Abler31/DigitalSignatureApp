package com.abler31.digitalsignature

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File


class MainActivity : AppCompatActivity() {

    private lateinit var selectedFileUri: Uri
    private lateinit var fileNameTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val selectFileButton: MaterialButton = findViewById(R.id.btn_select_file)
        fileNameTextView = findViewById(R.id.tv_file_name)
        selectFileButton.setOnClickListener {
            pickDocument.launch(arrayOf("application/*"))
        }

    }

    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            // Выбранный файл доступен по uri
            // Ваш код для дальнейшей обработки выбранного файла
            val selectedFile = File(uri.path ?: "")
            fileNameTextView.text = getUriFileName(uri)
            // Ваш код для работы с выбранным файлом
        }
    }

    fun getUriFileName(uri: Uri): String? {
        var fileName: String? = null
        val contentResolver = getContentResolver()

        // ContentResolver query
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)

        try {
            cursor?.let {
                if (it.moveToFirst()) {
                    // Get the column index of the file name
                    val fileNameIndex: Int = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (fileNameIndex != -1) {
                        fileName = it.getString(fileNameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Error getting file name from uri: $e")
        } finally {
            cursor?.close()
        }
        return fileName
    }
}