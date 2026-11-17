
package com.pdfmaster.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlin.math.min

private enum class PdfAction {
    NONE, READ, MERGE, SPLIT, COMPRESS, IMAGES
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PDFBoxResourceLoader.init(applicationContext)

        setContent {
            MaterialTheme {
                PdfMasterScreen()
            }
        }
    }
}

@Composable
private fun PdfMasterScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as ComponentActivity
    val resolver = context.contentResolver

    var language by remember { mutableStateOf("ar") }
    var action by remember { mutableStateOf(PdfAction.NONE) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var readerUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var startPage by remember { mutableStateOf("1") }
    var endPage by remember { mutableStateOf("1") }
    var status by remember { mutableStateOf("اختار الخدمة اللي بغيتي") }
    var busy by remember { mutableStateOf(false) }

    val isArabic = language == "ar"

    fun runTask(task: () -> String) {
        if (busy) return
        busy = true
        status = if (isArabic) "جاري المعالجة..." else "Processing..."

        Thread {
            val result = try {
                task()
            } catch (e: Exception) {
                "Error: ${e.message ?: "Unknown error"}"
            }

            activity.runOnUiThread {
                status = result
                busy = false
            }
        }.start()
    }

    fun showPage(uri: Uri, pageIndex: Int) {
        runTask {
            val result = renderPdfPage(resolver, uri, pageIndex)
            activity.runOnUiThread {
                preview = result.first
                currentPage = pageIndex
                pageCount = result.second
            }
            if (isArabic) {
                "الصفحة ${pageIndex + 1} من ${result.second}"
            } else {
                "Page ${pageIndex + 1} of ${result.second}"
            }
        }
    }

    val outputPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { outputUri ->
            if (outputUri != null) {
                val sources = pendingUris
                val selectedAction = action

                runTask {
                    when (selectedAction) {
                        PdfAction.MERGE ->
                            mergePdfs(resolver, sources, outputUri)

                        PdfAction.SPLIT -> {
                            val first = startPage.toIntOrNull()
                                ?: throw IllegalArgumentException("Invalid start page")
                            val last = endPage.toIntOrNull()
                                ?: throw IllegalArgumentException("Invalid end page")

                            splitPdf(resolver, sources.first(), outputUri, first, last)
                        }

                        PdfAction.COMPRESS ->
                            compressPdf(resolver, sources.first(), outputUri)

                        PdfAction.IMAGES ->
                            imagesToPdf(resolver, sources, outputUri)

                        else -> "No operation selected"
                    }
                }
            }
        }

    val pdfPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                when (action) {
                    PdfAction.READ -> {
                        readerUri = uri
                        currentPage = 0
                        showPage(uri, 0)
                    }

                    PdfAction.SPLIT, PdfAction.COMPRESS -> {
                        pendingUris = listOf(uri)
                        val fileName =
                            if (action == PdfAction.SPLIT) "split.pdf"
                            else "compressed.pdf"
                        outputPicker.launch(fileName)
                    }

                    else -> Unit
                }
            }
        }

    val multiPdfPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                pendingUris = uris
                outputPicker.launch("merged.pdf")
            }
        }

    val imagePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                pendingUris = uris
                outputPicker.launch("images.pdf")
            }
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101820)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PDF Master AI",
                    color = Color(0xFF00D68F),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        language = when (language) {
                            "ar" -> "fr"
                            "fr" -> "en"
                            else -> "ar"
                        }
                    }
                ) {
                    Text(
                        when (language) {
                            "ar" -> "AR"
                            "fr" -> "FR"
                            else -> "EN"
                        },
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (isArabic) "جميع أدوات PDF في تطبيق واحد"
                else "All your PDF tools in one app",
                color = Color.LightGray,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(22.dp))

            ServiceButton(
                title = if (isArabic) "قراءة PDF" else "Read PDF",
                description = if (isArabic) "فتح وقراءة ملف PDF"
                else "Open and read a PDF",
                onClick = {
                    action = PdfAction.READ
                    pdfPicker.launch(arrayOf("application/pdf"))
                }
            )

            ServiceButton(
                title = if (isArabic) "دمج ملفات PDF" else "Merge PDFs",
                description = if (isArabic) "جمع عدة ملفات في ملف واحد"
                else "Combine multiple PDF files",
                onClick = {
                    action = PdfAction.MERGE
                    multiPdfPicker.launch(arrayOf("application/pdf"))
                }
            )

            ServiceButton(
                title = if (isArabic) "تقسيم PDF" else "Split PDF",
                description = if (isArabic) "استخراج صفحات محددة"
                else "Extract selected pages",
                onClick = {
                    action = PdfAction.SPLIT
                }
            )

            if (action == PdfAction.SPLIT) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C2935)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (isArabic) "رقم الصفحة الأولى والأخيرة"
                            else "First and last page",
                            color = Color.White
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = startPage,
                            onValueChange = { startPage = it },
                            label = {
                                Text(if (isArabic) "من الصفحة" else "From page")
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = endPage,
                            onValueChange = { endPage = it },
                            label = {
                                Text(if (isArabic) "إلى الصفحة" else "To page")
                            },
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                pdfPicker.launch(arrayOf("application/pdf"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isArabic) "اختار PDF وقسمو"
                                else "Choose PDF and split"
                            )
                        }
                    }
                }
            }

            ServiceButton(
                title = if (isArabic) "ضغط PDF" else "Compress PDF",
                description = if (isArabic) "تقليل الحجم مع احتمال نقص الجودة"
                else "Reduce size with possible quality loss",
                onClick = {
                    action = PdfAction.COMPRESS
                    pdfPicker.launch(arrayOf("application/pdf"))
                }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3A2B1C)
                )
            ) {
                Text(
                    if (isArabic) {
                        "تنبيه: الضغط يحوّل صفحات PDF إلى صور. قد تنقص الجودة، ولن يبقى النص قابلاً للبحث أو النسخ، وقد لا يصبح الملف أصغر دائماً."
                    } else {
                        "Warning: compression converts PDF pages to images. Quality may decrease, searchable text and links are lost, and the result is not guaranteed to be smaller."
                    },
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }

            ServiceButton(
                title = if (isArabic) "تحويل الصور إلى PDF"
                else "Images to PDF",
                description = if (isArabic) "جمع الصور في ملف PDF"
                else "Combine images into a PDF",
                onClick = {
                    action = PdfAction.IMAGES
                    imagePicker.launch(arrayOf("image/*"))
                }
            )

            Spacer(Modifier.height(18.dp))

            if (busy) {
                CircularProgressIndicator(
                    color = Color(0xFF00D68F)
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                status,
                color = Color(0xFF00D68F),
                fontSize = 14.sp
            )

            if (preview != null) {
                Spacer(Modifier.height(20.dp))

                Text(
                    if (isArabic) "معاينة PDF" else "PDF Preview",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Image(
                    bitmap = preview!!.asImageBitmap(),
                    contentDescription = "PDF page preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                )

                Text(
                    "${currentPage + 1} / $pageCount",
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        enabled = !busy && currentPage > 0,
                        onClick = {
                            val uri = readerUri
                            val targetPage = currentPage - 1
                            if (uri != null) showPage(uri, targetPage)
                        }
                    ) {
                        Text(if (isArabic) "السابق" else "Previous")
                    }

                    Button(
                        enabled = !busy && currentPage < pageCount - 1,
                        onClick = {
                            val uri = readerUri
                            val targetPage = currentPage + 1
                            if (uri != null) showPage(uri, targetPage)
                        }
                    ) {
                        Text(if (isArabic) "التالي" else "Next")
                    }
                }
            }

            Spacer(Modifier.height(30.dp))

            Text(
                "PDF Master AI",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ServiceButton(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C2935)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00A878)
                )
            ) {
                Text(title, color = Color.White)
            }

            Text(
                description,
                color = Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun renderPdfPage(
    resolver: android.content.ContentResolver,
    uri: Uri,
    pageIndex: Int
): Pair<Bitmap, Int> {
    val descriptor: ParcelFileDescriptor =
        resolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF")

    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) {
                throw IllegalArgumentException("Page does not exist")
            }

            renderer.openPage(pageIndex).use { page ->
                val width = 1200
                val height = (width.toFloat() * page.height / page.width)
                    .toInt()
                    .coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                return bitmap to renderer.pageCount
            }
        }
    }
}

private fun mergePdfs(
    resolver: android.content.ContentResolver,
    sources: List<Uri>,
    outputUri: Uri
): String {
    require(sources.size >= 2) { "Choose at least two PDF files" }

    val output = PDDocument()

    try {
        for (uri in sources) {
            val inputStream = resolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open source PDF")

            inputStream.use { stream ->
                PDDocument.load(stream).use { source ->
                    for (index in 0 until source.numberOfPages) {
                        output.importPage(source.getPage(index))
                    }
                }
            }
        }

        val outputStream = resolver.openOutputStream(outputUri)
            ?: throw IllegalArgumentException("Cannot create output file")

        outputStream.use { stream ->
            output.save(stream)
        }
    } finally {
        output.close()
    }

    return "PDF files merged successfully"
}

private fun splitPdf(
    resolver: android.content.ContentResolver,
    sourceUri: Uri,
    outputUri: Uri,
    firstPage: Int,
    lastPage: Int
): String {
    val inputStream = resolver.openInputStream(sourceUri)
        ?: throw IllegalArgumentException("Cannot open PDF")

    inputStream.use { stream ->
        PDDocument.load(stream).use { source ->
            require(firstPage >= 1 && lastPage >= firstPage) {
                "Invalid page range"
            }
            require(lastPage <= source.numberOfPages) {
                "Last page exceeds PDF page count (${source.numberOfPages})"
            }

            PDDocument().use { output ->
                for (index in (firstPage - 1) until lastPage) {
                    output.importPage(source.getPage(index))
                }

                val outputStream = resolver.openOutputStream(outputUri)
                    ?: throw IllegalArgumentException("Cannot create output file")

                outputStream.use { out ->
                    output.save(out)
                }
            }
        }
    }

    return "PDF pages extracted successfully"
}

private fun compressPdf(
    resolver: android.content.ContentResolver,
    sourceUri: Uri,
    outputUri: Uri
): String {
    val descriptor = resolver.openFileDescriptor(sourceUri, "r")
        ?: throw IllegalArgumentException("Cannot open PDF")

    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            PdfDocument().use { output ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val width = 1000
                        val height = (
                            width.toFloat() * page.height / page.width
                        ).toInt().coerceIn(1, 2000)

                        val bitmap = Bitmap.createBitmap(
                            width,
                            height,
                            Bitmap.Config.RGB_565
                        )

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                        )

                        val pageWidth = 595
                        val pageHeight = (
                            pageWidth.toFloat() * page.height / page.width
                        ).toInt().coerceAtLeast(1)

                        val info = PdfDocument.PageInfo.Builder(
                            pageWidth,
                            pageHeight,
                            index + 1
                        ).create()

                        val newPage = output.startPage(info)

                        newPage.canvas.drawBitmap(
                            bitmap,
                            null,
                            Rect(0, 0, pageWidth, pageHeight),
                            null
                        )

                        output.finishPage(newPage)
                        bitmap.recycle()
                    }
                }

                val outputStream = resolver.openOutputStream(outputUri)
                    ?: throw IllegalArgumentException("Cannot create output file")

                outputStream.use { stream ->
                    output.writeTo(stream)
                }
            }
        }
    }

    return "PDF converted to image-based PDF"
}

private fun imagesToPdf(
    resolver: android.content.ContentResolver,
    imageUris: List<Uri>,
    outputUri: Uri
): String {
    require(imageUris.isNotEmpty()) { "Choose at least one image" }

    PdfDocument().use { document ->
        imageUris.forEachIndexed { index, uri ->
            val input = resolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open image")

            val bitmap = input.use {
                BitmapFactory.decodeStream(it)
            } ?: throw IllegalArgumentException("Unsupported image")

            val pageWidth = 595
            val pageHeight = 842

            val info = PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                index + 1
            ).create()

            val page = document.startPage(info)

            val scale = min(
                pageWidth.toFloat() / bitmap.width,
                pageHeight.toFloat() / bitmap.height
            )

            val drawWidth = (bitmap.width * scale).toInt()
            val drawHeight = (bitmap.height * scale).toInt()

            val left = (pageWidth - drawWidth) / 2
            val top = (pageHeight - drawHeight) / 2

            page.canvas.drawBitmap(
                bitmap,
                null,
                Rect(
                    left,
                    top,
                    left + drawWidth,
                    top + drawHeight
                ),
                null
            )

            document.finishPage(page)
            bitmap.recycle()
        }

        val outputStream = resolver.openOutputStream(outputUri)
            ?: throw IllegalArgumentException("Cannot create output file")

        outputStream.use { stream ->
            document.writeTo(stream)
        }
    }

    return "Images converted to PDF successfully"
}
