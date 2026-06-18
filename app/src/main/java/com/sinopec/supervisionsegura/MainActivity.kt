
package com.sinopec.supervisionsegura

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class MainActivity : Activity() {
    private lateinit var nombre: EditText
    private lateinit var categoria: EditText
    private lateinit var volante: EditText
    private lateinit var comentarios: EditText
    private lateinit var fotoTexto: TextView
    private val photoUris = mutableListOf<Uri>()
    private val checks = mutableListOf<Pair<String, Switch>>()

    private val preguntas = listOf(
        "¿Hoy me encuentro bien?",
        "¿Tengo casco?",
        "¿Mi overol está en buen estado?",
        "¿Mis botas están en buen estado?",
        "¿Mis polainas están en buen estado?",
        "¿Mi chaleco está en buen estado?",
        "¿Cuento con guantes?",
        "¿Cuento con lentes?",
        "¿Cuento con barbiquejo?",
        "¿Mis compañeros se encuentran bien?",
        "¿Yo estoy listo para ir a trabajar?",
        "¿Recibí mi equipo y herramienta en buen estado?",
        "¿Tengo identificados los riesgos a los que estoy expuesto?",
        "¿Sé qué hacer en caso de una emergencia?",
        "¿Se planearon las actividades con la identificación de riesgos?",
        "¿Se tienen los procedimientos o instructivos de trabajo?"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VersionService(this).check { allowed, message ->
            runOnUiThread {
                if (allowed) buildUi() else showBlocked(message)
            }
        }
    }

    private fun showBlocked(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Versión no vigente")
            .setMessage(message.ifBlank { "Esta versión ya no está vigente. Solicita la versión actualizada." })
            .setCancelable(false)
            .setPositiveButton("Cerrar") { _, _ -> finish() }
            .show()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Supervisión Segura"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(181, 30, 46))
        })
        root.addView(TextView(this).apply {
            text = "V${BuildConfig.VERSION_NAME} · PDF + JSON cifrado + QR"
            textSize = 12f
            setTextColor(Color.DKGRAY)
        })

        nombre = input("Nombre")
        categoria = input("Categoría")
        volante = input("Volante / cuadrilla")
        root.addView(nombre); root.addView(categoria); root.addView(volante)

        section(root, "CHECKLIST")
        preguntas.forEach { q ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply { text = q; textSize = 15f }
            val sw = Switch(this).apply { text = "SI"; isChecked = true }
            sw.setOnCheckedChangeListener { button, checked -> button.text = if (checked) "SI" else "NO" }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(sw)
            root.addView(row)
            checks.add(q to sw)
        }

        section(root, "COMENTARIOS")
        comentarios = EditText(this).apply {
            hint = "Ejemplo: llevamos suficiente agua y nos sentimos bien de salud."
            minLines = 3
            gravity = Gravity.TOP
        }
        root.addView(comentarios)

        val btnFotos = Button(this).apply { text = "Agregar fotos de evidencia" }
        fotoTexto = TextView(this).apply { text = "Fotos anexas: 0" }
        btnFotos.setOnClickListener { pickPhotos() }
        root.addView(btnFotos); root.addView(fotoTexto)

        val btnPdf = Button(this).apply { text = "Generar PDF y compartir por WhatsApp" }
        btnPdf.setOnClickListener {
            if (nombre.text.toString().trim().isBlank()) {
                Toast.makeText(this, "Captura el nombre", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val payload = CryptoService.encrypt(buildJson().toString())
            val pdf = PdfService(this).createPdf(buildJson(), payload, photoUris)
            sharePdf(pdf)
        }
        root.addView(btnPdf)
        setContentView(scroll)
    }

    private fun input(h: String) = EditText(this).apply { hint = h; setSingleLine(true) }

    private fun section(root: LinearLayout, text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(181, 30, 46))
            setPadding(10, 10, 10, 10)
        })
    }

    private fun pickPhotos() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 25)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 25 && resultCode == RESULT_OK && data != null) {
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) photoUris.add(clip.getItemAt(i).uri)
            } ?: data.data?.let { photoUris.add(it) }
            fotoTexto.text = "Fotos anexas: ${photoUris.size}"
        }
    }

    private fun buildJson(): JSONObject {
        val answers = JSONArray()
        checks.forEach { (question, sw) ->
            answers.put(JSONObject().apply {
                put("pregunta", question)
                put("cumple", sw.isChecked) // true / false para graficar después
            })
        }
        return JSONObject().apply {
            put("formato", "SUPERVISION_SEGURA")
            put("version_formato", "1.0")
            put("app_version", BuildConfig.VERSION_NAME)
            put("app_version_code", BuildConfig.VERSION_CODE)
            put("fecha", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
            put("hora", SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()))
            put("nombre", nombre.text.toString().trim())
            put("categoria", categoria.text.toString().trim())
            put("volante", volante.text.toString().trim())
            put("comentarios", comentarios.text.toString().trim())
            put("checklist", answers)
            put("fotos_anexas", photoUris.size)
            put("created_at_device", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
        }
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }
        try { startActivity(send) } catch (e: Exception) { startActivity(Intent.createChooser(send, "Compartir PDF")) }
    }
}

object CryptoService {
    fun encrypt(plainText: String): JSONObject {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(BuildConfig.DATA_KEY.toByteArray(Charsets.UTF_8))
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("tipo", "SUPERVISION_SEGURA_ENCRYPTED")
            put("crypto", "AES-256-GCM")
            put("key_id", "SS-V1-DEMO")
            put("iv_b64", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("data_b64", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }
    }
}

class VersionService(private val activity: Activity) {
    fun check(callback: (Boolean, String) -> Unit) {
        Thread {
            val localOk = isBeforeOrSame(BuildConfig.LOCAL_EXPIRES_AT)
            if (!localOk) {
                callback(false, "La versión ${BuildConfig.VERSION_NAME} caducó el ${BuildConfig.LOCAL_EXPIRES_AT}.")
                return@Thread
            }
            val url = BuildConfig.VERSION_CONTROL_URL
            if (url.isBlank()) {
                callback(true, "")
                return@Thread
            }
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                val enabled = obj.optBoolean("enabled", true)
                val minCode = obj.optInt("min_version_code", 1)
                val remoteExpiresAt = obj.optString("expires_at", BuildConfig.LOCAL_EXPIRES_AT)
                val message = obj.optString("message", "Esta versión ya no está vigente.")
                val allowed = enabled && BuildConfig.VERSION_CODE >= minCode && isBeforeOrSame(remoteExpiresAt)
                callback(allowed, if (allowed) "" else message)
            } catch (_: Exception) {
                // Si no hay internet, por ahora deja trabajar si no venció localmente.
                callback(true, "")
            }
        }.start()
    }

    private fun isBeforeOrSame(date: String): Boolean {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            today <= date
        } catch (_: Exception) { true }
    }
}

class PdfService(private val activity: Activity) {
    fun createPdf(json: JSONObject, encryptedPayload: JSONObject, photos: List<Uri>): File {
        val dir = File(activity.getExternalFilesDir(null), "SupervisionSegura").apply { mkdirs() }
        val safeName = json.optString("nombre", "SIN_NOMBRE").uppercase(Locale.US).replace(Regex("[^A-Z0-9]+"), "_")
        val file = File(dir, "SUPERVISION_SEGURA_${json.optString("fecha")}_${safeName}.pdf")
        val encryptedText = encryptedPayload.toString()
        val embeddedMarker = "%%SUPERVISION_SEGURA_JSON_ENCRYPTED_BASE64:" + Base64.encodeToString(encryptedText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = doc.startPage(pageInfo)
        var c = page.canvas
        var y = 40f

        fun text(t: String, size: Float = 12f, bold: Boolean = false) {
            paint.color = Color.BLACK
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            c.drawText(t.take(95), 35f, y, paint)
            y += size + 9f
        }
        fun band(t: String) {
            paint.color = Color.rgb(181, 30, 46)
            c.drawRect(30f, y, 565f, y + 25f, paint)
            paint.color = Color.WHITE; paint.textSize = 14f; paint.typeface = Typeface.DEFAULT_BOLD
            c.drawText(t, 40f, y + 18f, paint)
            y += 35f
        }

        text("SINOPEC - Supervisión Segura", 20f, true)
        text("Fecha: ${json.optString("fecha")}    Volante: ${json.optString("volante")}", 12f)
        text("Categoría: ${json.optString("categoria")}", 12f)
        text("Nombre: ${json.optString("nombre")}", 12f)
        text("Archivo con datos cifrados para lectura automática", 9f)
        band("CHECKLIST")
        val arr = json.getJSONArray("checklist")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            text("${if (o.getBoolean("cumple")) "SI" else "NO"} - ${o.getString("pregunta")}", 10.5f)
            if (y > 690) { doc.finishPage(page); page = doc.startPage(pageInfo); c = page.canvas; y = 40f }
        }
        band("COMENTARIOS")
        json.optString("comentarios").chunked(80).forEach { text(it, 12f) }
        band("QR DE LECTURA")
        text("Este QR contiene el paquete cifrado. El lector de PC podrá descifrarlo.", 9f)
        try {
            val qrBmp = makeQr(encryptedText, 170)
            c.drawBitmap(qrBmp, 35f, y, null)
            y += 180f
        } catch (_: Exception) {
            text("QR no generado; datos embebidos en marcador interno.", 10f)
        }
        band("FOTOS ANEXAS")
        text("Cantidad de fotos anexas: ${photos.size}")
        doc.finishPage(page)

        photos.take(8).forEachIndexed { idx, uri ->
            val p = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, idx + 2).create())
            val cc = p.canvas
            paint.color = Color.BLACK; paint.textSize = 18f; paint.typeface = Typeface.DEFAULT_BOLD
            cc.drawText("Evidencia fotográfica ${idx + 1}", 35f, 40f, paint)
            try {
                val bmp = MediaStore.Images.Media.getBitmap(activity.contentResolver, uri)
                val newW = 520
                val newH = (bmp.height * 520f / bmp.width).toInt().coerceAtMost(720)
                val scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true)
                cc.drawBitmap(scaled, 35f, 70f, null)
            } catch (_: Exception) { }
            doc.finishPage(p)
        }

        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        // Marcador embebido: el lector de PC buscará esta línea al final del PDF, sin OCR.
        file.appendText("\n$embeddedMarker\n")
        return file
    }

    private fun makeQr(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }
}
