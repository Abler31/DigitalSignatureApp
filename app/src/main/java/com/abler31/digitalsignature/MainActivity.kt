package com.abler31.digitalsignature

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.w3c.dom.Text
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


class MainActivity : AppCompatActivity() {

    companion object {
        const val PERMISSION_REQUEST_CODE = 123
    }

    private lateinit var selectedFileUri: Uri
    private lateinit var fileNameTextView: TextView
    private lateinit var privateKeyPassword: String

    val downloadDirectory =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val directoryPath = downloadDirectory.absolutePath

    //расширения для ключей
    val targetExtensionsPrivate = listOf("key", "pem", "der", "p12")
    val targetExtensionsPublic = listOf("csr", "cer")

    //найденные пути к ключам
    private var pathsPrivate: List<String> = emptyList()
    private var pathsPublic: List<String> = emptyList()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Security.addProvider(BouncyCastleProvider())
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)

        val selectFileButton: MaterialButton = findViewById(R.id.btn_select_file)
        fileNameTextView = findViewById(R.id.tv_file_name)
        val signButton = findViewById<MaterialButton>(R.id.btn_sign)
        val editTextPassword = findViewById<EditText>(R.id.et_password)
        val textViewStatus = findViewById<TextView>(R.id.tv_status)

        selectFileButton.setOnClickListener {
            pickDocument.launch(arrayOf("application/*"))
        }

        signButton.setOnClickListener {
            try {
                privateKeyPassword = editTextPassword.text.toString()
                val privateKeyPath = pathsPrivate[0]
                val publicKeyPath = pathsPublic[0]
                // Загрузка закрытого ключа
                val privateKey = loadPrivateKey(privateKeyPath, privateKeyPassword)

                // Загрузка сертификата
                val certificate = loadCertificate(publicKeyPath)

                // Подписание файла
                val signature = sign(selectedFileUri, privateKey)

                // Верификация подписи
                val isVerified = verify(selectedFileUri, signature, certificate.publicKey)

                if (isVerified) {
                    textViewStatus.text = "Подписание документа завершено успешно"
                    textViewStatus.setTextColor(resources.getColor(R.color.green))
                } else {
                    textViewStatus.text = "Подписание документа не удалось"
                    textViewStatus.setTextColor(resources.getColor(R.color.red))
                }

            }  catch (e: IOException){
                Toast.makeText(this, "Неправильный пароль", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val pickDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                selectedFileUri = uri

                fileNameTextView.text = getUriFileName(uri)
                pathsPrivate = findFilePathsByExtensions(directoryPath, targetExtensionsPrivate)
                pathsPublic = findFilePathsByExtensions(directoryPath, targetExtensionsPublic)

                if (pathsPrivate.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Отсутствует закрытый ключ ЭЦП. Убедитесь, что он установлен на вашем устройстве",
                        Toast.LENGTH_LONG
                    )
                    return@registerForActivityResult
                }
                if (pathsPublic.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Отсутствует публичный ключ ЭЦП. Убедитесь, что он установлен на вашем устройстве и соответствует закрытому ключу ЭЦП",
                        Toast.LENGTH_LONG
                    )
                    return@registerForActivityResult
                }
            }
        }

    private fun getUriFileName(uri: Uri): String? {
        var fileName: String? = null
        val contentResolver = contentResolver

        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)

        try {
            cursor?.let {
                if (it.moveToFirst()) {

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

    fun loadPrivateKey(privateKeyPath: String, password: String): PrivateKey {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(FileInputStream(privateKeyPath), password.toCharArray())
        return keyStore.getKey(
            keyStore.aliases().nextElement(),
            password.toCharArray()
        ) as PrivateKey
    }


    fun loadCertificate(certificatePath: String): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        FileInputStream(certificatePath).use { certificateStream ->
            return certificateFactory.generateCertificate(certificateStream) as X509Certificate
        }
    }

    fun sign(documentUri: Uri, privateKey: PrivateKey): ByteArray {
        try {
            // Получение InputStream из Uri
            contentResolver.openInputStream(documentUri)?.use { documentStream ->
                val signature = Signature.getInstance("SHA256withRSA", "BC")
                signature.initSign(privateKey)

                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (documentStream.read(buffer).also { bytesRead = it } != -1) {
                    signature.update(buffer, 0, bytesRead)
                }

                return signature.sign()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ByteArray(0) // В случае ошибки возвращается пустой массив байтов
    }

    fun verify(
        documentUri: Uri,
        signature: ByteArray,
        publicKey: java.security.PublicKey
    ): Boolean {
        try {
            // Получение InputStream из Uri
            contentResolver.openInputStream(documentUri)?.use { documentStream ->
                val verifySignature = Signature.getInstance("SHA256withRSA", "BC")
                verifySignature.initVerify(publicKey)

                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (documentStream.read(buffer).also { bytesRead = it } != -1) {
                    verifySignature.update(buffer, 0, bytesRead)
                }

                return verifySignature.verify(signature)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun findFilePathsByExtensions(directoryPath: String, extensions: List<String>): List<String> {
        val directory = File(directoryPath)
        val resultPaths = mutableListOf<String>()
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        if (directory.exists() && directory.isDirectory) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val files = directory.listFiles()

                files?.let {
                    for (file in it) {
                        if (file.isFile && extensions.any { extension ->
                                file.extension.equals(
                                    extension,
                                    ignoreCase = true
                                )
                            }) {
                            resultPaths.add(file.absolutePath)
                        }
                    }
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    PERMISSION_REQUEST_CODE
                )
            }
        }

        return resultPaths
    }

}