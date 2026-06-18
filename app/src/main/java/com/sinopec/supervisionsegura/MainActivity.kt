
package com.sinopec.supervisionsegura

import android.app.*
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
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
    private lateinit var idEmpleado: EditText
    private lateinit var categoria: Spinner
    private lateinit var volante: Spinner
    private lateinit var comentarios: EditText
    private lateinit var fotoTexto: TextView
    private lateinit var fotoListContainer: LinearLayout
    private var currentCameraUri: Uri? = null
    private val photoUris = mutableListOf<Uri>()
    private val checks = mutableListOf<Pair<String, RadioGroup>>()

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
            text = "V${BuildConfig.VERSION_NAME} · PDF + QR"
            textSize = 12f
            setTextColor(Color.DKGRAY)
        })

        nombre = input("Nombre")
        idEmpleado = input("ID empleado")

        categoria = spinner(
            listOf("Cabo", "Checador", "Obrero", "Sobrestante")
        )

        volante = spinner(
            listOf("Base", "Volante 1", "volante 2", "volante 3", "volante 4", "volante 5", "volante 6", "volante 7")
        )

        root.addView(nombre)
        root.addView(idEmpleado)
        root.addView(labelSmall("Categoría"))
        root.addView(categoria)
        root.addView(labelSmall("Volante / cuadrilla"))
        root.addView(volante)

        section(root, "CHECKLIST")
        preguntas.forEach { q ->
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val label = TextView(this).apply {
                text = q
                textSize = 15f
                setTextColor(Color.DKGRAY)
            }

            val group = RadioGroup(this).apply {
                orientation = RadioGroup.HORIZONTAL
            }

            val rbSi = RadioButton(this).apply {
                text = "SI"
                id = View.generateViewId()
                isChecked = true
            }
            val rbNo = RadioButton(this).apply {
                text = "NO"
                id = View.generateViewId()
            }
            val rbCambio = RadioButton(this).apply {
                text = "CAMBIO"
                id = View.generateViewId()
            }

            group.addView(rbSi)
            group.addView(rbNo)
            group.addView(rbCambio)

            block.addView(label)
            block.addView(group)
            root.addView(block)
            checks.add(q to group)
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
        fotoListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6, 0, 12)
        }
        btnFotos.setOnClickListener { showPhotoOptions() }
        root.addView(btnFotos)
        root.addView(fotoTexto)
        root.addView(fotoListContainer)

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

    private fun labelSmall(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.DKGRAY)
        setPadding(0, 14, 0, 4)
    }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            items
        )
    }

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

    private fun showPhotoOptions() {
        val options = arrayOf("Tomar foto con cámara", "Seleccionar desde galería")
        AlertDialog.Builder(this)
            .setTitle("Agregar evidencia")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickPhotos()
                }
            }
            .show()
    }

    private fun pickPhotos() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, 25)
    }

    private fun takePhoto() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 80)
            return
        }
        openCamera()
    }

    private fun openCamera() {
        try {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SupervisionSegura").apply { mkdirs() }
            val name = "EVIDENCIA_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            val file = File(dir, name)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            currentCameraUri = uri

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            startActivityForResult(intent, 26)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la cámara: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 80) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado. Puedes activarlo en Ajustes de la app.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 25 && resultCode == RESULT_OK && data != null) {
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) photoUris.add(clip.getItemAt(i).uri)
            } ?: data.data?.let { photoUris.add(it) }
            updatePhotoUi()
        }

        if (requestCode == 26 && resultCode == RESULT_OK) {
            currentCameraUri?.let { photoUris.add(it) }
            currentCameraUri = null
            updatePhotoUi()
        }
    }

    private fun updatePhotoUi() {
        fotoTexto.text = "Fotos anexas: ${photoUris.size}"
        fotoListContainer.removeAllViews()

        if (photoUris.isEmpty()) {
            return
        }

        photoUris.forEachIndexed { index, _ ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }

            val label = TextView(this).apply {
                text = "Foto ${index + 1}"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val remove = Button(this).apply {
                text = "Quitar"
                setOnClickListener {
                    if (index in photoUris.indices) {
                        photoUris.removeAt(index)
                        updatePhotoUi()
                    }
                }
            }

            row.addView(label)
            row.addView(remove)
            fotoListContainer.addView(row)
        }

        val removeAll = Button(this).apply {
            text = "Quitar todas las fotos"
            setOnClickListener {
                photoUris.clear()
                updatePhotoUi()
            }
        }
        fotoListContainer.addView(removeAll)
    }

    private fun buildJson(): JSONObject {
        val answers = JSONArray()
        checks.forEach { (question, group) ->
            val selected = group.findViewById<RadioButton>(group.checkedRadioButtonId)?.text?.toString() ?: "SI"
            answers.put(JSONObject().apply {
                put("pregunta", question)
                put("respuesta", selected)       // "SI", "NO" o "CAMBIO"
                put("cumple", selected == "SI") // true / false para graficar después
                put("cambio", selected == "CAMBIO")
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
            put("id_empleado", idEmpleado.text.toString().trim())
            put("categoria", categoria.selectedItem?.toString()?.trim() ?: "")
            put("volante", volante.selectedItem?.toString()?.trim() ?: "")
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
        val safeName = json.optString("nombre", "SIN_NOMBRE")
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
            .ifBlank { "SIN_NOMBRE" }

        val folio = buildFolio(json, safeName)
        val safeDate = json.optString("fecha").replace("-", "_")
        val file = File(dir, "SUPERVISION_SEGURA_${safeDate}_${safeName}_$folio.pdf")

        val encryptedText = encryptedPayload.toString()
        val embeddedMarker = "%%SUPERVISION_SEGURA_JSON_ENCRYPTED_BASE64:" +
                Base64.encodeToString(encryptedText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val doc = PdfDocument()

        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val bmp = renderTarjetaAsBitmap(json, folio)
        page.canvas.drawBitmap(bmp, 0f, 0f, null)
        doc.finishPage(page)

        photos.take(8).forEachIndexed { idx, uri ->
            val p = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, idx + 2).create())
            val canvas = p.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(Color.WHITE)
            paint.color = Color.BLACK
            paint.textSize = 18f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("Evidencia fotográfica ${idx + 1}", 35f, 45f, paint)

            try {
                val original = MediaStore.Images.Media.getBitmap(activity.contentResolver, uri)
                val maxW = 525f
                val maxH = 720f
                val scale = minOf(maxW / original.width.toFloat(), maxH / original.height.toFloat())
                val drawW = original.width * scale
                val drawH = original.height * scale
                val left = (595f - drawW) / 2f
                val dest = RectF(left, 80f, left + drawW, 80f + drawH)
                val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                canvas.drawBitmap(original, null, dest, imagePaint)
            } catch (_: Exception) {
                paint.textSize = 13f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText("No se pudo cargar esta fotografía.", 35f, 80f, paint)
            }
            doc.finishPage(p)
        }

        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        // Este marcador es lo que leerá el programa de PC. No depende del texto visible ni del QR.
        file.appendText("\n$embeddedMarker\n")
        return file
    }

    private fun renderTarjetaAsBitmap(json: JSONObject, folio: String): Bitmap {
        val page = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawColor(Color.WHITE)

        val left = 35f
        val top = 18f
        val right = 560f
        val bottom = 822f
        val red = Color.rgb(225, 0, 0)
        val blue = Color.rgb(35, 55, 165)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = Color.BLACK
        canvas.drawRect(left, top, right, bottom, p)

        drawHeader(canvas, p, json, left, top, right)

        val personalY = top + 100f
        drawSection(canvas, p, "PERSONAL", left, personalY, right, red)

        val personalQuestions = listOf(
            "¿HOY ME ENCUENTRO BIEN?",
            "¿TENGO CASCO?",
            "¿MI OVEROL ESTA EN BUEN ESTADO?",
            "¿MIS BOTAS ESTAN EN BUEN ESTADO?",
            "¿MIS POLAINAS ESTAN EN BUEN ESTADO?",
            "¿MI CHALECO ESTA EN BUEN ESTADO?",
            "¿CUENTO CON GUANTES?",
            "¿CUENTO CON LENTES?",
            "¿CUENTO CON BARBIQUEJO?",
            "¿MIS COMPAÑEROS SE ENCUENTRAN BIEN?",
            "¿YO ESTOY LISTO PARA IR A TRABAJAR?"
        )

        drawChecklistBlock(canvas, p, json, 0, personalQuestions,
            x = left + 7f, y = personalY + 42f, rowH = 23f,
            boxX = right - 148f, boxW = 43f, boxH = 23f, textSize = 10.8f, blue = blue)

        val toolsY = personalY + 42f + personalQuestions.size * 23f + 8f
        drawSection(canvas, p, "EQUIPOS Y HERRAMIENTAS", left, toolsY, right, red)

        val toolsQuestions = listOf(
            "¿RECIBI MI EQUIPO Y HERRAMIENTA\nEN BUEN ESTADO?",
            "¿TENGO IDENTIFICADOS LOS RIESGOS A LOS QUE\nESTOY EXPUESTO?",
            "¿SE QUE HACER EN CASO DE UNA EMERGENCIA?"
        )

        drawChecklistBlock(canvas, p, json, 11, toolsQuestions,
            x = left + 7f, y = toolsY + 42f, rowH = 41f,
            boxX = right - 148f, boxW = 43f, boxH = 41f, textSize = 10.0f, blue = blue)

        val jobsY = toolsY + 42f + toolsQuestions.size * 41f + 8f
        drawSection(canvas, p, "TRABAJOS A REALIZAR", left, jobsY, right, red)

        val jobQuestions = listOf(
            "¿SE PLANEARON LAS ACTIVIDADES CON LA\nIDENTIFICACION DE RIESGOS?",
            "¿SE TIENEN LOS PROCEDIMIENTOS O\nINSTRUCTIVOS DE TRABAJO?"
        )

        drawChecklistBlock(canvas, p, json, 14, jobQuestions,
            x = left + 7f, y = jobsY + 42f, rowH = 39f,
            boxX = right - 148f, boxW = 43f, boxH = 39f, textSize = 10.0f, blue = blue)

        val hydrationY = jobsY + 42f + jobQuestions.size * 39f + 7f
        p.style = Paint.Style.FILL
        p.color = red
        canvas.drawRect(left, hydrationY, right, hydrationY + 42f, p)

        p.color = Color.WHITE
        p.textSize = 12.7f
        p.typeface = Typeface.DEFAULT_BOLD
        drawCenteredText(canvas, p, "PRIMERO QUE TODO TU SEGURIDAD LLEVA AGUA Y SUERO PARA", left, right, hydrationY + 17f)
        drawCenteredText(canvas, p, "MANTENERTE HIDRATADO", left, right, hydrationY + 34f)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.BLACK
        canvas.drawRect(left, hydrationY, right, hydrationY + 42f, p)

        val commentTop = hydrationY + 42f
        drawCommentsBox(canvas, p, json, folio, left, commentTop, right, bottom, blue)

        return page
    }

    private fun drawHeader(canvas: Canvas, p: Paint, json: JSONObject, left: Float, top: Float, right: Float) {
        val red = Color.rgb(225, 0, 0)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = Color.BLACK

        val headerBottom = top + 100f
        val logoRight = left + 155f
        val titleRight = right - 165f
        canvas.drawRect(left, top, right, headerBottom, p)
        canvas.drawLine(logoRight, top, logoRight, headerBottom, p)
        canvas.drawLine(titleRight, top, titleRight, top + 72f, p)
        canvas.drawLine(titleRight, top + 36f, right, top + 36f, p)
        canvas.drawLine(logoRight, top + 72f, right, top + 72f, p)

        val logo = loadLogoBitmap()
        if (logo != null) {
            canvas.drawBitmap(logo, null, RectF(left + 8f, top + 8f, logoRight - 8f, top + 66f), null)
        } else {
            p.style = Paint.Style.FILL
            p.color = red
            p.textSize = 21f
            p.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("SINOPEC", left + 22f, top + 44f, p)
        }

        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.textSize = 21f
        p.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Supervisión Segura", logoRight + 8f, top + 44f, p)

        p.textSize = 10.5f
        canvas.drawText("FECHA:", titleRight + 8f, top + 17f, p)
        canvas.drawText(json.optString("fecha"), titleRight + 70f, top + 17f, p)
        canvas.drawText("VOLANTE:", titleRight + 8f, top + 53f, p)
        canvas.drawText(json.optString("volante").uppercase(Locale.US).take(14), titleRight + 75f, top + 53f, p)

        p.textSize = 10.2f
        canvas.drawText("CATEGORIA:", logoRight + 8f, top + 88f, p)
        canvas.drawText(json.optString("categoria").uppercase(Locale("es", "MX")).take(18), logoRight + 90f, top + 88f, p)

        canvas.drawText("NOMBRE:", logoRight + 8f, top + 99f, p)
        canvas.drawText(json.optString("nombre").uppercase(Locale("es", "MX")).take(24), logoRight + 75f, top + 99f, p)

        canvas.drawText("ID:", titleRight + 8f, top + 88f, p)
        canvas.drawText(json.optString("id_empleado").uppercase(Locale.US).take(18), titleRight + 35f, top + 88f, p)
    }

    private fun drawSection(canvas: Canvas, p: Paint, title: String, left: Float, y: Float, right: Float, red: Int) {
        p.style = Paint.Style.FILL
        p.color = red
        canvas.drawRect(left, y, right, y + 22f, p)
        p.color = Color.WHITE
        p.textSize = 13f
        p.typeface = Typeface.DEFAULT_BOLD
        drawCenteredText(canvas, p, title, left, right, y + 16f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.BLACK
        canvas.drawRect(left, y, right, y + 22f, p)
    }

    private fun drawChecklistBlock(
        canvas: Canvas,
        p: Paint,
        json: JSONObject,
        startIndex: Int,
        questions: List<String>,
        x: Float,
        y: Float,
        rowH: Float,
        boxX: Float,
        boxW: Float,
        boxH: Float,
        textSize: Float,
        blue: Int
    ) {
        val arr = json.optJSONArray("checklist") ?: JSONArray()

        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.textSize = 9.4f
        p.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("SI", boxX + 14f, y - 7f, p)
        canvas.drawText("NO", boxX + boxW + 10f, y - 7f, p)
        canvas.drawText("CAMBIO", boxX + boxW * 2f + 1f, y - 7f, p)

        questions.forEachIndexed { idx, question ->
            val rowTop = y + idx * rowH
            val lines = question.split("\n")
            p.style = Paint.Style.FILL
            p.color = Color.BLACK
            p.textSize = textSize
            p.typeface = Typeface.DEFAULT_BOLD
            var textY = rowTop + if (lines.size > 1) 13f else 15.5f
            lines.forEach {
                canvas.drawText(it, x, textY, p)
                textY += 12f
            }

            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.1f
            p.color = Color.BLACK
            for (c in 0..2) {
                canvas.drawRect(boxX + c * boxW, rowTop, boxX + (c + 1) * boxW, rowTop + boxH, p)
            }

            val globalIndex = startIndex + idx
            val respuesta = if (globalIndex < arr.length()) {
                val obj = arr.getJSONObject(globalIndex)
                obj.optString("respuesta", if (obj.optBoolean("cumple", true)) "SI" else "NO")
            } else "SI"

            p.style = Paint.Style.FILL
            p.color = blue
            p.textSize = if (boxH <= 24f) 22f else 25f
            p.typeface = Typeface.DEFAULT_BOLD

            when (respuesta.uppercase(Locale.US)) {
                "SI" -> canvas.drawText("✓", boxX + 12f, rowTop + boxH - 5f, p)
                "NO" -> canvas.drawText("X", boxX + boxW + 12f, rowTop + boxH - 7f, p)
                "CAMBIO" -> canvas.drawText("X", boxX + boxW * 2f + 12f, rowTop + boxH - 7f, p)
                else -> canvas.drawText("✓", boxX + 12f, rowTop + boxH - 5f, p)
            }
        }
    }

    private fun drawCommentsBox(canvas: Canvas, p: Paint, json: JSONObject, folio: String, left: Float, top: Float, right: Float, bottom: Float, blue: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.BLACK
        canvas.drawRect(left, top, right, bottom, p)

        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.textSize = 10.5f
        p.typeface = Typeface.DEFAULT
        canvas.drawText("COMENTARIOS", left + 7f, top + 14f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.45f
        p.color = Color.rgb(210, 210, 210)
        var lineY = top + 30f
        while (lineY < bottom - 9f) {
            canvas.drawLine(left + 5f, lineY, right - 5f, lineY, p)
            lineY += 18f
        }

        val qrSize = 35f
        val qrX = right - 49f
        val qrY = top + 19f
        drawFolioQr(canvas, p, folio, qrX, qrY)

        p.style = Paint.Style.FILL
        p.color = blue
        p.textSize = 12.5f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        val maxChars = 46
        val commentLines = wrapText(json.optString("comentarios"), maxChars).take(3)
        var cy = top + 32f
        commentLines.forEach { line ->
            canvas.drawText(line, left + 10f, cy, p)
            cy += 18f
        }
    }

    private fun drawCenteredText(canvas: Canvas, p: Paint, text: String, left: Float, right: Float, y: Float) {
        val tw = p.measureText(text)
        canvas.drawText(text, left + ((right - left) - tw) / 2f, y, p)
    }

    private fun loadLogoBitmap(): Bitmap? {
        return try {
            val resId = activity.resources.getIdentifier(
                "tarjeta_supervision",
                "drawable",
                activity.packageName
            )
            if (resId == 0) null else BitmapFactory.decodeResource(activity.resources, resId)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawFolioQr(canvas: Canvas, p: Paint, folio: String, x: Float, y: Float) {
        try {
            val qr = makeQr(folio, 34)
            canvas.drawBitmap(qr, x, y, null)
            p.color = Color.BLACK
            p.textSize = 4.4f
            p.typeface = Typeface.DEFAULT
            canvas.drawText(folio.take(18), x - 7f, y + 42f, p)
        } catch (_: Exception) {}
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val clean = text.replace("\n", " ").trim()
        if (clean.isBlank()) return emptyList()
        val words = clean.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val next = if (current.isBlank()) word else "$current $word"
            if (next.length <= maxChars) current = next else {
                if (current.isNotBlank()) lines.add(current)
                current = word
            }
        }
        if (current.isNotBlank()) lines.add(current)
        return lines
    }

    private fun buildFolio(json: JSONObject, safeName: String): String {
        val base = "${json.optString("fecha")}|${json.optString("volante")}|$safeName|${System.currentTimeMillis()}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(base.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
            .take(8)
        val date = json.optString("fecha").replace("-", "")
        val vol = json.optString("volante", "SV")
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "")
        return "SS-$date-$vol-$hash"
    }

    private fun makeQr(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
